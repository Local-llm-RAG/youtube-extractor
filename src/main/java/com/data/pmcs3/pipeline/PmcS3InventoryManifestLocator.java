package com.data.pmcs3.pipeline;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Pure XML parsing helper that locates the newest PMC inventory manifest key
 * from an S3 {@code ListObjectsV2} response XML body.
 *
 * <p>The PMC Open Access bucket publishes daily inventory folders under
 * {@code inventory-reports/pmc-oa-opendata/metadata/YYYY-MM-DDTHH-MMZ/}.
 * As of 2026-04, the hour component is {@code 01-00Z} (01:00 UTC), but hardcoding
 * that assumption has bitten us before. Instead we list the common prefixes
 * directly and pick the lexicographically largest one (ISO-8601 timestamps
 * sort correctly as strings).
 *
 * <p>Kept deliberately free of I/O so it can be unit-tested with canned XML.
 */
public final class PmcS3InventoryManifestLocator {

    private static final String MANIFEST_FILENAME = "manifest.json";

    /**
     * Matches the last path segment of an inventory day-folder prefix, e.g.
     * {@code 2026-04-11T01-00Z}. The S3 listing also returns sibling prefixes
     * such as {@code hive/} (inventory partition metadata), which we must
     * skip — probing them for a {@code manifest.json} is guaranteed to 404
     * and just pollutes the logs.
     */
    private static final Pattern DAY_FOLDER_PATTERN =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}Z");

    private PmcS3InventoryManifestLocator() {}

    /**
     * Parses an S3 {@code ListObjectsV2} XML body and returns the key of the
     * newest inventory manifest, or {@code null} if no day-folder prefix is
     * present in the response.
     *
     * <p>Example input fragment:
     * <pre>{@code
     * <ListBucketResult>
     *   <CommonPrefixes>
     *     <Prefix>inventory-reports/pmc-oa-opendata/metadata/2026-04-11T01-00Z/</Prefix>
     *   </CommonPrefixes>
     *   ...
     * </ListBucketResult>
     * }</pre>
     *
     * @param listObjectsV2Xml raw XML body returned by the S3 list call
     * @return the full S3 key of the latest {@code manifest.json}, or {@code null}
     *         if the XML contains no day-folder common prefix
     */
    public static String findLatestManifestKey(String listObjectsV2Xml) {
        if (listObjectsV2Xml == null || listObjectsV2Xml.isBlank()) {
            return null;
        }
        Document doc = Jsoup.parse(listObjectsV2Xml, "", Parser.xmlParser());
        // ISO-8601 timestamps in the prefix sort correctly as plain strings,
        // so a lexicographic max gives us the latest published day.
        return doc.select("CommonPrefixes > Prefix").stream()
                .map(el -> el.text())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                // Strip any trailing slash so prefixes compare cleanly and we can
                // re-append the manifest filename uniformly.
                .map(s -> s.endsWith("/") ? s.substring(0, s.length() - 1) : s)
                // Only accept day-folder prefixes whose last segment matches the
                // ISO timestamp pattern. This filters out siblings like "hive/"
                // that live alongside the real daily folders under the inventory
                // prefix but have no manifest.json.
                .filter(s -> {
                    int lastSlash = s.lastIndexOf('/');
                    String lastSegment = lastSlash < 0 ? s : s.substring(lastSlash + 1);
                    return DAY_FOLDER_PATTERN.matcher(lastSegment).matches();
                })
                .max(Comparator.naturalOrder())
                .map(p -> p + "/" + MANIFEST_FILENAME)
                .orElse(null);
    }
}
