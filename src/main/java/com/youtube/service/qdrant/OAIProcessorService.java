package com.youtube.service.qdrant;

import com.youtube.external.rest.arxiv.ArxivOaiSimpleClient;
import com.youtube.external.rest.arxiv.dto.ArxivRecord;
import com.youtube.external.rest.arxiv.dto.PaperDocument;
import com.youtube.external.rest.grobid.GrobidClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAIProcessorService implements Job {

    private final ArxivOaiSimpleClient client;
    private final GrobidClient grobid;

    @Override
    public void execute(JobExecution execution) {
        List<ArxivRecord> recs = client.listRecords("2026-02-01", "2026-02-02");

        for (ArxivRecord r : recs) {
            r.setArxivId(extractArxivIdFromOai(r.getOaiIdentifier()));
            try {
                byte[] pdf = client.getPdf(r.getArxivId());
                if (pdf == null || pdf.length == 0) {
                    log.warn("Empty PDF for {}", r.getArxivId());
                    continue;
                }

                String tei = grobid.processFulltext(pdf, r.getArxivId() + ".pdf");
                PaperDocument doc = GrobidTeiMapperJsoup.toPaperDocument(r.getArxivId(), r.getOaiIdentifier(), tei);
                log.info("Collected document for paper with id {}", r.getOaiIdentifier());
                r.setDocument(doc);

            } catch (Exception e) {
                log.warn("Failed to process arXivId={} with GROBID", r.getArxivId(), e);
            }
        }

        log.info("Processed {} records with GROBID", recs.size());
    }

    private static String extractArxivIdFromOai(String oaiIdentifier) {
        if (oaiIdentifier == null) return null;
        int idx = oaiIdentifier.lastIndexOf(':');
        String id = (idx >= 0) ? oaiIdentifier.substring(idx + 1) : oaiIdentifier;
        return id.replaceAll("v\\d+$", "");
    }

    @Override
    public String getName() {
        return "OAIProcessorService Job";
    }
}
