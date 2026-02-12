package com.youtube.service.arxiv;

import com.youtube.external.rest.arxiv.dto.ArxivRecord;
import com.youtube.external.rest.arxiv.dto.ArxivPaperDocument;
import com.youtube.jpa.dao.ArxivTracker;
import com.youtube.service.grobid.GrobidService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArxivGenericFacade {
    private final ArxivOaiService arxivOaiService;
    private final GrobidService grobidService;
    private final ArxivInternalService arxivInternalService;

    public ArxivTracker getArxivTracker() {
        return arxivInternalService.getArchiveTracker();
    }

    public void processCollectedArxivRecord(ArxivTracker arxivTracker) {
        if (!arxivTracker.getAllPapersForPeriod().equals(arxivTracker.getProcessedPapersForPeriod())) {
            List<Object> arxivRecordsInDatabase = List.of(); //TODO get papers that are processed
            List<ArxivRecord> recs = arxivOaiService.getArxivPapersMetadata(arxivTracker.getDateStart().toString(), arxivTracker.getDateEnd().toString());
            //TODO write logic to remove already processed and continue to non processed
            return;
        }
        List<ArxivRecord> recs = arxivOaiService.getArxivPapersMetadata(arxivTracker.getDateStart().toString(), arxivTracker.getDateEnd().toString());
        arxivTracker.setAllPapersForPeriod(recs.size());
        for (ArxivRecord r : recs) {
            String arxivId = ArxivRecord.extractArxivIdFromOai(r.getOaiIdentifier());
            r.setArxivId(arxivId);
            try {
                byte[] pdf = arxivOaiService.getPdf(arxivId);
                if (pdf == null || pdf.length == 0) {
                    log.warn("Empty PDF for {}", arxivId);
                    continue;
                }

                ArxivPaperDocument doc = grobidService.processGrobidDocument(arxivId, r.getOaiIdentifier(), pdf);
                r.setDocument(doc);
                //TODO persist record in database
            } catch (Exception e) {
                log.warn("Failed to process arXivId={} with GROBID", arxivId, e);
            }
            arxivTracker.setProcessedPapersForPeriod(arxivTracker.getProcessedPapersForPeriod() + 1);
            arxivInternalService.saveTracker(arxivTracker);
        }

        log.info("Processed {} records with GROBID", recs.size());
    }
}
