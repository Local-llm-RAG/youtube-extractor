package com.data.oai.grobid.tei;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shared utility methods used by TEI mapping/extraction classes.
 */
final class GrobidTeiUtils {

    private GrobidTeiUtils() {}

    private static final Pattern RESIDUAL_TAGS = Pattern.compile("<[^>]+>");
    private static final char UNICODE_REPLACEMENT = '\uFFFD';

    static String normalizeWs(String s) {
        if (s == null) return null;
        return s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    static String textOrNull(Element el) {
        if (el == null) return null;
        String t = normalizeWs(el.text());
        return t.isBlank() ? null : t;
    }

    static String firstText(Document doc, String css) {
        Element el = doc.selectFirst(css);
        return el == null ? null : el.text();
    }

    static boolean isInsideTable(Element el) {
        return el.closest("table") != null || el.closest("row") != null || el.closest("cell") != null;
    }

    static String firstRegex(Pattern p, String s) {
        if (s == null) return null;
        var m = p.matcher(s);
        return m.find() ? m.group() : null;
    }

    static void putIfNonBlank(Map<String, String> m, String k, String v) {
        if (m == null || k == null) return;
        if (v != null && !v.isBlank()) m.put(k, v);
    }

    static String safe(String s) {
        return s == null ? "" : s;
    }

    static String firstOrEmpty(List<String> xs) {
        return (xs == null || xs.isEmpty()) ? "" : String.valueOf(xs.getFirst());
    }

    /**
     * Strips residual HTML/XML tags and the Unicode replacement character (U+FFFD)
     * from text extracted by GROBID, then normalizes whitespace.
     */
    static String cleanText(String s) {
        if (s == null) return null;
        String cleaned = RESIDUAL_TAGS.matcher(s).replaceAll("");
        cleaned = cleaned.replace(String.valueOf(UNICODE_REPLACEMENT), "");
        return normalizeWs(cleaned);
    }
}
