package com.data.grobid;

import com.data.oai.generic.common.dto.PaperDocument;
import com.data.oai.generic.common.dto.Reference;
import com.data.oai.generic.common.dto.Section;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class GrobidTeiMapperJsoup {

    private GrobidTeiMapperJsoup() {}

    private static final Pattern DOI =
            Pattern.compile("(?i)\\b10\\.\\d{4,9}/[-._;()/:A-Z0-9]+\\b");

    private static final Set<String> BLOCK_TAGS = Set.of(
            "head", "p", "ab", "quote", "cit", "list", "item", "label", "note", "formula", "figdesc"
    );

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

        List<Reference> references = extractReferences(tei);

        String docType = extractDocType(tei);

        // Improved: recursive, preserves nested divs and avoids dropping subsection text.
        List<Section> sections = extractSections(tei);
        if (sections.isEmpty() || sections.stream().allMatch(s -> s.text().isBlank())) {
            String bodyText = normalizeWs(firstText(tei, "text > body"));
            sections = List.of(new Section("BODY", 1, bodyText == null ? "" : bodyText));
        }

        // Improved: no re-parse; preserve order better; avoid table dumps but keep captions.
        String rawContent = teiToPlainText(tei);

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

    /**
     * Improved affiliation extraction:
     * - includes teiHeader affiliation nodes
     * - includes per-author affiliation nodes under analytic author
     * - normalizes orgName parts, then falls back to full text.
     */
    private static List<String> extractAffiliations(Document tei) {
        List<String> result = new ArrayList<>();

        Elements affiliations = tei.select(
                "teiHeader author affiliation, " +
                        "teiHeader affiliation, " +
                        "teiHeader fileDesc sourceDesc biblStruct analytic author affiliation"
        );

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

            for (String token : raw.split("[,;\\s]+")) {
                String code = token.trim();
                if (!code.isBlank()) out.add(code);
            }
        }

        return out.stream().distinct().toList();
    }

    /**
     * Improved references extraction:
     * - handles back listBibl biblStruct
     * - falls back to back listBibl bibl
     * - handles references divs
     * - captures more venue-ish data into idnos map (publisher/place/volume/issue/pages)
     */
    public static List<Reference> extractReferences(Document tei) {
        List<Reference> out = new ArrayList<>();
        int idx = 0;

        // biblStruct first
        Elements biblStructs = tei.select(
                "back listBibl biblStruct, " +
                        "back div[type=references] biblStruct, " +
                        "back div[subtype=references] biblStruct"
        );

        for (Element bibl : biblStructs) {
            idx++;
            Reference r = parseBiblStruct(idx, bibl);
            if (r != null) out.add(r);
        }

        // fallback: plain bibl (non-structured refs)
        Elements bibls = tei.select(
                "back listBibl > bibl, " +
                        "back div[type=references] > bibl, " +
                        "back div[subtype=references] > bibl"
        );

        for (Element bibl : bibls) {
            idx++;
            Reference r = parseBibl(idx, bibl);
            if (r != null) out.add(r);
        }

        return out.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                r -> safe(r.analyticTitle()) + "::" + safe(r.monogrTitle()) + "::" +
                                        safe(r.doi()) + "::" + firstOrEmpty(r.urls()) + "::" + safe(r.year()),
                                r -> r,
                                (a, b) -> a,
                                LinkedHashMap::new
                        ),
                        m -> List.copyOf(m.values())
                ));
    }

    private static Reference parseBiblStruct(int idx, Element bibl) {
        String analyticTitle = textOrNull(bibl.selectFirst("analytic > title"));
        String monogrTitle   = textOrNull(bibl.selectFirst("monogr > title"));
        String bestTitle = !isBlank(analyticTitle) ? analyticTitle : monogrTitle;

        String doi = normalizeDoi(extractDoi(bibl));

        List<String> urls = bibl.select("ptr[target], ref[target], idno[type=url]")
                .stream()
                .map(e -> {
                    if (e.hasAttr("target")) return normalizeWs(e.attr("target"));
                    return normalizeWs(e.text());
                })
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        List<String> authors = bibl.select("author persName")
                .eachText()
                .stream()
                .map(GrobidTeiMapperJsoup::normalizeWs)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        String year = extractYear(bibl);

        String venue = textOrNull(bibl.selectFirst("monogr > title"));

        Map<String, String> idnos = extractIdnos(bibl);

        putIfNonBlank(idnos, "publisher", textOrNull(bibl.selectFirst("monogr imprint publisher")));
        putIfNonBlank(idnos, "pubplace", textOrNull(bibl.selectFirst("monogr imprint pubPlace")));
        putIfNonBlank(idnos, "date", textOrNull(bibl.selectFirst("monogr imprint date[when], monogr imprint date")));
        for (Element scope : bibl.select("monogr imprint biblScope[unit]")) {
            String unit = normalizeWs(scope.attr("unit")).toLowerCase(Locale.ROOT);
            String val = normalizeWs(scope.hasAttr("from") ? scope.attr("from") : scope.text());
            if (!unit.isBlank() && !val.isBlank()) {
                idnos.put("biblscope_" + unit, val);
            }
        }

        if (isBlank(bestTitle) && isBlank(doi) && urls.isEmpty() && authors.isEmpty()) return null;

        return new Reference(idx, analyticTitle, monogrTitle, doi, urls, authors, year, venue, idnos);
    }

    private static Reference parseBibl(int idx, Element bibl) {
        String raw = normalizeWs(bibl.text());
        if (raw.isBlank()) return null;

        String doi = normalizeDoi(firstRegex(DOI, raw));

        Map<String, String> idnos = new LinkedHashMap<>();
        if (doi != null) idnos.put("doi", doi);

        idnos.put("raw_reference", raw);

        List<String> urls = bibl.select("ptr[target], ref[target]")
                .stream()
                .map(e -> normalizeWs(e.attr("target")))
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        return new Reference(idx, null, null, doi, urls, List.of(), null, null, idnos);
    }

    private static String extractYear(Element bibl) {
        Element date = bibl.selectFirst("imprint date[when], imprint date, date[when], date");
        if (date != null) {
            String raw = normalizeWs(date.hasAttr("when") ? date.attr("when") : date.text());
            if (raw != null && raw.length() >= 4) return raw.substring(0, 4);
        }
        // fallback: parse year from raw reference if present
        Element raw = bibl.selectFirst("note[type=raw_reference], note[type=rawRef], note[type=raw]");
        String rawText = raw != null ? raw.text() : bibl.text();
        String y = firstRegex(Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b"), rawText);
        return y;
    }

    private static Map<String, String> extractIdnos(Element bibl) {
        Map<String, String> idnos = new LinkedHashMap<>();
        for (Element idnoEl : bibl.select("idno")) {
            String type = normalizeWs(idnoEl.attr("type"));
            String val  = normalizeWs(idnoEl.text());
            if (!val.isBlank()) {
                idnos.put(type.isBlank() ? "unknown" : type.toLowerCase(Locale.ROOT), val);
            }
        }
        return idnos;
    }

    /**
     * Improved plain-text view intended for search/fulltext.
     * - does NOT dump table cells (we don't traverse table structures)
     * - keeps figure captions (figDesc)
     * - keeps document order better (walks text subtree)
     */
    private static String teiToPlainText(Document tei) {
        if (tei == null) return null;

        Element textRoot = tei.selectFirst("TEI > text");
        Element root = (textRoot != null) ? textRoot : tei;

        StringBuilder out = new StringBuilder(4096);

        walkInDocumentOrder(root, node -> {
            if (!(node instanceof Element el)) return;

            String tag = el.tagName().toLowerCase(Locale.ROOT);

            if (isInsideTable(el)) return;

            if ("figure".equals(tag)) {
                Element figDesc = el.selectFirst("figDesc");
                String t = normalizeWs(figDesc != null ? figDesc.text() : "");
                appendBlock(out, t);
                return;
            }

            if (BLOCK_TAGS.contains(tag)) {
                String t = normalizeWs(el.text());
                if (!t.isBlank()) appendBlock(out, t);
            }
        });

        if (out.isEmpty()) {
            out.append(normalizeWs(root.text()));
        }

        return out.toString().trim();
    }

    private static boolean isInsideTable(Element el) {
        return el.closest("table") != null || el.closest("row") != null || el.closest("cell") != null;
    }

    private static void walkInDocumentOrder(Element root, java.util.function.Consumer<Node> visitor) {
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            Node n = stack.pop();
            visitor.accept(n);

            List<Node> children = n.childNodes();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
    }

    private static void appendBlock(StringBuilder out, String block) {
        String t = normalizeWs(block);
        if (t == null || t.isBlank()) return;
        if (!out.isEmpty()) out.append("\n\n");
        out.append(t);
    }

    private static List<Section> extractSections(Document tei) {
        Element text = tei.selectFirst("text");
        if (text == null) return List.of();

        List<Section> out = new ArrayList<>();

        for (Element container : text.select("> front, > body, > back")) {
            String containerName = container.tagName().equals("back") ? "BACK"
                    : container.tagName().equals("front") ? "FRONT"
                    : "BODY";

            for (Element div : container.select("> div")) {
                walkDivAsSections(out, div, 1);
            }

            // Then capture any floating blocks not inside a div as a generic section
            String floating = extractFloatingBlocks(container);
            if (!floating.isBlank() && out.stream().noneMatch(s -> s.title().equals(containerName) && s.level() == 1)) {
                out.add(new Section(containerName, 1, floating));
            }
        }

        // de-dupe by (title + text hash) keeping order
        return out.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                s -> s.title() + "::" + s.text().hashCode(),
                                s -> s,
                                (a, b) -> a, LinkedHashMap::new
                        ),
                        m -> List.copyOf(m.values())
                ));
    }

    private static void walkDivAsSections(List<Section> out, Element div, int level) {
        String head = "";
        Element headEl = div.selectFirst("> head");
        if (headEl != null) head = cleanedHeadText(headEl);

        if (head.isBlank()) {
            String type = div.attr("type");
            if (!type.isBlank()) head = type.toUpperCase(Locale.ROOT);
        }

        String sectionTitle = normalizeSectionTitle(head);
        if (sectionTitle.isBlank()) sectionTitle = "SECTION";

        String text = extractDivTextExcludingNestedDivs(div);
        if (!text.isBlank()) {
            out.add(new Section(sectionTitle, Math.max(1, level), text));
        }

        // Recurse into nested divs as subsections
        for (Element childDiv : div.select("> div")) {
            walkDivAsSections(out, childDiv, level + 1);
        }
    }

    private static String extractDivTextExcludingNestedDivs(Element div) {
        if (div == null) return "";

        Element clone = div.clone();
        clone.select("div").remove();
        clone.select("> head").remove();

        StringBuilder sb = new StringBuilder();

        for (Element el : clone.select("p, ab, quote, cit, list, item, label, note, formula, figure")) {
            if (isInsideTable(el)) continue;

            String chunk;
            if ("figure".equalsIgnoreCase(el.tagName())) {
                Element figDesc = el.selectFirst("figDesc");
                chunk = normalizeWs(figDesc != null ? figDesc.text() : "");
            } else {
                chunk = normalizeWs(el.text());
            }

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
                    normalizeWs(el.text());
            case "figure" -> normalizeWs(Optional.ofNullable(el.selectFirst("figDesc")).map(Element::text).orElse(""));
            default -> "";
        };
    }

    private static String cleanedHeadText(Element head) {
        return normalizeWs(head.text());
    }

    private static String normalizeDoi(String doi) {
        if (doi == null) return null;
        String d = doi.trim();

        d = d.replaceFirst("(?i)^https?://doi\\.org/", "");
        d = d.replaceFirst("(?i)^doi:\\s*", "");
        d = d.replaceAll("[\\s\\p{Punct}]+$", "");
        d = d.toLowerCase(Locale.ROOT);

        return d.isBlank() ? null : d;
    }

    private static String extractDoi(Element bibl) {
        Element doiEl = bibl.selectFirst("idno[type=DOI], idno[type=doi]");
        if (doiEl != null) {
            String hit = firstRegex(DOI, doiEl.text());
            if (!isBlank(hit)) return hit;
        }

        Element raw = bibl.selectFirst("note[type=raw_reference], note[type=rawRef], note[type=raw]");
        String rawText = raw != null ? raw.text() : bibl.text();
        return firstRegex(DOI, rawText);
    }

    private static String firstRegex(Pattern p, String s) {
        if (s == null) return null;
        var m = p.matcher(s);
        return m.find() ? m.group() : null;
    }

    private static String textOrNull(Element el) {
        if (el == null) return null;
        String t = normalizeWs(el.text());
        return t.isBlank() ? null : t;
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
        return s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String normalizeSectionTitle(String head) {
        if (head == null) return "";
        return normalizeWs(head);
    }

    private static void putIfNonBlank(Map<String, String> m, String k, String v) {
        if (m == null || k == null) return;
        if (v != null && !v.isBlank()) m.put(k, v);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String firstOrEmpty(List<String> xs) {
        return (xs == null || xs.isEmpty()) ? "" : String.valueOf(xs.getFirst());
    }
}