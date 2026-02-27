package com.data.grobid;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

@Service
public class GrobidClient {
    private final RestClient rest;

    public GrobidClient(@Qualifier("grobidRestClient") RestClient rest) {
        this.rest = rest;
    }

    public String processPdfToXmlString(String arxivId, byte[] pdfBytes, String baseUrl, String endpoint) {
        MultipartBodyBuilder mb = buildGrobidRequest(pdfBytes, String.format("%s.pdf", arxivId));

        int maxAttempts = 3;
        long backoffMs = 250;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return rest.post()
                        .uri(baseUrl + endpoint)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(mb.build())
                        .retrieve()
                        .body(String.class);
            } catch (HttpStatusCodeException ex) {
                 if (attempt == maxAttempts) {
                    throw ex;
                }

                try {
                    Thread.sleep(backoffMs * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();

                    throw new RuntimeException(ie);
                }
            }
        }

        throw new IllegalStateException("Not reachable");
    }

    private static MultipartBodyBuilder buildGrobidRequest(byte[] pdfBytes, String filename) {
        MultipartBodyBuilder mb = new MultipartBodyBuilder();
        mb.part("input", pdfBytes)
                .filename(filename)
                .contentType(MediaType.APPLICATION_PDF);

        mb.part("consolidateHeader", "1");
        mb.part("consolidateCitations", "1");
        mb.part("segmentSentences", "1");
        mb.part("includeRawCitations", "1");
        mb.part("includeRawAffiliations", "1");
        return mb;
    }
}
