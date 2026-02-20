package com.data.oai.zenodo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Service
@RequiredArgsConstructor
public class ZenodoClient {

    private final RestClient rest;

    public byte[] listRecords(String baseUrl, String from, String until, String token, String metadataPrefix) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromUriString(baseUrl)
                .queryParam("verb", "ListRecords");

        if (token == null) {
            b.queryParam("metadataPrefix", metadataPrefix)
                    .queryParam("from", from)
                    .queryParam("until", until);
        } else {
            b.queryParam("resumptionToken", token);
        }

        URI uri = b.build(true).toUri();

        return rest.get()
                .uri(uri)
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();

                    // Zenodo returns 422 with <error code="noRecordsMatch"> for empty windows
                    // We still want the XML body so our parser can return empty list.
                    if (status.is2xxSuccessful() || status.value() == 422) {
                        return res.bodyTo(byte[].class);
                    }

                    // for other errors, bubble up as RestClient exception
                    throw new IllegalStateException("Zenodo OAI call failed. HTTP " + status.value() + " for " + uri);
                });
    }

    public ZenodoRecord getRecord(String recordId) {
        URI uri = URI.create("https://zenodo.org/api/records/" + recordId);
        return rest.get().uri(uri).retrieve().body(ZenodoRecord.class);
    }

    public byte[] downloadFile(String url) {
        URI uri = UriComponentsBuilder
                .fromUriString(url)
                .build()
                .encode()
                .toUri();

        return rest.get()
                .uri(uri)
                .retrieve()
                .body(byte[].class);
    }
}
