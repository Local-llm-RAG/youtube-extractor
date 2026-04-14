package com.data.pmcs3.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.data.pmcs3.client.PmcS3Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
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

    /**
     * Thread-safe Jackson CSV mapper. S3 Inventory CSVs have no header row;
     * columns are fixed: bucket (index 0), key (index 1).
     */
    private static final CsvMapper CSV_MAPPER = new CsvMapper();

    /**
     * Schema for the headerless S3 Inventory CSV format.
     * Column 0 = bucket name, column 1 = object key.
     * Both fields are always quoted in practice, but CsvMapper handles that transparently.
     */
    private static final CsvSchema INVENTORY_CSV_SCHEMA = CsvSchema.builder()
            .addColumn("bucket")
            .addColumn("key")
            .build();

    private final PmcS3Client client;
    private final ObjectMapper objectMapper;

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

        List<InventoryEntry> out = dataKeys.stream()
                .flatMap(this::downloadAndParseSafely)
                .toList();

        List<InventoryEntry> deduped = dedupeByPmcIdKeepingHighestVersion(out);
        log.info("PMC S3 inventory produced {} article entries", deduped.size());
        return deduped;
    }

    /**
     * Downloads and parses a single inventory CSV.gz data file.
     * Returns a stream of {@link InventoryEntry} records for valid rows.
     * On any download or parse failure, logs a warning and returns an empty stream
     * so that one corrupt data file does not halt processing of the remaining files.
     */
    private Stream<InventoryEntry> downloadAndParseSafely(String dataKey) {
        byte[] gz = client.downloadBytes(dataKey);
        if (gz == null) {
            log.warn("PMC S3 inventory data file missing: {}", dataKey);
            return Stream.empty();
        }
        try {
            return parseGzippedCsv(gz);
        } catch (IOException e) {
            log.warn("Failed to parse PMC S3 inventory data file {}: {}", dataKey, e.getMessage());
            return Stream.empty();
        }
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
        if (entries == null) {
            return List.of();
        }
        if (entries.isEmpty()) {
            return entries;
        }
        List<InventoryEntry> deduped = entries.stream()
                .collect(Collectors.toMap(
                        InventoryEntry::pmcId,
                        Function.identity(),
                        (a, b) -> b.version() > a.version() ? b : a,
                        LinkedHashMap::new))
                .values().stream()
                .toList();
        int before = entries.size();
        int after = deduped.size();
        if (before != after) {
            log.info("Deduped inventory from {} entries to {} unique pmcIds", before, after);
        }
        return deduped;
    }

    /**
     * Parses the JSON manifest's {@code "files"} array and returns the {@code "key"}
     * value of each file object. On any parse failure, logs a warning and returns
     * an empty list (preserving the silent-failure behavior of the prior hand-scraper).
     */
    private List<String> extractDataFileKeys(String manifestJson) {
        try {
            JsonNode root = objectMapper.readTree(manifestJson);
            return StreamSupport.stream(root.path("files").spliterator(), false)
                    .map(node -> node.path("key").asText())
                    .filter(key -> !key.isEmpty())
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to parse PMC S3 inventory manifest JSON: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Decompresses and parses a gzipped S3 Inventory CSV file.
     *
     * <p>The S3 Inventory format has no header row. Columns are fixed:
     * index 0 = bucket name, index 1 = object key. Both fields are quoted.
     * Rows whose key does not match the expected {@code metadata/PMC{id}.{v}.json}
     * pattern are silently skipped by {@link InventoryEntry#fromS3Key}.
     */
    private static Stream<InventoryEntry> parseGzippedCsv(byte[] gz) throws IOException {
        try (InputStream decompressed = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            return CSV_MAPPER
                    .readerFor(Map.class)
                    .with(INVENTORY_CSV_SCHEMA)
                    .<Map<String, String>>readValues(decompressed)
                    .readAll()
                    .stream()
                    .map(row -> row.get("key"))
                    .filter(Objects::nonNull)
                    .map(InventoryEntry::fromS3Key)
                    .filter(Objects::nonNull);
        }
    }
}
