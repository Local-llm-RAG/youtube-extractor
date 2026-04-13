package com.data.pmcs3.inventory;

import com.data.pmcs3.client.PmcS3Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/**
 * Discovers the set of PMC S3 articles that are available for processing.
 *
 * <p>PMC publishes a daily inventory under
 * {@code s3://pmc-oa-opendata/inventory-reports/pmc-oa-opendata/metadata/...}
 * — an S3 Inventory report consisting of a JSON manifest that points at one
 * or more gzipped CSV data files. Each CSV row is a single S3 object key.
 *
 * <p>This service fetches the manifest, walks the referenced CSV.gz files,
 * and filters the rows down to JATS XML keys — one per PMC article version.
 * Articles are returned as {@link InventoryEntry} records that subsequent
 * pipeline stages use to compute per-file keys.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final String MANIFEST_SUFFIX = "manifest.json";

    private final PmcS3Client client;

    /**
     * Fetches the most recent inventory and returns the resulting entries.
     * Implementation is intentionally best-effort: on any parsing error the
     * method returns an empty list so the pipeline can continue rather than
     * halt.
     *
     * @param manifestKey the S3 key of the inventory manifest JSON, e.g.
     *                    {@code "inventory-reports/pmc-oa-opendata/metadata/2026-04-10T00-00Z/manifest.json"}
     */
    public List<InventoryEntry> fetchInventory(String manifestKey) {
        Objects.requireNonNull(manifestKey, "manifestKey");
        if (!manifestKey.endsWith(MANIFEST_SUFFIX)) {
            log.warn("Inventory manifestKey does not look like a manifest.json: {}", manifestKey);
        }

        String manifestJson = client.downloadText(manifestKey);
        if (manifestJson == null || manifestJson.isBlank()) {
            log.warn("PMC S3 inventory manifest missing or empty at key={}", manifestKey);
            return List.of();
        }

        List<String> dataKeys = extractDataFileKeys(manifestJson);
        log.info("PMC S3 inventory manifest references {} data file(s)", dataKeys.size());

        List<InventoryEntry> out = new ArrayList<>();
        for (String dataKey : dataKeys) {
            byte[] gz = client.downloadBytes(dataKey);
            if (gz == null) {
                log.warn("PMC S3 inventory data file missing: {}", dataKey);
                continue;
            }
            try {
                parseGzippedCsv(gz, out);
            } catch (IOException e) {
                log.warn("Failed to parse PMC S3 inventory data file {}: {}", dataKey, e.getMessage());
            }
        }
        List<InventoryEntry> deduped = dedupeByPmcIdKeepingHighestVersion(out);
        log.info("PMC S3 inventory produced {} article entries", deduped.size());
        return deduped;
    }

    /**
     * Collapses multiple versions of the same PMC article to a single entry,
     * keeping the one with the highest {@code version}.
     *
     * <p>The PMC S3 inventory emits one row per {@code (pmcId, version)} tuple,
     * but our {@code source_record.source_identifier} column is unique on the
     * numeric PMC id alone. Without dedup here, the second version of any
     * multi-version article hits the unique constraint and gets skipped.
     * "First wins by latest version" matches the semantics downstream
     * consumers expect — the newest version of an article supersedes older ones.
     *
     * <p>Preserves insertion order for deterministic logs and tests.
     */
    static List<InventoryEntry> dedupeByPmcIdKeepingHighestVersion(List<InventoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return entries == null ? List.of() : entries;
        }
        Map<String, InventoryEntry> byPmcId = new LinkedHashMap<>();
        for (InventoryEntry entry : entries) {
            byPmcId.merge(
                    entry.pmcId(),
                    entry,
                    (existing, incoming) -> incoming.version() > existing.version() ? incoming : existing
            );
        }
        int before = entries.size();
        int after = byPmcId.size();
        if (before != after) {
            log.info("Deduped inventory from {} entries to {} unique pmcIds", before, after);
        }
        return new ArrayList<>(byPmcId.values());
    }

    /**
     * Minimal JSON scraper for the {@code "files"} array of an S3 Inventory manifest.
     * Returns the {@code "key"} value of each file object. We deliberately avoid
     * pulling in a JSON library for such a small, well-defined input.
     */
    private static List<String> extractDataFileKeys(String manifestJson) {
        List<String> out = new ArrayList<>();
        String needle = "\"key\"";
        int pos = 0;
        while (true) {
            int idx = manifestJson.indexOf(needle, pos);
            if (idx < 0) break;
            int colon = manifestJson.indexOf(':', idx + needle.length());
            if (colon < 0) break;
            int startQuote = manifestJson.indexOf('"', colon + 1);
            if (startQuote < 0) break;
            int endQuote = manifestJson.indexOf('"', startQuote + 1);
            if (endQuote < 0) break;
            out.add(manifestJson.substring(startQuote + 1, endQuote));
            pos = endQuote + 1;
        }
        return out;
    }

    private static void parseGzippedCsv(byte[] gz, List<InventoryEntry> collector) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(gz));
             BufferedReader reader = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String key = extractKeyField(line);
                if (key == null) continue;
                InventoryEntry entry = InventoryEntry.fromS3Key(key);
                if (entry != null) {
                    collector.add(entry);
                }
            }
        }
    }

    /**
     * Extracts the {@code key} column from an S3 Inventory CSV row.
     *
     * <p>The Inventory schema places bucket in column 0 and key in column 1,
     * both quoted. Values may contain commas so we cannot simply split on ','.
     */
    private static String extractKeyField(String line) {
        if (line == null || line.isBlank()) return null;
        int first = line.indexOf('"');
        if (first < 0) return null;
        int firstEnd = line.indexOf('"', first + 1);
        if (firstEnd < 0) return null;
        int second = line.indexOf('"', firstEnd + 1);
        if (second < 0) return null;
        int secondEnd = line.indexOf('"', second + 1);
        if (secondEnd < 0) return null;
        return line.substring(second + 1, secondEnd);
    }
}
