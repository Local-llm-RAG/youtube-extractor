package com.data.oai;

import com.data.oai.arxiv.ArxivOaiService;
import com.data.oai.common.tracker.Tracker;
import com.data.oai.common.dto.Record;
import com.data.oai.common.dto.PaperDocument;
import com.data.grobid.GrobidService;
import com.data.oai.zenodo.ZenodoOaiService;
import com.data.oai.zenodo.ZenodoRecord;
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
public class GenericFacade {
    private final ArxivOaiService arxivOaiService;
    private final ZenodoOaiService zenodoOaiService;
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
        Set<String> processedArxivIds = new java.util.HashSet<>(
                paperInternalService.findArxivIdsProcessedInPeriod(
                        tracker.getDateStart(),
                        tracker.getDateEnd(),
                        tracker.getDataSource()
                )
        );

        List<Record> allRecords = new ArrayList<>();
        if(tracker.getDataSource() == DataSource.ARXIV){

            allRecords = arxivOaiService.getArxivPapersMetadata(
                    tracker.getDateStart().atStartOfDay().toString(),
                    tracker.getDateEnd().toString()
            );
        }
        else if (tracker.getDataSource() == DataSource.ZENODO){
            allRecords = zenodoOaiService.getZenodoPapersMetadata(
                    tracker.getDateEnd().atStartOfDay().plusHours(1).toString(),
                    tracker.getDateEnd().atStartOfDay().plusHours(2).toString()
            );
        }

        tracker.setAllPapersForPeriod(allRecords.size());
        List<Record> unprocessed = allRecords.stream()
                .peek(r -> r.setArxivId(Record.extractArxivIdFromOai(r.getOaiIdentifier())))
                .filter(r -> r.getArxivId() != null)
                .filter(r -> !processedArxivIds.contains(r.getArxivId()))
                .toList();

        AtomicInteger processed = new AtomicInteger(tracker.getProcessedPapersForPeriod());

        List<CompletableFuture<Void>> futures = unprocessed.stream()
                .map(r -> CompletableFuture.runAsync(() -> processOne(tracker, r, processed), grobidPool))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Processed {} records with GROBID", unprocessed.size());
    }

    private void processOne(Tracker tracker, Record r, AtomicInteger processed) {
        String arxivId = r.getArxivId();

        try {
            byte[] pdf = null;
            if(tracker.getDataSource() == DataSource.ARXIV){
                pdf = arxivOaiService.getPdf(arxivId);
            } else if (tracker.getDataSource() == DataSource.ZENODO){
                var map = zenodoOaiService.getPdf(arxivId);
                ZenodoRecord record = map.getKey();
                r.setLanguage(record.getMetadata().getLanguage()); // TODO: apache tika or etc for language detection if null
                pdf = map.getValue();
            }
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
