package com.data.grobid;

import com.data.oai.generic.common.dto.PaperDocument;
import com.data.oai.generic.common.dto.Section;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.stream.Collectors;

public final class GrobidTeiMapperJsoup {

    public static PaperDocument toArxivPaperDocument(String arxivId, String oaiIdentifier, String teiXml) {
        if (teiXml == null || teiXml.isBlank()) {
            return new PaperDocument(arxivId, oaiIdentifier, null, null,
                    List.of(new Section("BODY", 1, "")), teiXml, "NO_CONTENT",
                    List.of(), List.of(), List.of(), List.of(), null);
        }

        Document tei = Jsoup.parse(teiXml, "", Parser.xmlParser());

        String title = firstText(tei, "teiHeader titleStmt > title");
        if (isBlank(title)) title = firstText(tei, "teiHeader sourceDesc biblStruct analytic title");

        String abstractText = normalizeWs(firstText(tei, "teiHeader profileDesc abstract"));
        List<String> keywords = extractKeywords(tei);
        List<String> affiliation = extractAffiliations(tei);
        List<String> classCodes = extractClassCodes(tei);
        List<String> references = extractReferences(tei);
        String docType = extractDocType(tei);

        List<Section> sections = extractSections(tei);
        if (sections.isEmpty() || sections.stream().allMatch(s -> s.text().isBlank())) {
            String bodyText = normalizeWs(firstText(tei, "text > body"));
            sections = List.of(new Section("BODY", 1, bodyText == null ? "" : bodyText));
        }

        String rawContent = teiToPlainText(teiXml);

        return new PaperDocument(
                arxivId,
                oaiIdentifier,
                title,
                abstractText,
                sections,
                teiXml,
                rawContent,
                keywords,
                affiliation,
                classCodes,
                references,
                docType
        );
    }

