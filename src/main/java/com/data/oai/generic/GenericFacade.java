package com.data.oai.generic;

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
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenericFacade {

    private final OaiSourceRegistry sourceRegistry;
    private final GrobidService grobidService;
    private final PaperInternalService paperInternalService;

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
                .filter(r -> r.getArxivId() != null)
                .filter(r -> !processedArxivIds.contains(r.getArxivId()))
                .toList();

        AtomicInteger processed = new AtomicInteger(tracker.getProcessedPapersForPeriod());

        List<CompletableFuture<Void>> futures = unprocessed.stream()
                .map(r -> CompletableFuture.runAsync(() -> processOne(handler, tracker, r, processed), grobidPool))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("Processed {} records with GROBID", unprocessed.size());
    }

    private void processOne(OaiSourceHandler handler, Tracker tracker, Record r, AtomicInteger processed) {
        String arxivId = r.getArxivId();

        try {
            byte[] pdf = handler.fetchPdfAndEnrich(r);

            if (pdf == null || pdf.length == 0) {
                log.warn("Empty PDF for {}", arxivId);
                return;
            }

            PaperDocument doc = grobidService.processGrobidDocument(arxivId, r.getOaiIdentifier(), pdf);
            r.setDocument(doc);

        } catch (Exception e) {
            log.warn("Failed to process arXivId={} with GROBID", arxivId, e);
        } finally {
            int newVal = processed.incrementAndGet();
            tracker.setProcessedPapersForPeriod(newVal);
            paperInternalService.persistState(tracker, r);
        }
    }
}