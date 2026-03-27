package com.data.oai.grobid.tei;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.function.Consumer;

import static com.data.oai.grobid.tei.GrobidTeiUtils.*;

/**
 * Extracts plain text, keywords, affiliations, class codes, and document type
 * from TEI-XML documents parsed by GROBID.
 */
final class GrobidTextExtractor {

    private GrobidTextExtractor() {}

    private static final Set<String> BLOCK_TAGS = Set.of(
            "head", "p", "ab", "quote", "cit", "list", "item", "label", "note", "formula", "figdesc"
    );

    static List<String> extractKeywords(Document tei) {
        return tei.select("teiHeader profileDesc textClass keywords term")
                .eachText()
                .stream()
                .map(GrobidTeiUtils::normalizeWs)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    static String extractDocType(Document tei) {
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
    static List<String> extractAffiliations(Document tei) {
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

    static List<String> extractClassCodes(Document tei) {
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
     * Improved plain-text view intended for search/fulltext.
     * - does NOT dump table cells (we don't traverse table structures)
     * - keeps figure captions (figDesc)
     * - keeps document order better (walks text subtree)
     */
    static String teiToPlainText(Document tei) {
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

    private static void walkInDocumentOrder(Element root, Consumer<Node> visitor) {
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
}
