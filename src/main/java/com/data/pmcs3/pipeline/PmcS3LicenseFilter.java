package com.data.pmcs3.pipeline;

import java.util.Locale;
import java.util.Set;

/**
 * License filter for PMC S3 articles. PMC provides a concise
 * {@code license_code} enum string in the per-article JSON that is much
 * easier to match on than the various license URL forms used by ArXiv.
 *
 * <p>Accepted (commercial use allowed):
 * <ul>
 *   <li>{@code CC0} — public domain dedication</li>
 *   <li>{@code CC BY} (any version)</li>
 *   <li>{@code CC BY-SA} (any version) — ShareAlike is acceptable</li>
 * </ul>
 *
 * <p>Rejected (any NC or ND variant, or missing license code).</p>
 */
public final class PmcS3LicenseFilter {

    private static final Set<String> ACCEPTED_PREFIXES = Set.of(
            "cc0",
            "cc-by",
            "ccby"
    );

    private PmcS3LicenseFilter() {}

    /**
     * Returns {@code true} if the given PMC license code authorizes commercial
     * use and permits derivative works.
     */
    public static boolean isAcceptable(String licenseCode) {
        if (licenseCode == null || licenseCode.isBlank()) return false;

        String normalized = licenseCode.trim().toLowerCase(Locale.ROOT)
                .replace(" ", "-")
                .replace("_", "-");

        // Reject NonCommercial and NoDerivatives variants immediately.
        if (normalized.contains("-nc")) return false;
        if (normalized.contains("-nd")) return false;

        for (String prefix : ACCEPTED_PREFIXES) {
            if (normalized.startsWith(prefix)) return true;
        }
        return false;
    }
}
