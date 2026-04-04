package com.data.oai.grobid.tei;

import com.data.oai.shared.dto.Section;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;
import java.util.stream.Collectors;

import static com.data.oai.grobid.tei.GrobidTeiUtils.*;


/**
 * Extracts sections from TEI-XML documents parsed by GROBID.
 */
final class GrobidSectionExtractor {

    private GrobidSectionExtractor() {}

    static List<Section> extractSections(Document tei) {
        Element text = tei.selectFirst("text");
        if (text == null) return List.of();

        List<Section> out = new ArrayList<>();

        for (Element container : text.select("> front, > body, > back")) {
            String containerName = container.tagName().equals("back") ? "BACK"
                    : container.tagName().equals("front") ? "FRONT"
                    : "BODY";

            for (Element div : container.select("> div")) {
                String sectionTitle = resolveSectionTitle(div);
                String sectionText = extractDivTextIncludingNestedDivs(div);

                if (!sectionText.isBlank()) {
                    out.add(new Section(sectionTitle, 1, sectionText, new ArrayList<>()));
                }
            }

            String floating = extractFloatingBlocks(container);
            if (!floating.isBlank() && out.stream().noneMatch(s -> s.getTitle().equals(containerName) && s.getLevel() == 1)) {
                out.add(new Section(containerName, 1, floating, new ArrayList<>()));
            }
        }

        return out.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                s -> s.getTitle() + "::" + s.getText().hashCode(),
                                s -> s,
                                (a, b) -> a,
                                LinkedHashMap::new
                        ),
                        m -> List.copyOf(m.values())
                ));
    }

    private static String resolveSectionTitle(Element div) {
        String head = "";
        Element headEl = div.selectFirst("> head");
        if (headEl != null) head = normalizeWs(headEl.text());

        if (head.isBlank()) {
            String type = div.attr("type");
            if (!type.isBlank()) head = type.toUpperCase(Locale.ROOT);
        }

        String sectionTitle = normalizeWs(head);
        return (sectionTitle == null || sectionTitle.isBlank()) ? "SECTION" : sectionTitle;
    }

    private static String extractDivTextIncludingNestedDivs(Element div) {
        if (div == null) return "";

        Element clone = div.clone();

        // Remove only the direct heading of the parent div,
        // but keep nested subsection headings as part of the text.
        clone.select("> head").remove();

        StringBuilder sb = new StringBuilder();

        for (Element el : clone.select("p, ab, quote, cit, list, item, label, note, formula, figure, table, head")) {
            if (isInsideTable(el)) continue;

            String chunk = extractBlockText(el);
            if (!chunk.isBlank()) {
                if (!sb.isEmpty()) sb.append("\n\n");
                sb.append(chunk);
            }
        }

        if (sb.isEmpty()) {
            return normalizeWs(clone.text());
        }

        return sb.toString().trim();
    }

    private static String extractFloatingBlocks(Element container) {
        StringBuilder sb = new StringBuilder();

        for (Element child : container.children()) {
            if ("div".equalsIgnoreCase(child.tagName())) continue;

            String chunk = extractBlockText(child);
            if (!chunk.isBlank()) {
                if (!sb.isEmpty()) sb.append("\n\n");
                sb.append(chunk);
            }
        }
        return sb.toString().trim();
    }

    private static String extractBlockText(Element el) {
        if (el == null) return "";
        String tag = el.tagName().toLowerCase(Locale.ROOT);

        if (isInsideTable(el)) return "";

        return switch (tag) {
            case "p", "note", "formula", "list", "item", "head", "ab", "quote", "cit", "label" ->
                    cleanText(el.text());
            case "figure" -> cleanText(Optional.ofNullable(el.selectFirst("figDesc")).map(Element::text).orElse(""));
            default -> "";
        };
    }
}
