package com.youtube.arxiv.oai;

import com.youtube.arxiv.oai.tracker.ArxivTracker;
import com.youtube.external.rest.arxiv.dto.ArxivRecord;
import com.youtube.external.rest.arxiv.dto.ArxivPaperDocument;
import com.youtube.service.grobid.GrobidService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArxivGenericFacade {
    private final ArxivOaiService arxivOaiService;
    private final GrobidService grobidService;
    private final ArxivInternalService arxivInternalService;
    private final ExecutorService grobidPool = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r);
                t.setName("grobid-worker-" + t.getId());
                t.setDaemon(true);
                return t;
            });
    public ArxivTracker getArxivTracker() {
        return arxivInternalService.getArchiveTracker();
    }

    public void processCollectedArxivRecord(ArxivTracker arxivTracker) {
        if (!arxivTracker.getAllPapersForPeriod().equals(arxivTracker.getProcessedPapersForPeriod())) {
            Set<String> processedArxivIds = new java.util.HashSet<>(
                    arxivInternalService.findArxivIdsProcessedInPeriod(
                            arxivTracker.getDateStart(),
                            arxivTracker.getDateEnd()
                    )
            );
            List<ArxivRecord> recs = arxivOaiService.getArxivPapersMetadata(
                    arxivTracker.getDateStart().toString(),
                    arxivTracker.getDateEnd().toString()
            );
            arxivTracker.setAllPapersForPeriod(recs.size());
            List<ArxivRecord> unprocessed = recs.stream()
                    .peek(r -> r.setArxivId(ArxivRecord.extractArxivIdFromOai(r.getOaiIdentifier())))
                    .filter(r -> r.getArxivId() != null)
                    .filter(r -> !processedArxivIds.contains(r.getArxivId()))
                    .toList();

            AtomicInteger processed = new AtomicInteger(arxivTracker.getProcessedPapersForPeriod());

            List<CompletableFuture<Void>> futures = unprocessed.stream()
                    .map(r -> CompletableFuture.runAsync(() -> processOne(arxivTracker, r, processed), grobidPool))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            log.info("Resumed: processed {} remaining records (out of {} total) for period",
                    unprocessed.size(), recs.size());
            return;
        }

        List<ArxivRecord> recs = arxivOaiService.getArxivPapersMetadata(
                arxivTracker.getDateStart().toString(),
                arxivTracker.getDateEnd().toString());

        arxivTracker.setAllPapersForPeriod(recs.size());

        AtomicInteger processed = new AtomicInteger(arxivTracker.getProcessedPapersForPeriod());

        List<CompletableFuture<Void>> futures = recs.stream()
                .map(r -> CompletableFuture.runAsync(() -> processOne(arxivTracker, r, processed), grobidPool))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Processed {} records with GROBID", recs.size());
    }

    private void processOne(ArxivTracker arxivTracker, ArxivRecord r, AtomicInteger processed) {
        String arxivId = r.getArxivId();

        try {
            byte[] pdf = arxivOaiService.getPdf(arxivId);
            if (pdf == null || pdf.length == 0) {
                log.warn("Empty PDF for {}", arxivId);
                return;
            }

            ArxivPaperDocument doc = grobidService.processGrobidDocument(arxivId, r.getOaiIdentifier(), pdf);
            r.setDocument(doc);

        } catch (Exception e) {
            log.warn("Failed to process arXivId={} with GROBID", arxivId, e);
        } finally {
            int newVal = processed.incrementAndGet();
            arxivTracker.setProcessedPapersForPeriod(newVal);
            arxivInternalService.persistArxivState(arxivTracker, r);
        }
    }
}
