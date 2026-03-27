package com.data.oai.zenodo;

import com.data.oai.RetryableApiException;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
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
public class ZenodoClient {

    private final RestClient rest;

    public ZenodoClient(@Qualifier("zenodoRestClient") RestClient rest) {
        this.rest = rest;
    }

    @Retry(name = "zenodo")
    @RateLimiter(name = "zenodo")
    public byte[] listRecords(String baseUrl, String from, String until, String token, String metadataPrefix) {
        URI uri = buildListRecordsUri(baseUrl, from, until, token, metadataPrefix);

        return rest.get()
                .uri(uri)
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();

                    if (status.is2xxSuccessful() || status.value() == 422) {
                        return res.bodyTo(byte[].class);
                    }

                    throwIfRetryable(status, uri.toString());
                    throw new IllegalStateException(
                            "Zenodo OAI call failed. HTTP " + status.value() + " for " + uri);
                });
    }

    @Retry(name = "zenodo")
    @RateLimiter(name = "zenodo")
    public ZenodoRecord getRecord(String recordId) {
        URI uri = URI.create("https://zenodo.org/api/records/" + recordId);

        return rest.get()
                .uri(uri)
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();

                    if (status.is2xxSuccessful()) {
                        return res.bodyTo(ZenodoRecord.class);
                    }

                    throwIfRetryable(status, "recordId " + recordId);
                    throw new IllegalStateException(
                            "Zenodo getRecord failed. HTTP " + status.value() + " for recordId " + recordId);
                });
    }

    @Retry(name = "zenodo")
    @RateLimiter(name = "zenodo")
    public byte[] downloadFile(String url) {
        URI uri = toEncodedUri(url);

        return rest.get()
                .uri(uri)
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();

                    if (status.is2xxSuccessful()) {
                        return res.bodyTo(byte[].class);
                    }

                    throwIfRetryable(status, url);
                    throw new IllegalStateException(
                            "Zenodo file download failed. HTTP " + status.value() + " for " + url);
                });
    }

    private static URI toEncodedUri(String url) {
        String decoded = UriUtils.decode(url, StandardCharsets.UTF_8);
        return UriComponentsBuilder.fromUriString(decoded).encode().build().toUri();
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

    private static void throwIfRetryable(HttpStatusCode status, String context) {
        int code = status.value();
        if (code == 429 || code == 502 || code == 503 || code == 504) {
            throw new RetryableApiException("Retryable Zenodo failure. HTTP " + code + " for " + context);
        }
    }
}
