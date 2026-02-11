package com.youtube.service.qdrant;

import com.youtube.external.rest.arxiv.dto.PaperDocument;
import com.youtube.external.rest.arxiv.dto.Section;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.util.*;
import java.util.stream.Collectors;

public final class GrobidTeiMapperJsoup {

    public static PaperDocument toPaperDocument(String arxivId, String oaiIdentifier, String teiXml) {
        if (teiXml == null || teiXml.isBlank()) {
            return new PaperDocument(arxivId, oaiIdentifier, null, List.of(), null,
                    List.of(new Section("BODY", 1, "")), teiXml);
        }

        Document tei = Jsoup.parse(teiXml, "", Parser.xmlParser());

        //here title is handled, depending on different grobid versions
        String title = firstText(tei, "teiHeader titleStmt > title");
        if (isBlank(title)) title = firstText(tei, "teiHeader sourceDesc biblStruct analytic title");

        //here authors are handled, depending on different grobid versions and their output
        List<String> authors = tei.select("teiHeader sourceDesc biblStruct analytic author persName")
                .stream()
                .map(GrobidTeiMapperJsoup::extractAuthorName)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        //Normalize because grobid include line breaks
        String abstractText = normalizeWs(firstText(tei, "teiHeader profileDesc abstract"));

        // Extract sections and if there is not fallback to default 1 section with whole text
        List<Section> sections = extractSections(tei);
        if (sections.isEmpty() || sections.stream().allMatch(s -> s.text().isBlank())) {
            String bodyText = normalizeWs(firstText(tei, "text > body"));
            sections = List.of(new Section("BODY", 1, bodyText));
        }

        return new PaperDocument(
                arxivId,
                oaiIdentifier,
                title,
                authors,
                abstractText,
                sections,
                teiXml);
    }

    private static String extractAuthorName(Element persName) {
        if (persName == null) return "";

        List<String> parts = new ArrayList<>();

        // Forenames (can be multiple)
        persName.select("forename").forEach(fn -> {
            String t = normalizeWs(fn.text());
            if (!t.isBlank()) parts.add(t);
        });

        // Surname
        String surname = normalizeWs(persName.selectFirst("surname") != null
                ? persName.selectFirst("surname").text()
                : "");
        if (!surname.isBlank()) parts.add(surname);

        // Fallback if structure is weird
        if (parts.isEmpty()) {
            return normalizeWs(persName.text());
        }

        return String.join(" ", parts);
    }

    private static List<Section> extractSections(Document tei) {
        // Taking divs with heads, because they are sections with primary information
        List<Element> divs = tei.select("text > body div:has(> head)");

        if (divs.isEmpty()) return List.of();

        List<Section> out = new ArrayList<>();

        for (Element div : divs) {
            // Clear the title of the section from semantics like 1. a), etc.
            String head = normalizeWs(div.selectFirst("> head").text());
            String sectionTitle = normalizeSectionTitle(head);
            if (sectionTitle.isBlank()) continue;

            // How nested is the current section
            int level = Math.max(1, div.parents().stream().filter(p -> p.tagName().equals("div")).toList().size() + 1);

            // Get text from direct-ish paragraphs only (avoid nested div paragraphs)
            // Select p where the closest parent div is THIS div
            List<String> texts = new ArrayList<>();
            div.select("p").stream()
                    .filter(p -> closestDiv(p) == div)
                    .map(Element::text)
                    .map(GrobidTeiMapperJsoup::normalizeWs)
                    .filter(s -> !s.isBlank())
                    .forEach(texts::add);

            // list items (bullet points, steps)
            div.select("list > item").stream()
                    .filter(i -> closestDiv(i) == div)
                    .map(Element::text)
                    .map(GrobidTeiMapperJsoup::normalizeWs)
                    .filter(s -> !s.isBlank())
                    .forEach(texts::add);

            // figure captions (very valuable)
            div.select("figure figDesc").stream()
                    .filter(f -> closestDiv(f) == div)
                    .map(Element::text)
                    .map(GrobidTeiMapperJsoup::normalizeWs)
                    .filter(s -> !s.isBlank())
                    .forEach(texts::add);

            String text = String.join("\n\n", texts).trim();
            if (text.isBlank()) continue;

            out.add(new Section(sectionTitle, level, text));
        }

        // cheap de-dupe
        return out.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(s -> s.title() + "::" + s.text().hashCode(), s -> s, (a, b) -> a, LinkedHashMap::new),
                        m -> List.copyOf(m.values())));
    }

    private static Element closestDiv(Element el) {
        Element cur = el.parent();
        while (cur != null) {
            if ("div".equals(cur.tagName())) return cur;
            cur = cur.parent();
        }
        return null;
    }

    private static String firstText(Document doc, String css) {
        Element el = doc.selectFirst(css);
        return el == null ? null : el.text();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String normalizeWs(String s) {
        if (s == null) return null;
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String normalizeSectionTitle(String head) {
        if (head == null) return "";
        String s = normalizeWs(head);
        if (s == null) return "";
        s = s.replaceFirst("^(\\(?[IVXLC]+\\)?\\s*[\\.:\\)]\\s+)", "");
        s = s.replaceFirst("^(\\d+(?:\\.\\d+)*\\s*[\\.:\\)]\\s+)", "");
        return s.trim();
    }
}
