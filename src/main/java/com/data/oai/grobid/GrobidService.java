package com.data.oai.grobid;

import com.data.oai.grobid.tei.GrobidTeiMapperJsoup;
import com.data.oai.shared.dto.PaperDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GrobidService {

    private final GrobidClient grobidClient;

    public PaperDocument processGrobidDocument(String sourceId, String oaiIdentifier, byte[] pdfBytes) {
        long t0 = System.nanoTime();
        String xmlString = grobidClient.processPdfToXmlString(sourceId, pdfBytes);
        long t1 = System.nanoTime();
        PaperDocument doc = GrobidTeiMapperJsoup.toPaperDocument(sourceId, oaiIdentifier, xmlString);
        long t2 = System.nanoTime();

        log.info("GROBID {} ms | Mapping {} ms | total {} ms | id={}",
                (t1 - t0) / 1_000_000,
                (t2 - t1) / 1_000_000,
                (t2 - t0) / 1_000_000,
                oaiIdentifier);
        return doc;
    }
}
