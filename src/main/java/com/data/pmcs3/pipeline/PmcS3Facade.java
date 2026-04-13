package com.data.pmcs3.pipeline;

import com.data.oai.persistence.PaperInternalService;
import com.data.oai.shared.dto.Author;
import com.data.oai.shared.dto.PaperDocument;
import com.data.oai.shared.dto.Record;
import com.data.pmcs3.client.PmcS3Client;
import com.data.pmcs3.inventory.InventoryEntry;
import com.data.pmcs3.inventory.InventoryService;
import com.data.pmcs3.jats.JatsParser;
import com.data.pmcs3.metadata.PubmedArticleMetadata;
import com.data.pmcs3.metadata.MetadataService;
import com.data.config.properties.PmcS3Properties;
import com.data.pmcs3.persistence.PmcS3TrackerService;
import com.data.pmcs3.persistence.entity.PmcS3Tracker;
import com.data.shared.DataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates the PMC S3 processing flow for a single batch (one inventory
 * manifest): discover → filter → download → parse → persist.
 *
 * <p>Per-record failures are isolated — any exception while processing a
 * single article is caught, logged, and the batch continues. Progress is
 * persisted atomically via {@link PmcS3TrackerService} so restarts resume.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmcS3Facade {

    private static final String DEFAULT_LANGUAGE = "en";

    private final PmcS3Client client;
    private final InventoryService inventoryService;
    private final MetadataService metadataService;
    private final PmcS3TrackerService trackerService;
    private final PaperInternalService paperInternalService;
    private final PmcS3Properties props;

    @Resource(name = "pmcS3Executor")
    private ExecutorService pmcS3Executor;

    private Semaphore inFlight;

    @PostConstruct
    void init() {
        this.inFlight = new Semaphore(Math.max(1, props.concurrency()));
    }

    /**
     * Processes a single inventory manifest end-to-end. The manifest key
     * doubles as the tracker batch id so a restart can resume cleanly.
     */
    public void processBatch(String manifestKey) {
        PmcS3Tracker tracker = trackerService.getOrCreate(manifestKey);
        try {
            List<InventoryEntry> entries = inventoryService.fetchInventory(manifestKey);
            trackerService.updateDiscovered(tracker.getId(), entries.size());

            Set<String> alreadyProcessed = new HashSet<>(
                    paperInternalService.findAllSourceIdsByDataSource(DataSource.PMC_S3)
            );

            List<InventoryEntry> unprocessed = entries.stream()
                    .filter(e -> !alreadyProcessed.contains(e.pmcId()))
                    .toList();

            log.info("PMC S3 batch={} discovered={} alreadyProcessed={} unprocessed={}",
                    manifestKey, entries.size(), alreadyProcessed.size(), unprocessed.size());

            int batchSize = Math.max(1, props.batchSize());
            int total = unprocessed.size();
            int chunkCount = (total + batchSize - 1) / batchSize;
            AtomicInteger processed = new AtomicInteger(0);

            for (int chunkIdx = 0, from = 0; from < total; chunkIdx++, from += batchSize) {
                if (Thread.currentThread().isInterrupted()) {
                    log.warn("PMC S3 batch={} interrupted after {} chunks", manifestKey, chunkIdx);
                    break;
                }
                int to = Math.min(from + batchSize, total);
                List<InventoryEntry> chunk = unprocessed.subList(from, to);
                log.info("PMC S3 batch={} chunk={}/{} size={}",
                        manifestKey, chunkIdx + 1, chunkCount, chunk.size());

                CompletableFuture<?>[] futures = new CompletableFuture[chunk.size()];
                for (int i = 0; i < chunk.size(); i++) {
                    InventoryEntry entry = chunk.get(i);
                    futures[i] = CompletableFuture.runAsync(
                            () -> processOne(tracker, entry, processed), pmcS3Executor);
                }
                CompletableFuture.allOf(futures).join();
            }

            logSkipBreakdown(tracker, manifestKey);
            trackerService.markCompleted(tracker.getId());
            log.info("PMC S3 batch={} completed. processed={}", manifestKey, processed.get());
        } catch (Exception e) {
            log.error("PMC S3 batch={} failed: {}", manifestKey, e.getMessage(), e);
            trackerService.markFailed(tracker.getId());
            throw e;
        }
    }

    private void processOne(PmcS3Tracker tracker, InventoryEntry entry, AtomicInteger processed) {
        String pmcId = entry.pmcId();
        try {
            inFlight.acquire();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            trackerService.incrementSkippedInterrupted(tracker.getId());
            return;
        }
        try {
            processOneInternal(tracker, entry, processed, pmcId);
        } finally {
            inFlight.release();
        }
    }

    private void processOneInternal(PmcS3Tracker tracker, InventoryEntry entry, AtomicInteger processed, String pmcId) {
        try {
            PubmedArticleMetadata metadata = metadataService.fetchMetadata(entry);
            if (metadata == null) {
                log.debug("Skipping PMC {} — no JSON metadata", pmcId);
                trackerService.incrementSkippedMissingMetadata(tracker.getId());
                return;
            }

            if (!PmcS3LicenseFilter.isAcceptable(metadata.licenseCode())) {
                log.debug("Skipping PMC {} — license {} not commercially usable",
                        pmcId, metadata.licenseCode());
                trackerService.incrementSkippedLicense(tracker.getId());
                return;
            }

            // Author manuscripts frequently have no JATS URL advertised in
            // metadata — short-circuit here to avoid a wasted S3 GET and to
            // keep the missing-JATS counter accurate.
            if (metadata.xmlUrl() == null || metadata.xmlUrl().isBlank()) {
                log.debug("Skipping PMC {} — metadata has no xml_url (likely author manuscript)", pmcId);
                trackerService.incrementSkippedMissingJats(tracker.getId());
                return;
            }

            String jatsKey = client.articleKey(pmcId, entry.version(), "xml");
            String jatsXml = client.downloadText(jatsKey);
            if (jatsXml == null || jatsXml.isBlank()) {
                log.debug("Skipping PMC {} — no JATS XML", pmcId);
                trackerService.incrementSkippedMissingJats(tracker.getId());
                return;
            }

            String txtKey = client.articleKey(pmcId, entry.version(), "txt");
            String rawContent = client.downloadText(txtKey);

            PaperDocument paperDoc = JatsParser.parse(pmcId, metadata.pmid(), jatsXml);
            // Re-assemble with rawContent that the facade downloaded separately.
            paperDoc = withRawContent(paperDoc, rawContent);

            String language = JatsParser.extractLanguage(jatsXml);
            if (language == null || language.isBlank()) language = DEFAULT_LANGUAGE;

            String pdfUrl = metadata.pdfUrl() != null
                    ? metadata.pdfUrl()
                    : client.urlFor(client.articleKey(pmcId, entry.version(), "pdf"));

            Record record = buildRecord(metadata, pmcId, jatsXml, language);

            paperInternalService.persistState(DataSource.PMC_S3, record, paperDoc, pdfUrl);
            trackerService.incrementProcessed(tracker.getId());

            int newVal = processed.incrementAndGet();
            if (newVal % 50 == 0) {
                log.info("PMC S3 processed {} records so far (batch={})",
                        newVal, tracker.getBatchId());
            }
        } catch (DataIntegrityViolationException e) {
            log.warn("PMC S3 skipping pmcId={} (duplicate): {}", pmcId, e.getMessage());
            trackerService.incrementSkippedDuplicate(tracker.getId());
        } catch (Exception e) {
            log.warn("PMC S3 failed to process pmcId={}: {}", pmcId, e.getMessage());
            trackerService.incrementSkippedIo(tracker.getId());
        }
    }

    /**
     * Emits an aggregate INFO-level breakdown of skip counters for the batch.
     * Per-article license rejections are logged at DEBUG (there can be
     * thousands of them), so this aggregate is the operational signal for
     * quality monitoring.
     *
     * <p>The in-memory {@code tracker} reference carries the entity snapshot
     * from batch start; counters are incremented atomically at the DB level,
     * so we re-fetch here to get the current values before logging.
     */
    private void logSkipBreakdown(PmcS3Tracker tracker, String manifestKey) {
        PmcS3Tracker fresh = trackerService.findById(tracker.getId()).orElse(tracker);
        log.info("PMC S3 batch={} skip breakdown: license={} missingMeta={} missingJats={} duplicate={} io={} interrupted={}",
                manifestKey,
                fresh.getSkippedLicense(),
                fresh.getSkippedMissingMetadata(),
                fresh.getSkippedMissingJats(),
                fresh.getSkippedDuplicate(),
                fresh.getSkippedIo(),
                fresh.getSkippedInterrupted());
    }

    /**
     * Builds a {@link Record} DTO from the PMC S3 JSON metadata and JATS XML.
     * The JATS document is the authoritative source for authors and DOI;
     * the JSON metadata provides license, PMID, and scheduling info.
     */
    private static Record buildRecord(PubmedArticleMetadata metadata, String pmcId, String jatsXml, String language) {
        Record record = new Record();
        record.setSourceId(pmcId);
        record.setExternalIdentifier(metadata.pmid());
        record.setDatestamp(metadata.publicationDate());
        record.setLicense(licenseUrlOrCode(metadata));
        record.setLanguage(language);
        record.setTitle(metadata.articleTitle());
        record.setJournalRef(metadata.journalTitle());

        String doi = metadata.doi();
        if (doi == null || doi.isBlank()) {
            doi = JatsParser.extractDoi(jatsXml);
        }
        record.setDoi(doi);

        List<Author> authors = JatsParser.extractAuthors(jatsXml);
        record.getAuthors().addAll(authors);

        return record;
    }

    private static String licenseUrlOrCode(PubmedArticleMetadata metadata) {
        if (metadata.licenseUrl() != null && !metadata.licenseUrl().isBlank()) {
            return metadata.licenseUrl();
        }
        return metadata.licenseCode();
    }

    private static PaperDocument withRawContent(PaperDocument doc, String rawContent) {
        if (rawContent == null) return doc;
        return new PaperDocument(
                doc.sourceId(),
                doc.sourceIdentifier(),
                doc.title(),
                doc.abstractText(),
                doc.sections(),
                doc.sourceXml(),
                rawContent,
                doc.keywords(),
                doc.affiliation(),
                doc.classCodes(),
                doc.fundingList(),
                doc.references(),
                doc.docType()
        );
    }
}
