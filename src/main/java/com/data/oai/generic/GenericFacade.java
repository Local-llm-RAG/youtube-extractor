package com.data.oai.generic;

import com.data.config.properties.EmbeddingProperties;
import com.data.embedding.RagSystemRestApiService;
import com.data.embedding.dto.EmbeddingDto;
import com.data.external.rest.pythonclient.dto.EmbedTranscriptRequest;
import com.data.external.rest.pythonclient.dto.EmbeddingTask;
import com.data.oai.DataSource;
import com.data.oai.PaperInternalService;
import com.data.oai.generic.common.tracker.Tracker;
import com.data.oai.generic.common.dto.Record;
import com.data.oai.generic.common.dto.PaperDocument;
import com.data.grobid.GrobidService;
import com.optimaize.langdetect.i18n.LdLocale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenericFacade {

    private final OaiSourceRegistry sourceRegistry;
    private final GrobidService grobidService;
    private final PaperInternalService paperInternalService;
    private final RagSystemRestApiService ragService;
    private final EmbeddingProperties embeddingProperties;
    private final LanguageDetector languageDetector;

    private final ExecutorService grobidPool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setName("grobid-worker-" + t.getId());
        t.setDaemon(true);
        return t;
    });

    public Tracker getTracker(LocalDate startDate, DataSource dataSource) {
        return paperInternalService.getTracker(startDate, dataSource);
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
        AtomicInteger processed = new AtomicInteger(tracker.getProcessedPapersForPeriod());

        List<CompletableFuture<Void>> futures = unprocessed.stream()
                .map(r -> CompletableFuture.runAsync(() -> processOne(handler, tracker, r, processed), grobidPool))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Processed {} records with GROBID", unprocessed.size());
    }

    private void processOne(OaiSourceHandler handler, Tracker tracker, Record apiRecord, AtomicInteger processed) {
        String sourceId = apiRecord.getSourceId();

        try {
            AbstractMap.SimpleEntry<String, byte[]> urlWithContent = handler.fetchPdfAndEnrich(apiRecord);
            byte[] pdfContent = urlWithContent.getValue();
            if (pdfContent == null || pdfContent.length == 0) {
                throw new RuntimeException("Empty or no pdf content found for %s".formatted(sourceId));
            }

            PaperDocument grobidDoc = grobidService.processGrobidDocument(sourceId, apiRecord.getOaiIdentifier(), pdfContent);
            apiRecord.setLanguage(detectLang(grobidDoc.title() + " " + grobidDoc.abstractText(), sourceId));
            EmbeddingDto embeddingInfo = ragService.getEmbeddingsForText(buildEmbedTranscriptRequest(grobidDoc.rawContent()));
//            EmbeddingDto embeddingInfo = EmbeddingDto.builder().build();
            paperInternalService.persistState(
                    tracker.getDataSource(),
                    apiRecord,
                    grobidDoc,
                    embeddingInfo,
                    urlWithContent.getKey());
        } catch (Exception e) {
            log.warn("Failed to process arXivId={} with GROBID", sourceId, e);
        } finally {
            int newVal = processed.incrementAndGet();
            tracker.setProcessedPapersForPeriod(newVal);
            paperInternalService.persistTracker(tracker);
        }
    }

    private String detectLang(String text, String sourceId) {
        try {
            LanguageResult result = languageDetector.detect(text);
            if (result.isUnknown()) {
                log.warn("Unknown language for sourceId={}", sourceId);
                return "en";
            }
            return result.getLanguage();
        } catch (Exception e) {
            log.error("Language detection failed for sourceId={}", sourceId, e);
            return "en";
        }
    }

    private EmbedTranscriptRequest buildEmbedTranscriptRequest(String transcriptText) {
        return EmbedTranscriptRequest.builder()
                .text(transcriptText)
                .task(EmbeddingTask.RETRIEVAL_PASSAGE.getValue())
                .chunkTokens(embeddingProperties.chunkSize())
                .chunkOverlap(embeddingProperties.overlap())
                .normalize(embeddingProperties.normalize())
                .build();
    }
}