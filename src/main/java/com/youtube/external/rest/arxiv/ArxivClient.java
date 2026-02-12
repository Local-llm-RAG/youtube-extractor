package com.youtube.external.rest.arxiv;

import com.youtube.config.ArxivOaiProps;
import com.youtube.external.rest.arxiv.dto.ArxivAuthor;
import com.youtube.external.rest.arxiv.dto.ArxivRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ArxivClient {
    private final RestClient rest;

    public byte[] listRecords(String baseUrl, String from, String until, String token) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromUriString(baseUrl)
                .queryParam("verb", "ListRecords");

        if (token == null) {
            b.queryParam("metadataPrefix", "arXiv")
                    .queryParam("from", from)
                    .queryParam("until", until);
        } else {
            b.queryParam("resumptionToken", token);
        }

        URI uri = b.build(true).toUri();

        return rest.get()
                .uri(uri)
                .retrieve()
                .body(byte[].class);
    }

    public byte[] getPdf(String arxivId) {
        URI pdfUri = URI.create("https://arxiv.org/pdf/" + arxivId + ".pdf");
        return rest.get().uri(pdfUri).retrieve().body(byte[].class);
    }

    public byte[] getEText(String arxivId) {
        URI srcUri = URI.create("https://arxiv.org/e-print/" + arxivId);
        return rest.get().uri(srcUri).retrieve().body(byte[].class);
    }
}
