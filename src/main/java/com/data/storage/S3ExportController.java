package com.data.storage;

import com.data.config.properties.StorageProperties;
import com.data.config.properties.StorageProperties.ExportMode;
import com.data.shared.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * REST controller for manually triggering S3 exports of research paper data.
 */
@Slf4j
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class S3ExportController {

    private static final Map<String, Long> ALLOWED_SIZES = Map.ofEntries(
            Map.entry("200mb", 200L * 1024 * 1024),
            Map.entry("500mb", 500L * 1024 * 1024),
            Map.entry("1gb", 1L * 1024 * 1024 * 1024),
            Map.entry("2gb", 2L * 1024 * 1024 * 1024),
            Map.entry("5gb", 5L * 1024 * 1024 * 1024),
            Map.entry("10gb", 10L * 1024 * 1024 * 1024),
            Map.entry("50gb", 50L * 1024 * 1024 * 1024),
            Map.entry("100gb", 100L * 1024 * 1024 * 1024)
    );

    private final S3ExportService s3ExportService;
    private final StorageProperties storageProperties;

    /**
     * Exports all data sources (ARXIV, ZENODO, PUBMED, PUBMED_S3) sequentially to S3.
     * Advances watermark per source after each successful INCREMENTAL export.
     *
     * @param mode    optional export mode override (FULL or INCREMENTAL); defaults to configured value
     * @param from    optional start date filter on datestamp (FULL mode only, ISO format yyyy-MM-dd)
     * @param to      optional end date filter on datestamp (FULL mode only, ISO format yyyy-MM-dd)
     * @param maxSize optional size limit (e.g. "200mb", "1gb", "5gb"); if absent, no limit
     * @return list of export results, one per data source
     */
    @PostMapping
    public List<S3ExportResult> exportAll(@RequestParam(required = false) ExportMode mode,
                                           @RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to,
                                           @RequestParam(required = false) String maxSize) {
        ExportMode effectiveMode = resolveMode(mode);
        Long maxSizeBytes = parseMaxSize(maxSize);

        log.info("Manual S3 export triggered for all sources (mode={}, from={}, to={}, maxSize={})",
                effectiveMode, from, to, maxSize != null ? maxSize : "unlimited");

        return Arrays.stream(DataSource.values())
                .map(source -> exportSingleSource(source, effectiveMode, from, to, maxSizeBytes))
                .toList();
    }

    /**
     * Exports a single data source to S3 and advances its watermark on INCREMENTAL exports.
     *
     * @param source  the data source to export (e.g. ARXIV, ZENODO, PUBMED)
     * @param mode    optional export mode override (FULL or INCREMENTAL); defaults to configured value
     * @param from    optional start date filter on datestamp (FULL mode only, ISO format yyyy-MM-dd)
     * @param to      optional end date filter on datestamp (FULL mode only, ISO format yyyy-MM-dd)
     * @param maxSize optional size limit (e.g. "200mb", "1gb", "5gb"); if absent, no limit
     * @return export result with record count, S3 key, and whether size limit was reached
     */
    @PostMapping("/{source}")
    public S3ExportResult exportSource(@PathVariable DataSource source,
                                       @RequestParam(required = false) ExportMode mode,
                                       @RequestParam(required = false) LocalDate from,
                                       @RequestParam(required = false) LocalDate to,
                                       @RequestParam(required = false) String maxSize) {
        ExportMode effectiveMode = resolveMode(mode);
        Long maxSizeBytes = parseMaxSize(maxSize);

        log.info("Manual S3 export triggered for {} (mode={}, from={}, to={}, maxSize={})",
                source, effectiveMode, from, to, maxSize != null ? maxSize : "unlimited");

        return exportSingleSource(source, effectiveMode, from, to, maxSizeBytes);
    }

    private S3ExportResult exportSingleSource(DataSource source, ExportMode mode,
                                               LocalDate from, LocalDate to, Long maxSizeBytes) {
        // Capture start time before the export begins — used for watermark advancement
        OffsetDateTime exportStartedAt = OffsetDateTime.now(ZoneOffset.UTC);

        // Ignore date range params in INCREMENTAL mode
        LocalDate effectiveFrom = (mode == ExportMode.INCREMENTAL) ? null : from;
        LocalDate effectiveTo = (mode == ExportMode.INCREMENTAL) ? null : to;

        S3ExportResult result = s3ExportService.exportToS3(source, mode, effectiveFrom, effectiveTo, maxSizeBytes);

        // Advance watermark for INCREMENTAL exports regardless of size limit or record count
        if (mode == ExportMode.INCREMENTAL && result.recordCount() > 0) {
            s3ExportService.advanceWatermark(source, exportStartedAt);
        }

        return result;
    }

    private ExportMode resolveMode(ExportMode mode) {
        return mode != null ? mode : storageProperties.exportMode();
    }

    /**
     * Parses a human-readable size string into bytes.
     * Accepted values (case-insensitive): 200mb, 500mb, 1gb, 2gb, 5gb, 10gb, 50gb, 100gb.
     *
     * @param maxSize the size string, or null for no limit
     * @return size in bytes, or null if no limit
     * @throws ResponseStatusException if the size string is not a recognized value
     */
    static Long parseMaxSize(String maxSize) {
        if (maxSize == null || maxSize.isBlank()) {
            return null;
        }

        String normalized = maxSize.strip().toLowerCase();
        Long bytes = ALLOWED_SIZES.get(normalized);
        if (bytes != null) {
            return bytes;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid maxSize '%s'. Accepted values: %s".formatted(maxSize, ALLOWED_SIZES.keySet()));
    }
}
