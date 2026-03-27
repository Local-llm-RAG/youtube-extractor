package com.data.oai.shared.util;

import java.util.Locale;

public final class DoiNormalizer {

    private DoiNormalizer() {}

    public static String normalize(String doi) {
        if (doi == null) return null;
        String d = doi.trim();

        d = d.replaceFirst("(?i)^https?://doi\\.org/", "");
        d = d.replaceFirst("(?i)^doi:\\s*", "");
        d = d.replaceAll("[\\s\\p{Punct}]+$", "");
        d = d.toLowerCase(Locale.ROOT);

        return d.isBlank() ? null : d;
    }
}
