package com.data.pmcs3.inventory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A single row from a PMC S3 inventory CSV representing one article version.
 *
 * <p>The inventory CSV emits exactly one row per article version, pointing at
 * the per-article metadata JSON under the flat {@code metadata/} prefix — e.g.
 * {@code metadata/PMC10009416.1.json}. Downstream pipeline stages derive both
 * the metadata JSON key and the per-article asset keys (JATS XML, plain text,
 * PDF) from the fields on this record.
 *
 * @param pmcId   numeric PMC id without the {@code PMC} prefix, e.g. {@code "10009416"}
 * @param version article version, usually {@code 1}
 * @param keyBase the per-article directory name, e.g. {@code "PMC10009416.1"}.
 *                Downstream clients append {@code /PMC{id}.{v}.xml} (or
 *                {@code .txt}, {@code .pdf}) to get the final S3 key.
 */
public record InventoryEntry(
        String pmcId,
        int version,
        String keyBase
) {

    /**
     * Matches keys of the form {@code metadata/PMC{digits}.{digits}.json}.
     * Group 1 = pmcId digits, group 2 = version digits.
     */
    private static final Pattern KEY_PATTERN =
            Pattern.compile("^metadata/PMC(\\d+)\\.(\\d+)\\.json$");

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
     * <p>Any key that does not match {@code metadata/PMC{id}.{version}.json}
     * with a numeric id and numeric version is rejected (returns {@code null}).
     */
    public static InventoryEntry fromS3Key(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        Matcher m = KEY_PATTERN.matcher(key);
        if (!m.matches()) {
            return null;
        }
        String pmcId = m.group(1);
        int version = Integer.parseInt(m.group(2));;
        if (version < 0) {
            return null;
        }
        // keyBase is the stem used by downstream clients to build asset keys,
        // e.g. "PMC10009416.1" (prefix stripped, suffix stripped).
        String keyBase = "PMC" + pmcId + "." + version;
        return new InventoryEntry(pmcId, version, keyBase);
    }
}
