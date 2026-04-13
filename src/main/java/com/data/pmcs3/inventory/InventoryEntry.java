package com.data.pmcs3.inventory;

/**
 * A single row from a PMC S3 inventory CSV representing one article version.
 *
 * <p>The inventory CSV emits exactly one row per article version, pointing at
 * the per-article metadata JSON under the flat {@code metadata/} prefix — e.g.
 * {@code metadata/PMC10009416.1.json}. Downstream pipeline stages derive both
 * the metadata JSON key and the per-article asset keys (JATS XML, plain text,
 * PDF) from the fields on this record.
 *
 * @param pmcId    numeric PMC id without the {@code PMC} prefix, e.g. {@code "10009416"}
 * @param version  article version, usually {@code 1}
 * @param keyBase  the per-article directory name, e.g. {@code "PMC10009416.1"}.
 *                 Downstream clients append {@code /PMC{id}.{v}.xml} (or
 *                 {@code .txt}, {@code .pdf}) to get the final S3 key.
 */
public record InventoryEntry(
        String pmcId,
        int version,
        String keyBase
) {

    private static final String METADATA_PREFIX = "metadata/";
    private static final String JSON_SUFFIX = ".json";
    private static final String PMC_PREFIX = "PMC";

    /**
     * Parses an S3 object key from the PMC inventory CSV into an
     * {@link InventoryEntry}, or returns {@code null} if the key does not
     * match the expected shape.
     *
     * <p>The real PMC S3 Inventory publishes each article exactly once with
     * its per-article metadata JSON key, e.g.:
     * <pre>
     *   metadata/PMC10009416.1.json
     *   metadata/PMC6467555.1.json
     * </pre>
     *
     * <p>Any key that does not start with {@code metadata/}, end with
     * {@code .json}, or whose stem is not of the form {@code PMC{id}.{version}}
     * with a numeric id and numeric version is rejected.
     */
    public static InventoryEntry fromS3Key(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (!key.startsWith(METADATA_PREFIX) || !key.endsWith(JSON_SUFFIX)) {
            return null;
        }

        // Strip the "metadata/" prefix and ".json" suffix to isolate the stem.
        String stem = key.substring(METADATA_PREFIX.length(), key.length() - JSON_SUFFIX.length());

        // Reject nested paths — the metadata dir is flat, so no extra slashes allowed.
        if (stem.indexOf('/') >= 0) {
            return null;
        }
        if (!stem.startsWith(PMC_PREFIX)) {
            return null;
        }

        // Strip PMC prefix → "{id}.{version}".
        String idAndVersion = stem.substring(PMC_PREFIX.length());
        int dot = idAndVersion.indexOf('.');
        if (dot <= 0 || dot == idAndVersion.length() - 1) {
            return null;
        }

        String pmcId = idAndVersion.substring(0, dot);
        if (!isAllDigits(pmcId)) {
            return null;
        }

        String versionPart = idAndVersion.substring(dot + 1);
        int version;
        try {
            version = Integer.parseInt(versionPart);
        } catch (NumberFormatException e) {
            return null;
        }
        if (version < 0) {
            return null;
        }

        return new InventoryEntry(pmcId, version, stem);
    }

    private static boolean isAllDigits(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