    private static List<String> extractKeywords(Document tei) {
        return tei.select("teiHeader profileDesc textClass keywords term")
                .eachText()
                .stream()
                .map(GrobidTeiMapperJsoup::normalizeWs)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private static String extractDocType(Document tei) {
        Element text = tei.selectFirst("TEI > text");
        if (text != null) {
            String type = text.attr("type");
            if (!type.isBlank()) return normalizeWs(type);
        }
        Element teiRoot = tei.selectFirst("TEI");
        if (teiRoot != null) {
            String subtype = teiRoot.attr("subtype");
            if (!subtype.isBlank()) return normalizeWs(subtype);
        }

        return null;
    }

    private static List<String> extractAffiliations(Document tei) {
        List<String> result = new ArrayList<>();

        Elements affiliations = tei.select("teiHeader author affiliation, teiHeader affiliation");

        for (Element aff : affiliations) {
            List<String> parts = new ArrayList<>();
            for (Element dep : aff.select("orgName[type=department]")) {
                String t = normalizeWs(dep.text());
                if (!t.isBlank()) parts.add(t);
            }
            for (Element inst : aff.select("orgName[type=institution]")) {
                String t = normalizeWs(inst.text());
                if (!t.isBlank()) parts.add(t);
            }
            for (Element org : aff.select("orgName:not([type=department]):not([type=institution])")) {
                String t = normalizeWs(org.text());
                if (!t.isBlank()) parts.add(t);
            }

            String joined = normalizeWs(String.join(", ", parts));
            if (joined.isBlank()) joined = normalizeWs(aff.text());

            if (!joined.isBlank()) result.add(joined);
        }

        return result.stream().distinct().toList();
    }

    private static List<String> extractClassCodes(Document tei) {
        List<String> out = new ArrayList<>();

        for (Element cc : tei.select("teiHeader profileDesc textClass classCode")) {
            String raw = normalizeWs(cc.text());
            if (raw.isBlank()) continue;

            // Split common separators
            for (String token : raw.split("[,;\\s]+")) {
                String code = token.trim();
                if (!code.isBlank()) out.add(code);
            }
        }

        return out.stream().distinct().toList();
    }

    private static List<String> extractReferences(Document tei) {
        List<String> references = new ArrayList<>();

        for (Element bibl : tei.select("back listBibl biblStruct")) {

            Element analyticTitle = bibl.selectFirst("analytic > title");
            if (analyticTitle != null && !analyticTitle.text().isBlank()) {
                references.add(normalizeWs(analyticTitle.text()));
            }

            Element monogrTitle = bibl.selectFirst("monogr > title");
            if (monogrTitle != null && !monogrTitle.text().isBlank()) {
                references.add(normalizeWs(monogrTitle.text()));
            }

            for (Element idno : bibl.select("idno")) {
                String val = normalizeWs(idno.text());
                if (!val.isBlank()) {
                    references.add(val);
                }
            }

            // URLs
            for (Element ptr : bibl.select("ptr[target]")) {
                String target = ptr.attr("target");
                if (!target.isBlank()) {
                    references.add(target.trim());
                }
            }
        }

        return references.stream()
                .distinct()
                .toList();
    }

    /**
     * Plain-text view intended for search/fulltext. Avoid table dumps; keep fig captions.
     */
    private static String teiToPlainText(String teiXml) {
        if (teiXml == null || teiXml.isBlank()) return null;
        Document doc = Jsoup.parse(teiXml, "", Parser.xmlParser());
        Element textRoot = doc.selectFirst("TEI > text");
        Element root = (textRoot != null) ? textRoot : doc;

        StringBuilder out = new StringBuilder(Math.max(teiXml.length(), 1024));

        for (Element el : root.select("head, p, formula, item, figDesc, note, label, list, ab, quote, cit")) {
            String t = normalize(el.text());
            if (!t.isEmpty()) {
                if (!out.isEmpty()) out.append("\n\n");
                out.append(t);
            }
        }

        if (out.isEmpty()) out.append(normalize(root.text()));
        return out.toString().trim();
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static List<Section> extractSections(Document tei) {
        Element text = tei.selectFirst("text");
        if (text == null) return List.of();

        List<Section> out = new ArrayList<>();

        for (Element container : text.select("> body, > back")) {

            for (Element child : container.children()) {
                String tag = child.tagName();

                if ("div".equals(tag)) {
                    extractDivAsSection(out, child);
                } else {
                    // capture floating material (but do not dump tables)
                    String floating = extractBlockText(child);
                    if (!floating.isBlank()) {
                        if (!out.isEmpty() && looksLikeCaptionOrTableDump(floating)) {
                            Section prev = out.getLast();
                            out.set(out.size() - 1, new Section(prev.title(), prev.level(), joinBlocks(prev.text(), floating)));
                        } else if (out.isEmpty()) {
                            out.add(new Section(container.tagName().equals("back") ? "BACK" : "BODY", 1, floating));
                        }
                    }
                }
            }
        }

        // de-dupe by (title + text hash) keeping order
        return out.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                s -> s.title() + "::" + s.text().hashCode(),
                                s -> s,
                                (a, _) -> a, LinkedHashMap::new), m -> List.copyOf(m.values())));
    }

    private static void extractDivAsSection(List<Section> out, Element div) {
        int level = Math.max(1, (int) div.parents().stream().filter(p -> p.tagName().equals("div")).count() + 1);

        Element headEl = div.selectFirst("> head");
        String head = headEl != null ? cleanedHeadText(headEl) : "";

        if (head.isBlank()) {
            String type = div.attr("type");
            if (!type.isBlank()) head = type.toUpperCase(Locale.ROOT);
        }

        String sectionTitle = normalizeSectionTitle(head);
        String text = extractDivContentPlain(div);
        if (text.isBlank()) return;

        out.add(new Section(sectionTitle, level, text));
    }

    private static String extractBlockText(Element el) {
        if (el == null) return "";
        String tag = el.tagName();
        return switch (tag) {
            case "p", "note", "formula", "list", "item", "head" -> normalizeWs(el.text());
            case "figure" -> normalizeWs(Optional.ofNullable(el.selectFirst("figDesc")).map(Element::text).orElse(""));
            default -> "";
        };
    }

    private static boolean looksLikeCaptionOrTableDump(String text) {
        if (text == null) return true;
        String t = normalizeWs(text).toLowerCase(Locale.ROOT);
        if (t.isBlank()) return true;
        return !t.startsWith("fig.") && !t.startsWith("figure")
                && !t.startsWith("table");
    }

    private static String cleanedHeadText(Element head) {
        return normalizeWs(head.text());
    }

    /**
     * Main content extractor
     */
    private static String extractDivContentPlain(Element div) {
        StringBuilder sb = new StringBuilder();

        for (Element child : div.children()) {

            String tag = child.tagName();
            if ("head".equals(tag)) continue;
            if ("div".equals(tag)) continue;

            String chunk = switch (tag) {
                case "p", "note", "formula", "list", "item", "ab", "quote", "cit", "label" -> normalizeWs(child.text());
                case "figure" ->
                        normalizeWs(Optional.ofNullable(child.selectFirst("figDesc")).map(Element::text).orElse(""));
                default -> "";
            };

            if (!chunk.isBlank()) {
                if (!sb.isEmpty()) sb.append("\n\n");
                sb.append(chunk);
            }
        }
        return sb.toString().trim();
    }

    private static String joinBlocks(String a, String b) {
        if (a == null || a.isBlank()) return b == null ? "" : b;
        if (b == null || b.isBlank()) return a;
        return a + "\n\n" + b;
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
        return normalizeWs(head);
    }
}
