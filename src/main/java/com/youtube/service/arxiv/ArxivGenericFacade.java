package com.youtube.service.arxiv;

import com.youtube.external.rest.arxiv.dto.ArxivRecord;
import com.youtube.external.rest.arxiv.dto.ArxivPaperDocument;
import com.youtube.jpa.dao.arxiv.ArxivTracker;
import com.youtube.jpa.repository.ArxivPaperDocumentRepository;
import com.youtube.service.grobid.GrobidService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

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
            Set<String> processedArxivIds = new java.util.HashSet<>(
                    arxivInternalService.findArxivIdsProcessedInPeriod(
                            arxivTracker.getDateStart().atStartOfDay().atOffset(ZoneOffset.UTC),
                            arxivTracker.getDateEnd().atStartOfDay().atOffset(ZoneOffset.UTC)
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

            for (ArxivRecord r : unprocessed) {
                processOne(arxivTracker, r);
            }

            log.info("Resumed: processed {} remaining records (out of {} total) for period",
                    unprocessed.size(), recs.size());
            return;
        }

        List<ArxivRecord> recs = arxivOaiService.getArxivPapersMetadata(
                arxivTracker.getDateStart().toString(),
                arxivTracker.getDateEnd().toString());

        arxivTracker.setAllPapersForPeriod(recs.size());

        for (ArxivRecord r : recs) {
            r.setArxivId(ArxivRecord.extractArxivIdFromOai(r.getOaiIdentifier()));
            if (r.getArxivId() == null) continue;
            processOne(arxivTracker, r);
        }
        log.info("Processed {} records with GROBID", recs.size());
    }

    private void processOne(ArxivTracker arxivTracker, ArxivRecord r) {
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
            arxivTracker.setProcessedPapersForPeriod(arxivTracker.getProcessedPapersForPeriod() + 1);
            arxivInternalService.persistArxivState(arxivTracker, r);
        }
    }
}
