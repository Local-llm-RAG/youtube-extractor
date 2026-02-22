package com.data.oai.generic;

import com.data.config.EmbeddingProperties;
import com.data.embedding.RagSystemRestApiService;
import com.data.embedding.dto.EmbeddingDto;
import com.data.external.rest.pythonclient.RagSystemRestApiClient;
import com.data.external.rest.pythonclient.dto.EmbedTranscriptRequest;
import com.data.external.rest.pythonclient.dto.EmbeddingTask;
import com.data.oai.DataSource;
import com.data.oai.PaperInternalService;
import com.data.oai.generic.common.tracker.Tracker;
import com.data.oai.generic.common.dto.Record;
import com.data.oai.generic.common.dto.PaperDocument;
import com.data.grobid.GrobidService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.AbstractMap;
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

    private final OaiSourceRegistry sourceRegistry;
    private final GrobidService grobidService;
    private final PaperInternalService paperInternalService;
    private final RagSystemRestApiService ragService;
    private final EmbeddingProperties embeddingProperties;

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

        Set<String> processedArxivIds = new java.util.HashSet<>(
                paperInternalService.findArxivIdsProcessedInPeriod(
                        tracker.getDateStart(),
                        tracker.getDateEnd(),
                        tracker.getDataSource()
                )
        );

        List<Record> allRecords = handler.fetchMetadata(
                tracker.getDateStart().atStartOfDay(),
                tracker.getDateEnd().atStartOfDay());

        tracker.setAllPapersForPeriod(allRecords.size());

        List<Record> unprocessed = allRecords.stream()
                .peek(r -> r.setArxivId(Record.extractIdFromOai(r.getOaiIdentifier())))
                .filter(r -> nonNull(r.getArxivId()))
                .filter(r -> !processedArxivIds.contains(r.getArxivId()))
                .filter(r -> isNull(onlyArxivIds) || onlyArxivIds.contains(r.getArxivId()))
                .toList();

        AtomicInteger processed = new AtomicInteger(tracker.getProcessedPapersForPeriod());

        List<CompletableFuture<Void>> futures = unprocessed.stream()
                .map(r -> CompletableFuture.runAsync(() -> processOne(handler, tracker, r, processed), grobidPool))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Processed {} records with GROBID", unprocessed.size());
    }

    private void processOne(OaiSourceHandler handler, Tracker tracker, Record apiRecord, AtomicInteger processed) {
        String arxivId = apiRecord.getArxivId();

        EmbeddingDto embeddingInfo = null;
        AbstractMap.SimpleEntry<String, byte[]> urlWithContent = handler.fetchPdfAndEnrich(apiRecord);
        try {
            byte[] pdfContent = urlWithContent.getValue();
            if (pdfContent == null || pdfContent.length == 0) {
                log.warn("Empty PDF for {}", arxivId);
                return;
            }

            PaperDocument doc = grobidService.processGrobidDocument(arxivId, apiRecord.getOaiIdentifier(), pdfContent);
            embeddingInfo = ragService.getEmbeddingsForText(buildEmbedTranscriptRequest(doc.rawContent()));
            apiRecord.setDocument(doc);

        } catch (Exception e) {
            log.warn("Failed to process arXivId={} with GROBID", arxivId, e);
        } finally {
            int newVal = processed.incrementAndGet();
            tracker.setProcessedPapersForPeriod(newVal);
            paperInternalService.persistState(tracker, apiRecord, embeddingInfo, urlWithContent.getKey());
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