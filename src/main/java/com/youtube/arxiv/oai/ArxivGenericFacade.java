package com.youtube.arxiv.oai;

import com.youtube.arxiv.oai.tracker.ArxivTracker;
import com.youtube.arxiv.oai.dto.ArxivRecord;
import com.youtube.arxiv.oai.dto.ArxivPaperDocument;
import com.youtube.service.grobid.GrobidService;
import com.youtube.zenodo.ZenodoOaiService;
import com.youtube.zenodo.ZenodoRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
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
    private final ZenodoOaiService zenodoOaiService;
    private final GrobidService grobidService;
    private final ArxivInternalService arxivInternalService;
    private final ExecutorService grobidPool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setName("grobid-worker-" + t.getId());
        t.setDaemon(true);
        return t;
    });

    public ArxivTracker getArxivTracker(LocalDate startDate, DataSource dataSource) {
        return arxivInternalService.getArchiveTracker(startDate, dataSource);
    }

    public void processCollectedArxivRecord(ArxivTracker arxivTracker) {
        Set<String> processedArxivIds = new java.util.HashSet<>(
                arxivInternalService.findArxivIdsProcessedInPeriod(
                        arxivTracker.getDateStart(),
                        arxivTracker.getDateEnd(),
                        arxivTracker.getDataSource()
                )
        );

        List<ArxivRecord> allRecords = new ArrayList<>();
        if(arxivTracker.getDataSource() == DataSource.ARXIV){

            allRecords = arxivOaiService.getArxivPapersMetadata(
                    arxivTracker.getDateStart().atStartOfDay().toString(),
                    arxivTracker.getDateEnd().toString()
            );
        }
        else if (arxivTracker.getDataSource() == DataSource.ZENODO){
            allRecords = zenodoOaiService.getZenodoPapersMetadata(
                    arxivTracker.getDateEnd().atStartOfDay().plusHours(1).toString(),
                    arxivTracker.getDateEnd().atStartOfDay().plusHours(2).toString()
            );
        }

        arxivTracker.setAllPapersForPeriod(allRecords.size());
        List<ArxivRecord> unprocessed = allRecords.stream()
                .peek(r -> r.setArxivId(ArxivRecord.extractArxivIdFromOai(r.getOaiIdentifier())))
                .filter(r -> r.getArxivId() != null)
                .filter(r -> !processedArxivIds.contains(r.getArxivId()))
                .toList();

        AtomicInteger processed = new AtomicInteger(arxivTracker.getProcessedPapersForPeriod());

        List<CompletableFuture<Void>> futures = unprocessed.stream()
                .map(r -> CompletableFuture.runAsync(() -> processOne(arxivTracker, r, processed), grobidPool))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Processed {} records with GROBID", unprocessed.size());
    }

    private void processOne(ArxivTracker arxivTracker, ArxivRecord r, AtomicInteger processed) {
        String arxivId = r.getArxivId();

        try {
            byte[] pdf = null;
            if(arxivTracker.getDataSource() == DataSource.ARXIV){
                pdf = arxivOaiService.getPdf(arxivId);
            } else if (arxivTracker.getDataSource() == DataSource.ZENODO){
                var map = zenodoOaiService.getPdf(arxivId);
                ZenodoRecord record = map.getKey();
                r.setLanguage(record.getMetadata().getLanguage()); // TODO: apache tika or etc for language detection if null
                pdf = map.getValue();
            }
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
