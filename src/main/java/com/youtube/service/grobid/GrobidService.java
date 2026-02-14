package com.youtube.service.grobid;

import com.youtube.config.GrobidProperties;
import com.youtube.arxiv.oai.dto.ArxivPaperDocument;
import com.youtube.external.rest.grobid.GrobidClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class GrobidService {

    private final GrobidProperties props;
    private final GrobidClient grobidClient;

    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    public ArxivPaperDocument processGrobidDocument(String arxivId, String oaiIdentifier, byte[] pdfBytes) {
        long t0 = System.nanoTime();
        String xmlString = grobidClient.processPdfToXmlString(arxivId, pdfBytes, props.baseUrl(), props.fulltextEndpoint());
        long t1 = System.nanoTime();
        ArxivPaperDocument doc = GrobidTeiMapperJsoup.toArxivPaperDocument(arxivId, oaiIdentifier, xmlString);
        long t2 = System.nanoTime();

        log.info("GROBID {} ms | Mapping {} ms | total {} ms | id={}",
                (t1 - t0) / 1_000_000,
                (t2 - t1) / 1_000_000,
                (t2 - t0) / 1_000_000,
                oaiIdentifier);
        return doc;
    }
}
