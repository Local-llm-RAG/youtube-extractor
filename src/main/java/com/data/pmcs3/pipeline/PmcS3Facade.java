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
import com.data.pmcs3.persistence.SkipReason;
import com.data.pmcs3.persistence.entity.PmcS3Tracker;
import com.data.shared.DataSource;
import com.data.shared.i18n.LanguageConstants;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
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

    private static final String DEFAULT_LANGUAGE = LanguageConstants.DEFAULT_LANGUAGE;

    private final PmcS3Client client;
    private final InventoryService inventoryService;
    private final MetadataService metadataService;
    private final PmcS3TrackerService trackerService;
    private final PaperInternalService paperInternalService;
    private final PmcS3Properties props;

    @Resource(name = "pmcS3Executor")
    private ExecutorService pmcS3Executor;

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
        processOneInternal(tracker, entry, processed, entry.pmcId());
    }

    private void processOneInternal(PmcS3Tracker tracker, InventoryEntry entry, AtomicInteger processed, String pmcId) {
        try {
            PubmedArticleMetadata metadata = loadAndValidateMetadata(entry, tracker, pmcId);
            if (metadata == null) return;

            AssetBundle assets = downloadArticleAssets(entry, metadata, tracker, pmcId);
            if (assets == null) return;

            PaperDocument paperDoc = buildPaperDocument(entry, metadata, assets, pmcId);
            persistRecord(entry, metadata, assets, paperDoc, tracker, processed, pmcId);
        } catch (DataIntegrityViolationException e) {
            log.warn("PMC S3 skipping pmcId={} (duplicate): {}", pmcId, e.getMessage());
            trackerService.incrementSkipped(tracker.getId(), SkipReason.DUPLICATE);
        } catch (Exception e) {
            log.warn("PMC S3 failed to process pmcId={}: {}", pmcId, e.getMessage());
            trackerService.incrementSkipped(tracker.getId(), SkipReason.IO);
        }
    }

    /**
     * Fetches and validates per-article JSON metadata. Returns {@code null}
     * (and increments the appropriate skip counter) if the article cannot be
     * processed.
     */
    private PubmedArticleMetadata loadAndValidateMetadata(InventoryEntry entry, PmcS3Tracker tracker, String pmcId) {
        PubmedArticleMetadata metadata = metadataService.fetchMetadata(entry);
        if (metadata == null) {
            log.debug("Skipping PMC {} — no JSON metadata", pmcId);
            trackerService.incrementSkipped(tracker.getId(), SkipReason.MISSING_METADATA);
            return null;
        }

        if (!PmcS3LicenseFilter.isAcceptable(metadata.licenseCode())) {
            log.debug("Skipping PMC {} — license {} not commercially usable",
                    pmcId, metadata.licenseCode());
            trackerService.incrementSkipped(tracker.getId(), SkipReason.LICENSE);
            return null;
        }

        // Author manuscripts frequently have no JATS URL advertised in
        // metadata — short-circuit here to avoid a wasted S3 GET and to
        // keep the missing-JATS counter accurate.
        if (metadata.xmlUrl() == null || metadata.xmlUrl().isBlank()) {
            log.debug("Skipping PMC {} — metadata has no xml_url (likely author manuscript)", pmcId);
            trackerService.incrementSkipped(tracker.getId(), SkipReason.MISSING_JATS);
            return null;
        }

        return metadata;
    }

    /**
     * Downloads the JATS XML and plain-text content for the article. Returns
     * {@code null} (and increments the appropriate skip counter) if the JATS
     * XML is absent.
     */
    private AssetBundle downloadArticleAssets(InventoryEntry entry, PubmedArticleMetadata metadata,
                                              PmcS3Tracker tracker, String pmcId) {
        String jatsKey = client.articleKey(pmcId, entry.version(), "xml");
        String jatsXml = client.downloadText(jatsKey);
        if (jatsXml == null || jatsXml.isBlank()) {
            log.debug("Skipping PMC {} — no JATS XML", pmcId);
            trackerService.incrementSkipped(tracker.getId(), SkipReason.MISSING_JATS);
            return null;
        }

        String txtKey = client.articleKey(pmcId, entry.version(), "txt");
        String rawContent = client.downloadText(txtKey);

        String pdfUrl = metadata.pdfUrl() != null
                ? metadata.pdfUrl()
                : client.urlFor(client.articleKey(pmcId, entry.version(), "pdf"));

        return new AssetBundle(jatsXml, rawContent, pdfUrl);
    }

    /**
     * Parses the JATS XML into a {@link PaperDocument}, merging in the
     * separately downloaded plain-text raw content.
     */
    private PaperDocument buildPaperDocument(InventoryEntry entry, PubmedArticleMetadata metadata,
                                             AssetBundle assets, String pmcId) {
        PaperDocument paperDoc = JatsParser.parse(pmcId, metadata.pmid(), assets.jatsXml());
        // Re-assemble with rawContent that the facade downloaded separately.
        return paperDoc.withRawContent(assets.rawContent());
    }

    /**
     * Persists the article, updates the tracker, and logs progress milestones.
     */
    private void persistRecord(InventoryEntry entry, PubmedArticleMetadata metadata,
                               AssetBundle assets, PaperDocument paperDoc,
                               PmcS3Tracker tracker, AtomicInteger processed, String pmcId) {
        String language = JatsParser.extractLanguage(assets.jatsXml());
        if (language == null || language.isBlank()) language = DEFAULT_LANGUAGE;

        Record record = buildRecord(metadata, pmcId, assets.jatsXml(), language);

        paperInternalService.persistState(DataSource.PMC_S3, record, paperDoc, assets.pdfUrl());
        trackerService.incrementProcessed(tracker.getId());

        int newVal = processed.incrementAndGet();
        if (newVal % 50 == 0) {
            log.info("PMC S3 processed {} records so far (batch={})",
                    newVal, tracker.getBatchId());
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
     *
     * <p>Datestamp priority (first non-null wins):
     * <ol>
     *   <li>JATS {@code <pub-date pub-type="epub">} — the electronic publication date,
     *       which is the actual shipping date for PMC articles.</li>
     *   <li>JATS {@code <pub-date pub-type="ppub">} — print publication date.</li>
     *   <li>JSON metadata {@code publication_date} — PMC's summary field, typically
     *       derived from the same JATS pub-dates but not guaranteed.</li>
     *   <li>{@code null} — persistence layer lets {@code source_record.created_at::date}
     *       stand in for datestamp queries via a backfill migration. A WARN is
     *       emitted so null-datestamp drift is visible in the logs.</li>
     * </ol>
     * S3 object {@code LastModified} is deliberately not queried here: it would
     * require an extra HEAD per article and, on PMC, is virtually always dominated
     * by the JATS pub-dates being present.
     */
    private Record buildRecord(PubmedArticleMetadata metadata, String pmcId, String jatsXml, String language) {
        Record record = new Record();
        record.setSourceId(pmcId);
        record.setExternalIdentifier(metadata.pmid());
        record.setDatestamp(resolveDatestamp(metadata, jatsXml, pmcId));
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
        return Optional.ofNullable(metadata.licenseUrl())
                .filter(s -> !s.isBlank())
                .orElse(metadata.licenseCode());
    }

    /**
     * Resolves the {@code source_record.datestamp} value for a PMC S3 article
     * by trying JATS pub-dates first (the authoritative publisher-supplied
     * dates), then the JSON metadata's summary field, and finally giving up
     * with a WARN log so operators see null-datestamp drift.
     *
     * <p>See {@link #buildRecord} for the full priority rationale.
     */
    private String resolveDatestamp(PubmedArticleMetadata metadata, String jatsXml, String pmcId) {
        String fromJats = JatsParser.extractPublicationDate(jatsXml);
        if (fromJats != null) return fromJats;

        String fromMetadata = metadata.publicationDate();
        if (fromMetadata != null && !fromMetadata.isBlank()) return fromMetadata;

        log.warn("PMC S3 pmcId={} produced null datestamp — no JATS <pub-date> and no metadata.publication_date; " +
                "downstream queries will rely on created_at.", pmcId);
        return null;
    }

    /**
     * Bundles the downloaded S3 assets for a single article so they can be
     * passed between the download and parse/persist phases without additional
     * fields on the facade itself.
     */
    private record AssetBundle(String jatsXml, String rawContent, String pdfUrl) {}

}
