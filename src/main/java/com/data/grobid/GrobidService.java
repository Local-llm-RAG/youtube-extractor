package com.data.grobid;

import com.data.config.properties.GrobidProperties;
import com.data.oai.generic.common.dto.PaperDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GrobidService {

    private final GrobidProperties props;
    private final GrobidClient grobidClient;

    public PaperDocument processGrobidDocument(String arxivId, String oaiIdentifier, byte[] pdfBytes) {
        long t0 = System.nanoTime();
        String xmlString = grobidClient.processPdfToXmlString(arxivId, pdfBytes, props.baseUrl(), props.fulltextEndpoint());
        long t1 = System.nanoTime();
        PaperDocument doc = GrobidTeiMapperJsoup.toArxivPaperDocument(arxivId, oaiIdentifier, xmlString);
        long t2 = System.nanoTime();

        log.info("GROBID {} ms | Mapping {} ms | total {} ms | id={}",
                (t1 - t0) / 1_000_000,
                (t2 - t1) / 1_000_000,
                (t2 - t0) / 1_000_000,
                oaiIdentifier);
        return doc;
    }
}
