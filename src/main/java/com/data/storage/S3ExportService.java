package com.data.storage;

import com.data.config.properties.StorageProperties;
import com.data.config.properties.StorageProperties.ExportMode;
import com.data.oai.persistence.entity.*;
import com.data.oai.persistence.repository.EmbedTranscriptChunkRepository;
import com.data.oai.persistence.repository.RecordRepository;
import com.data.oai.pipeline.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exports research paper records to S3 as JSONL files.
 * Supports FULL (all records) and INCREMENTAL (watermark-based) modes.
 * Uses S3 multipart upload with paginated DB reads to stay memory-safe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3ExportService {

    private static final int PAGE_SIZE = 500;
    private static final long MIN_PART_SIZE = 5 * 1024 * 1024; // 5 MB — S3 multipart minimum
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final StorageProperties storageProperties;
    private final RecordRepository recordRepository;
    private final EmbedTranscriptChunkRepository chunkRepository;
    private final ExportWatermarkRepository watermarkRepository;

    private ObjectMapper objectMapper;
    private S3Client s3Client;

    @PostConstruct
    void init() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        if (storageProperties.enabled() && storageProperties.s3() != null) {
            s3Client = S3Client.builder()
                    .region(Region.of(storageProperties.s3().region()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                    storageProperties.s3().accessKey(),
                                    storageProperties.s3().secretKey())))
                    .build();
            log.info("S3ExportService initialized: bucket={}, keyPrefix={}, mode={}",
                    storageProperties.s3().bucketName(),
                    storageProperties.s3().keyPrefix(),
                    storageProperties.exportMode());
        } else {
            log.info("S3ExportService disabled (storage.enabled=false)");
        }
    }

    /**
     * Exports records for the given data source to S3 using the configured export mode.
     * No size limit or date range filtering applied.
     */
    @Transactional(readOnly = true)
    public S3ExportResult exportToS3(DataSource dataSource, ExportMode mode,
                                      LocalDate from, LocalDate to, Long maxSizeBytes) {
        if (!storageProperties.enabled()) {
            log.debug("S3 export skipped — storage is disabled");
            return new S3ExportResult(dataSource, 0, null, false);
        }

        OffsetDateTime watermark = resolveWatermark(dataSource, mode);
        String s3Key = buildS3Key(dataSource);

        log.info("[{}] Starting {} S3 export → s3://{}/{} (maxSize={}, from={}, to={})",
                dataSource, mode, storageProperties.s3().bucketName(), s3Key,
                maxSizeBytes != null ? maxSizeBytes : "unlimited", from, to);

        String uploadId = initiateMultipartUpload(s3Key);
        List<CompletedPart> completedParts = new ArrayList<>();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int partNumber = 1;
        int totalRecords = 0;
        long totalBytes = 0;
        int page = 0;
        boolean sizeLimitReached = false;

        try {
            while (true) {
                List<Long> ids = fetchRecordIdPage(dataSource, watermark, mode, from, to, page);
                if (ids.isEmpty()) {
                    log.info("[{}] Page {} — no more records, export loop complete", dataSource, page);
                    break;
                }

                log.info("[{}] Page {} — fetched {} record IDs, loading documents...", dataSource, page, ids.size());
                List<RecordEntity> records = recordRepository.findByIdsWithDocument(ids);

                // Collect all section IDs for this page, then fetch chunk projections in one query
                // (excludes the embedding float array to avoid loading ~1.95 GB into memory)
                List<Long> sectionIds = records.stream()
                        .filter(r -> r.getDocument() != null)
                        .flatMap(r -> r.getDocument().getSections().stream())
                        .map(SectionEntity::getId)
                        .toList();
                log.info("[{}] Page {} — {} sections found, fetching chunk projections...", dataSource, page, sectionIds.size());

                Map<Long, List<EmbedChunkExportProjection>> chunksBySectionId = chunkRepository
                        .findExportProjectionsBySectionIds(sectionIds)
                        .stream()
                        .collect(Collectors.groupingBy(EmbedChunkExportProjection::sectionId));
                log.info("[{}] Page {} — {} sections have chunks", dataSource, page, chunksBySectionId.size());

                for (RecordEntity record : records) {
                    PaperExportDto dto = mapToDto(record, chunksBySectionId);
                    byte[] jsonLine = serializeJsonLine(dto);
                    buffer.write(jsonLine);
                    totalBytes += jsonLine.length;
                    totalRecords++;
                }
                log.info("[{}] Page {} — serialized {} records, running total: {} records / {} MB",
                        dataSource, page, records.size(), totalRecords, totalBytes / (1024 * 1024));

                // Check size limit after serializing the page
                if (maxSizeBytes != null && totalBytes > maxSizeBytes) {
                    sizeLimitReached = true;
                    log.warn("[{}] Size limit reached: {} MB > {} MB — stopping export after page {}",
                            dataSource, totalBytes / (1024 * 1024), maxSizeBytes / (1024 * 1024), page);
                }

                // Flush buffer as a multipart part when it exceeds the 5 MB minimum
                if (buffer.size() >= MIN_PART_SIZE) {
                    log.info("[{}] Flushing buffer as S3 part {} ({} MB)...", dataSource, partNumber, buffer.size() / (1024 * 1024));
                    completedParts.add(uploadPart(s3Key, uploadId, partNumber, buffer.toByteArray()));
                    partNumber++;
                    buffer.reset();
                }

                if (sizeLimitReached) break;

                page++;
            }

            // Upload any remaining data in the buffer
            if (buffer.size() > 0) {
                if (completedParts.isEmpty()) {
                    // Total data is under 5 MB — use a simple PUT instead of multipart
                    log.info("[{}] Data under 5 MB — using simple PUT upload ({} KB)", dataSource, buffer.size() / 1024);
                    abortMultipartUpload(s3Key, uploadId);
                    putObject(s3Key, buffer.toByteArray());
                } else {
                    log.info("[{}] Uploading final buffer as part {} ({} KB)", dataSource, partNumber, buffer.size() / 1024);
                    completedParts.add(uploadPart(s3Key, uploadId, partNumber, buffer.toByteArray()));
                    log.info("[{}] Completing multipart upload ({} parts total)...", dataSource, completedParts.size());
                    completeMultipartUpload(s3Key, uploadId, completedParts);
                }
            } else if (!completedParts.isEmpty()) {
                log.info("[{}] Completing multipart upload ({} parts total)...", dataSource, completedParts.size());
                completeMultipartUpload(s3Key, uploadId, completedParts);
            } else {
                abortMultipartUpload(s3Key, uploadId);
                log.warn("[{}] No records matched the export criteria — nothing uploaded (mode={}, from={}, to={})",
                        dataSource, mode, from, to);
                return new S3ExportResult(dataSource, 0, null, false);
            }

            log.info("[{}] Export finished — {} records, {} MB → s3://{}/{} (sizeLimitReached={})",
                    dataSource, totalRecords, totalBytes / (1024 * 1024),
                    storageProperties.s3().bucketName(), s3Key, sizeLimitReached);

            return new S3ExportResult(dataSource, totalRecords, s3Key, sizeLimitReached);

        } catch (Exception e) {
            log.error("[{}] Export failed on page {} after {} records — aborting multipart upload. Error: {}",
                    dataSource, page, totalRecords, e.getMessage(), e);
            abortMultipartUpload(s3Key, uploadId);
            throw new RuntimeException("S3 export failed for " + dataSource, e);
        }
    }


    private OffsetDateTime resolveWatermark(DataSource dataSource, ExportMode mode) {
        if (mode == ExportMode.FULL) {
            log.info("[{}] FULL mode — watermark ignored, exporting by date range", dataSource);
            return null;
        }
        OffsetDateTime watermark = watermarkRepository.findById(dataSource)
                .map(ExportWatermarkEntity::getExportedAt)
                .orElse(OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        log.info("[{}] INCREMENTAL mode — watermark resolved to {} (exporting records ingested after this)", dataSource, watermark);
        return watermark;
    }

    private List<Long> fetchRecordIdPage(DataSource dataSource, OffsetDateTime watermark,
                                          ExportMode mode, LocalDate from, LocalDate to, int page) {
        PageRequest pageRequest = PageRequest.of(page, PAGE_SIZE);
        if (mode == ExportMode.FULL) {
            if (from != null && to != null) {
                return recordRepository.findIdsByDataSourceAndDatestampBetween(dataSource, from, to, pageRequest);
            }
            return recordRepository.findIdsByDataSource(dataSource, pageRequest);
        }
        return recordRepository.findIdsByDataSourceAndCreatedAfter(dataSource, watermark, pageRequest);
    }

    private PaperExportDto mapToDto(RecordEntity record, Map<Long, List<EmbedChunkExportProjection>> chunksBySectionId) {
        PaperDocumentEntity doc = record.getDocument();

        List<PaperExportDto.AuthorDto> authors = record.getAuthors().stream()
                .sorted(Comparator.comparingInt(a -> a.getPos() != null ? a.getPos() : 0))
                .map(a -> new PaperExportDto.AuthorDto(
                        a.getPos() != null ? a.getPos() : 0,
                        a.getFirstName(),
                        a.getLastName()))
                .toList();

        String category = record.getCategories().isEmpty() ? null : record.getCategories().getFirst();

        List<PaperExportDto.SectionDto> sections = doc == null ? List.of()
                : doc.getSections().stream()
                .sorted(Comparator.comparingInt(s -> s.getPos() != null ? s.getPos() : 0))
                .map(s -> mapSection(s, chunksBySectionId.getOrDefault(s.getId(), List.of())))
                .toList();

        List<PaperExportDto.ReferenceDto> references = doc == null ? List.of()
                : doc.getReferences().stream()
                .sorted(Comparator.comparingInt(ReferenceMentionEntity::getRefIndex))
                .map(ref -> new PaperExportDto.ReferenceDto(
                        ref.getRefIndex(),
                        ref.getTitle(),
                        ref.getDoi(),
                        ref.getYear(),
                        ref.getVenue(),
                        ref.getAuthors() != null ? ref.getAuthors() : List.of(),
                        ref.getUrls() != null ? ref.getUrls() : List.of(),
                        ref.getIdnos() != null ? ref.getIdnos() : List.of()))
                .toList();

        return new PaperExportDto(
                record.getSourceId(),
                record.getOaiIdentifier(),
                record.getDoi(),
                record.getLicense(),
                record.getPdfUrl(),
                record.getLanguage(),
                record.getDataSource().name(),
                record.getDatestamp(),
                record.getComments(),
                record.getJournalRef(),
                doc != null ? doc.getTitle() : null,
                doc != null ? doc.getAbstractText() : null,
                doc != null ? doc.getRawContent() : null,
                doc != null ? doc.getDocType() : null,
                doc != null && doc.getKeywords() != null ? doc.getKeywords() : List.of(),
                doc != null && doc.getAffiliations() != null ? doc.getAffiliations() : List.of(),
                doc != null && doc.getClassCodes() != null ? doc.getClassCodes() : List.of(),
                authors,
                category,
                sections,
                references
        );
    }

    private PaperExportDto.SectionDto mapSection(SectionEntity section, List<EmbedChunkExportProjection> chunks) {
        List<PaperExportDto.EmbeddingChunkDto> chunkDtos = chunks.stream()
                .map(c -> new PaperExportDto.EmbeddingChunkDto(
                        c.chunkIndex(),
                        c.chunkText(),
                        c.embeddingModel(),
                        c.dim(),
                        c.task(),
                        c.chunkTokens(),
                        c.chunkOverlap(),
                        c.spanStart(),
                        c.spanEnd()))
                .toList();

        return new PaperExportDto.SectionDto(
                section.getPos() != null ? section.getPos() : 0,
                section.getLevel(),
                section.getTitle(),
                section.getText(),
                chunkDtos
        );
    }

    private byte[] serializeJsonLine(PaperExportDto dto) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(dto);
            byte[] line = new byte[json.length + 1];
            System.arraycopy(json, 0, line, 0, json.length);
            line[json.length] = '\n';
            return line;
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize PaperExportDto to JSON", e);
        }
    }

    private String buildS3Key(DataSource dataSource) {
        String prefix = storageProperties.s3().keyPrefix();
        String date = LocalDate.now(ZoneOffset.UTC).toString();
        String timestamp = OffsetDateTime.now(ZoneOffset.UTC).format(TIMESTAMP_FMT);
        return "%s/%s/%s/dump_%s.jsonl".formatted(prefix, dataSource.name(), date, timestamp);
    }

    // --- S3 multipart upload operations ---

    private String initiateMultipartUpload(String key) {
        log.info("Initiating S3 multipart upload for key={}", key);
        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(
                CreateMultipartUploadRequest.builder()
                        .bucket(storageProperties.s3().bucketName())
                        .key(key)
                        .contentType("application/x-ndjson")
                        .build());
        log.info("Multipart upload initiated — uploadId={}", response.uploadId());
        return response.uploadId();
    }

    private CompletedPart uploadPart(String key, String uploadId, int partNumber, byte[] data) {
        UploadPartResponse response = s3Client.uploadPart(
                UploadPartRequest.builder()
                        .bucket(storageProperties.s3().bucketName())
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .build(),
                RequestBody.fromBytes(data));

        log.info("Uploaded S3 part {} — {} KB for key={}", partNumber, data.length / 1024, key);
        return CompletedPart.builder()
                .partNumber(partNumber)
                .eTag(response.eTag())
                .build();
    }

    private void completeMultipartUpload(String key, String uploadId, List<CompletedPart> parts) {
        s3Client.completeMultipartUpload(
                CompleteMultipartUploadRequest.builder()
                        .bucket(storageProperties.s3().bucketName())
                        .key(key)
                        .uploadId(uploadId)
                        .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                        .build());
        log.info("Multipart upload completed — key={}, parts={}", key, parts.size());
    }

    private void abortMultipartUpload(String key, String uploadId) {
        try {
            s3Client.abortMultipartUpload(
                    AbortMultipartUploadRequest.builder()
                            .bucket(storageProperties.s3().bucketName())
                            .key(key)
                            .uploadId(uploadId)
                            .build());
            log.info("Multipart upload aborted — key={}", key);
        } catch (Exception e) {
            log.warn("Failed to abort multipart upload for key={}: {}", key, e.getMessage());
        }
    }

    private void putObject(String key, byte[] data) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(storageProperties.s3().bucketName())
                        .key(key)
                        .contentType("application/x-ndjson")
                        .build(),
                RequestBody.fromBytes(data));
    }

    // --- Watermark management ---

    /**
     * Advances the export watermark to the given timestamp.
     * The caller should pass the timestamp captured at the start of the export,
     * not the current time, to avoid skipping records ingested during the export window.
     *
     * @param dataSource      the data source whose watermark to advance
     * @param exportStartedAt the timestamp captured at the start of the export run
     */
    @Transactional
    public void advanceWatermark(DataSource dataSource, OffsetDateTime exportStartedAt) {
        ExportWatermarkEntity entity = watermarkRepository.findById(dataSource)
                .map(existing -> {
                    existing.setExportedAt(exportStartedAt);
                    return existing;
                })
                .orElse(ExportWatermarkEntity.builder()
                        .dataSource(dataSource)
                        .exportedAt(exportStartedAt)
                        .build());
        watermarkRepository.save(entity);
        log.info("[{}] Export watermark advanced to {}", dataSource, exportStartedAt);
    }
}
