package com.data.arxiv;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

public final class ArxivIdExtractor {
    private ArxivIdExtractor() {}

    // matches:
    // - https://arxiv.org/abs/2502.12345
    // - http://arxiv.org/abs/hep-th/9901001
    // - arXiv:2502.12345
    private static final Pattern ABS_URL = Pattern.compile("arxiv\\.org/abs/([^?#]+)");
    private static final Pattern ARXIV_PREFIX = Pattern.compile("arXiv:([^\\s]+)");

    public static String extract(String paperIdOrUrl, String absUrl) {
        String s = firstNonBlank(absUrl, paperIdOrUrl);

        if (isNull(s)) {
            return null;
        }

        // Try abs URL
        Matcher m1 = ABS_URL.matcher(s);

        if (m1.find()) {
            return normalize(m1.group(1));
        }

        // Try arXiv: prefix
        Matcher m2 = ARXIV_PREFIX.matcher(s);
        if (m2.find()) {
            return normalize(m2.group(1));
        }

        // Fallback: if it's already like "2502.12345" or "hep-th/9901001"
        if (looksLikeArxivId(s)) {
            return normalize(s);
        }

        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (nonNull(a) && !a.isBlank()) {
            return a.trim();
        }

        if (nonNull(b) && !b.isBlank()) {
            return b.trim();
        }

        return null;
    }

    private static String normalize(String x) {
        if (isNull(x)) {
            return null;
        }

        x = x.trim();
        // strip version: 2502.12345v2 -> 2502.12345
        int v = x.indexOf('v');

        if (v > 0 && x.substring(v + 1).matches("\\d+")) {
            x = x.substring(0, v);
        }

        // strip trailing slashes
        while (x.endsWith("/")) {
            x = x.substring(0, x.length() - 1);
        }

        return x;
    }

    private static boolean looksLikeArxivId(String s) {
        // new-style
        if (s.matches("\\d{4}\\.\\d{4,5}")) {
            return true;
        }

        // old-style
        return s.matches("[a-z-]+(\\.[A-Z]{2})?/\\d{7}");
    }
}