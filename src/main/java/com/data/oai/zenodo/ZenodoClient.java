package com.data.oai.zenodo;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URL;

@Service
public class ZenodoClient {

    private static final int MAX_RETRIES = 5;
    private static final long BASE_DELAY_MS = 1000L;

    private final RestClient rest;

    public ZenodoClient(@Qualifier("zenodoRestClient") RestClient rest) {
        this.rest = rest;
    }

    public byte[] listRecords(String baseUrl, String from, String until, String token, String metadataPrefix) {
        URI uri = buildListRecordsUri(baseUrl, from, until, token, metadataPrefix);

        int attempt = 0;
        while (true) {
            attempt++;

            try {
                byte[] body = rest.get()
                        .uri(uri)
                        .exchange((req, res) -> {
                            HttpStatusCode status = res.getStatusCode();

                            // 2xx = normal success
                            // 422 = expected OAI noRecordsMatch case, keep XML body
                            if (status.is2xxSuccessful() || status.value() == 422) {
                                return res.bodyTo(byte[].class);
                            }

                            // Retryable statuses
                            if (status.value() == 429 || status.value() == 502 || status.value() == 503 || status.value() == 504) {
                                throw new RetryableZenodoException(
                                        "Retryable Zenodo OAI failure. HTTP " + status.value() + " for " + uri
                                );
                            }

                            throw new IllegalStateException(
                                    "Zenodo OAI call failed. HTTP " + status.value() + " for " + uri
                            );
                        });

                throttleBetweenCalls();
                return body;

            } catch (RetryableZenodoException ex) {
                if (attempt >= MAX_RETRIES) {
                    throw new IllegalStateException(
                            "Zenodo OAI call failed after " + attempt + " attempts for " + uri,
                            ex
                    );
                }

                sleepWithBackoff(attempt);
            }
        }
    }

    public ZenodoRecord getRecord(String recordId) {
        URI uri = URI.create("https://zenodo.org/api/records/" + recordId);
        return rest.get()
                .uri(uri)
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status.is2xxSuccessful()) {
                        return res.bodyTo(ZenodoRecord.class);
                    }
                    throw new IllegalStateException(
                            "Zenodo getRecord failed. HTTP " + status.value() + " for recordId " + recordId
                    );
                });
    }

    public byte[] downloadFile(String url) {
        // java.net.URL parses leniently (handles spaces); the multi-arg URI constructor
        // then re-encodes each component, so spaces become %20 without double-encoding
        // any characters that are already percent-encoded.
        URI uri = toUri(url);

        return rest.get()
                .uri(uri)
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status.is2xxSuccessful()) {
                        return res.bodyTo(byte[].class);
                    }
                    throw new IllegalStateException(
                            "Zenodo file download failed. HTTP " + status.value() + " for " + url
                    );
                });
    }

    private static URI toUri(String url) {
        try {
            URL parsed = new URL(url);
            return new URI(parsed.getProtocol(), parsed.getUserInfo(), parsed.getHost(),
                           parsed.getPort(), parsed.getPath(), parsed.getQuery(), null);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Zenodo file URL: " + url, e);
        }
    }

    private URI buildListRecordsUri(String baseUrl, String from, String until, String token, String metadataPrefix) {
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

        return b.build(true).toUri();
    }

    private void throttleBetweenCalls() {
        sleepMillis(1000L);
    }

    private void sleepWithBackoff(int attempt) {
        long delay = BASE_DELAY_MS * (1L << Math.min(attempt - 1, 4)); // 1s, 2s, 4s, 8s, 16s
        sleepMillis(delay);
    }

    private void sleepMillis(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrupted while waiting to call Zenodo", e);
        }
    }

    private static class RetryableZenodoException extends RuntimeException {
        public RetryableZenodoException(String message) {
            super(message);
        }
    }
}