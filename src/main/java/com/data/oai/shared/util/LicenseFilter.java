package com.data.oai.shared.util;

import java.util.Locale;

/**
 * Shared license-checking and normalization utilities for OAI sources.
 */
public final class LicenseFilter {

    private LicenseFilter() {}

    /**
     * Normalizes a license string by trimming whitespace and converting
     * {@code http://} to {@code https://}.
     */
    public static String normalizeLicense(String license) {
        if (license == null) return null;
        return license.trim().replace("http://", "https://");
    }

    /**
     * Checks whether a license is commercially usable using an exact URL whitelist.
     * Used by ArXiv which provides license as full CC URLs.
     *
     * <p>Accepts: CC-BY 4.0, CC-BY-SA 4.0, CC0 1.0. Rejects -ND and -NC variants.</p>
     */
    public static boolean isAcceptableByUrlWhitelist(String license) {
        if (license == null || license.isBlank()) {
            return false;
        }

        String l = license.trim().toLowerCase(Locale.ROOT);
        l = l.replace("http://", "https://");

        return l.equals("https://creativecommons.org/licenses/by/4.0/")
                || l.equals("https://creativecommons.org/licenses/by-sa/4.0/")
                || l.equals("https://creativecommons.org/publicdomain/zero/1.0/");
    }

    /**
     * Checks whether a license is permissive enough for commercial use using
     * a reject-first pattern matching approach. Supports configurable rejection
     * of NoDerivatives (ND) and ShareAlike (SA) clauses.
     *
     * <p>Always rejects: NonCommercial (NC), GPL variants.</p>
     * <p>Always accepts: CC0 / Public Domain, CC-BY (any version), MIT, Apache 2.0, BSD.</p>
     *
     * @param license     the license text or URL
     * @param rejectND    if true, licenses containing "-nd" are rejected
     * @param rejectSA    if true, licenses containing "-sa" are rejected
     */
    public static boolean isPermissiveLicense(String license, boolean rejectND, boolean rejectSA) {
        if (license == null || license.isBlank()) {
            return false;
        }

        String l = license.trim()
                .toLowerCase(Locale.ROOT)
                .replace("http://", "https://");

        String compact = l.replaceAll("\\s+", "")
                .replace("_", "-");

        // ---------------------------------------------------
        // EXPLICITLY REJECT
        // ---------------------------------------------------
        if (compact.contains("-nc")) return false;
        if (rejectND && compact.contains("-nd")) return false;
        if (rejectSA && compact.contains("-sa")) return false;
        if (compact.contains("gpl")) return false;
        if (compact.contains("agpl")) return false;
        if (compact.contains("lgpl")) return false;

        // ---------------------------------------------------
        // SAFE CREATIVE COMMONS
        // ---------------------------------------------------
        if (l.contains("publicdomain/zero") || compact.contains("cc0")
                || compact.contains("cc0-1.0")) return true;

        // CC-BY (any version, including SA if not rejected above)
        if (l.contains("creativecommons.org/licenses/by/")) return true;
        if (l.contains("creativecommons.org/licenses/by-sa/")) return true;
        if (compact.contains("cc-by")) return true;

        // CC BY 3.0
        if (l.contains("creativecommons.org/licenses/by/3.0")) return true;
        if (compact.contains("cc-by-3.0")) return true;

        // ---------------------------------------------------
        // PERMISSIVE OSS LICENSES
        // ---------------------------------------------------
        if (compact.contains("mit")) return true;
        if (compact.contains("apache-2.0") || compact.contains("apache2")) return true;
        if (compact.contains("bsd-2-clause") || compact.contains("bsd-3-clause")) return true;
        if (compact.contains("isc")) return true;

        return false;
    }

    /**
     * Returns true if the text looks like a license URL rather than a
     * human-readable description. Used by parsers that receive both prose
     * and URL forms for license metadata (e.g., PubMed Dublin Core).
     */
    public static boolean looksLikeLicenseUrl(String text) {
        if (text == null || text.isBlank()) return false;
        String t = text.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("http://") || t.startsWith("https://");
    }
}
