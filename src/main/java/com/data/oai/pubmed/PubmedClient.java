package com.data.oai.pubmed;

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
public class PubmedClient {

    private final RestClient rest;

    public PubmedClient(@Qualifier("grobidRestClient") RestClient rest) {
        this.rest = rest;
    }

    @Retry(name = "pubmed")
    @RateLimiter(name = "pubmed")
    public byte[] listRecords(String baseUrl, String from, String until,
                              String token, String metadataPrefix, String set) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromUriString(baseUrl)
                .queryParam("verb", "ListRecords");

        if (token == null) {
            b.queryParam("metadataPrefix", metadataPrefix)
                    .queryParam("from", from)
                    .queryParam("until", until)
                    .queryParam("set", set);
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
                    if (status.value() == 404) {
                        return null;
                    }
                    throwIfRetryable(status, uri.toString());
                    throw new IllegalStateException(
                            "PMC OAI call failed. HTTP " + status.value() + " for " + uri);
                });
    }

    @Retry(name = "pubmed")
    @RateLimiter(name = "pubmed")
    public byte[] fetchOaLinks(String pmcId) {
        URI uri = URI.create("https://www.ncbi.nlm.nih.gov/pmc/utils/oa/oa.fcgi?id=" + pmcId);

        return rest.get()
                .uri(uri)
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status.is2xxSuccessful()) {
                        return res.bodyTo(byte[].class);
                    }
                    if (status.value() == 404) {
                        return null;
                    }
                    throwIfRetryable(status, uri.toString());
                    throw new IllegalStateException(
                            "PMC OA link call failed. HTTP " + status.value() + " for " + uri);
                });
    }

    @Retry(name = "pubmed")
    @RateLimiter(name = "pubmed")
    public byte[] downloadPdf(String url) {
        String httpsUrl = url.replace("ftp://ftp.ncbi.nlm.nih.gov/",
                "https://ftp.ncbi.nlm.nih.gov/");
        String decoded = UriUtils.decode(httpsUrl, StandardCharsets.UTF_8);
        URI uri = UriComponentsBuilder.fromUriString(decoded).encode().build().toUri();

        return rest.get()
                .uri(uri)
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status.is2xxSuccessful()) {
                        return res.bodyTo(byte[].class);
                    }
                    throwIfRetryable(status, url);
                    throw new IllegalStateException(
                            "PMC PDF download failed. HTTP " + status.value() + " for " + url);
                });
    }

    private static void throwIfRetryable(HttpStatusCode status, String context) {
        int code = status.value();
        if (code == 429 || code == 502 || code == 503 || code == 504) {
            throw new RetryableApiException("Retryable PMC failure. HTTP " + code + " for " + context);
        }
    }
}
