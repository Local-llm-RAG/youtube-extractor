package com.youtube.external.rest.grobid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class GrobidClient {
    private final RestClient rest;

    public String processPdfToXmlString(String arxivId, byte[] pdfBytes, String baseUrl, String endpoint) {
        MultipartBodyBuilder mb = buildGrobidRequest(pdfBytes, String.format("%s.pdf", arxivId));
        return rest.post()
                .uri(baseUrl + endpoint)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(mb.build())
                .retrieve()
                .body(String.class);
    }

    private static MultipartBodyBuilder buildGrobidRequest(byte[] pdfBytes, String filename) {
        MultipartBodyBuilder mb = new MultipartBodyBuilder();
        mb.part("input", pdfBytes)
                .filename(filename)
                .contentType(MediaType.APPLICATION_PDF);

        mb.part("consolidateHeader", "1");
        mb.part("consolidateCitations", "1");
        mb.part("segmentSentences", "1");
        return mb;
    }
}
