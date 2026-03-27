package com.data.oai.arxiv;

import com.data.oai.RetryableApiException;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Service
public class ArxivClient {

    private final RestClient rest;

    public ArxivClient(@Qualifier("grobidRestClient") RestClient rest) {
        this.rest = rest;
    }

    @Retry(name = "arxiv")
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
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status.is2xxSuccessful()) {
                        return res.bodyTo(byte[].class);
                    }
                    throwIfRetryable(status, uri.toString());
                    throw new IllegalStateException(
                            "ArXiv OAI call failed. HTTP " + status.value() + " for " + uri);
                });
    }

    @Retry(name = "arxiv")
    public byte[] getPdf(String pdfUrl) {
        String decoded = UriUtils.decode(pdfUrl, StandardCharsets.UTF_8);
        URI uri = UriComponentsBuilder.fromUriString(decoded).encode().build().toUri();

        return rest.get()
                .uri(uri)
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status.is2xxSuccessful()) {
                        return res.bodyTo(byte[].class);
                    }
                    throwIfRetryable(status, pdfUrl);
                    throw new IllegalStateException(
                            "ArXiv PDF download failed. HTTP " + status.value() + " for " + pdfUrl);
                });
    }

    @Retry(name = "arxiv")
    public byte[] getEText(String arxivId) {
        URI uri = URI.create("https://arxiv.org/e-print/" + arxivId);

        return rest.get()
                .uri(uri)
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status.is2xxSuccessful()) {
                        return res.bodyTo(byte[].class);
                    }
                    throwIfRetryable(status, arxivId);
                    throw new IllegalStateException(
                            "ArXiv e-print download failed. HTTP " + status.value() + " for " + arxivId);
                });
    }

    private static void throwIfRetryable(HttpStatusCode status, String context) {
        int code = status.value();
        if (code == 429 || code == 502 || code == 503 || code == 504) {
            throw new RetryableApiException("Retryable ArXiv failure. HTTP " + code + " for " + context);
        }
    }
}
