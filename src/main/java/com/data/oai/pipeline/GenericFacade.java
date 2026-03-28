package com.data.oai.pipeline;

import com.data.config.properties.EmbeddingProperties;
import com.data.rag.client.RagSystemRestApiService;
import com.data.rag.dto.EmbedTranscriptRequest;
import com.data.oai.persistence.PaperInternalService;
import com.data.oai.persistence.TrackerService;
import com.data.shared.exception.PdfDownloadException;
import com.data.oai.persistence.entity.Tracker;
import com.data.oai.shared.dto.PdfContent;
import com.data.oai.shared.dto.Record;
import com.data.oai.shared.dto.PaperDocument;
import com.data.oai.grobid.GrobidService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenericFacade {

    private static final String DEFAULT_LANGUAGE = "en";

    private final OaiSourceRegistry sourceRegistry;
    private final GrobidService grobidService;
    private final PaperInternalService paperInternalService;
    private final TrackerService trackerService;
    private final RagSystemRestApiService ragService;
    private final EmbeddingProperties embeddingProperties;
    private final LanguageDetector languageDetector;

    @Resource(name = "grobidExecutor")
    private ExecutorService grobidPool;

    public Tracker getTracker(LocalDate startDate, DataSource dataSource) {
        return trackerService.getTracker(startDate, dataSource);
    }

    public void processCollectedArxivRecord(Tracker tracker) {
        processCollectedArxivRecord(tracker, null);
    }

    public void processCollectedArxivRecord(Tracker tracker, Set<String> onlyArxivIds) {
        OaiSourceHandler handler = sourceRegistry.get(tracker.getDataSource());

        Set<String> processedPaperIds = new java.util.HashSet<>(
                paperInternalService.findArxivIdsProcessedInPeriod(
                        tracker.getDateStart(),
                        tracker.getDateEnd(),
                        tracker.getDataSource()
                )
        );

        List<Record> allRecords = handler.fetchMetadata(
                tracker.getDateStart(),
                tracker.getDateEnd());

        tracker.setAllPapersForPeriod(allRecords.size());

        List<Record> unprocessed = allRecords.stream()
                .peek(r -> r.setSourceId(Record.extractIdFromOai(r.getOaiIdentifier())))
                .filter(r -> nonNull(r.getSourceId()))
                .filter(r -> !processedPaperIds.contains(r.getSourceId()))
                .filter(r -> isNull(onlyArxivIds) || onlyArxivIds.contains(r.getSourceId()))
                .toList();
        tracker.setProcessedPapersForPeriod(allRecords.size() - unprocessed.size());

        // Persist initial tracker state (allPapersForPeriod, initial processedCount) from the main thread
        trackerService.persistTracker(tracker);

        AtomicInteger processed = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = unprocessed.stream()
                .map(r -> CompletableFuture.runAsync(
                        () -> processOne(handler, tracker, r, processed), grobidPool))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Processed {} records with GROBID", processed.get());
    }

    private void processOne(OaiSourceHandler handler, Tracker tracker, Record apiRecord, AtomicInteger processed) {
        String sourceId = apiRecord.getSourceId();

        try {
            PdfContent pdfResult = handler.fetchPdfAndEnrich(apiRecord);
            if (pdfResult == null) {
                return; // no PDF available — already logged by handler
            }
            byte[] pdfBytes = pdfResult.content();
            if (pdfBytes == null || pdfBytes.length == 0) {
                throw new PdfDownloadException("Empty or no pdf content found for %s".formatted(sourceId));
            }

            PaperDocument grobidDoc = grobidService.processGrobidDocument(sourceId, apiRecord.getOaiIdentifier(), pdfBytes);
            apiRecord.setLanguage(detectLang(grobidDoc.title() + " " + grobidDoc.abstractText(), sourceId));
            grobidDoc.sections().forEach(
                    section ->
                            section.setEmbeddings(ragService.getEmbeddingsForText(EmbedTranscriptRequest.forPassage(section.getText(), embeddingProperties)))
            );
            paperInternalService.persistState(
                    tracker.getDataSource(),
                    apiRecord,
                    grobidDoc,
                    pdfResult.url());
        } catch (Exception e) {
            log.warn("Failed to process sourceId={} with GROBID", sourceId, e);
        } finally {
            // Atomically increment at DB level — thread-safe, no JPA entity mutation from pool threads
            trackerService.incrementProcessed(tracker.getId());
            processed.incrementAndGet();
        }
    }

    private String detectLang(String text, String sourceId) {
        try {
            LanguageResult result;
            synchronized (languageDetector) {
                result = languageDetector.detect(text);
            }
            if (result.isUnknown()) {
                log.warn("Unknown language for sourceId={}", sourceId);
                return DEFAULT_LANGUAGE;
            }
            return result.getLanguage();
        } catch (Exception e) {
            log.error("Language detection failed for sourceId={}", sourceId, e);
            return DEFAULT_LANGUAGE;
        }
    }

}
