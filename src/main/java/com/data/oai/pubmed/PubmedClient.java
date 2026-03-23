package com.data.oai.pubmed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
public class PubmedClient {

    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 1_000;
    private static final long THROTTLE_DELAY_MS = 400;

    /** Limits concurrent NCBI requests to avoid 503 rate limiting (max ~3 req/s). */
    private final Semaphore ncbiThrottle = new Semaphore(2);

    private final RestClient rest;

    public PubmedClient(@Qualifier("grobidRestClient") RestClient rest) {
        this.rest = rest;
    }

    /**
     * Calls PMC OAI-PMH ListRecords verb. On first call (token == null) sends
     * metadataPrefix, from, until, and set parameters. On subsequent calls only
     * the resumptionToken is sent.
     */
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
        return executeWithRetry(uri);
    }

    /**
     * Calls the PMC Open Access Web Service to retrieve download links (PDF/tgz)
     * for a given PMC article.
     * Endpoint: https://www.ncbi.nlm.nih.gov/pmc/utils/oa/oa.fcgi?id=PMC{numericId}
     */
    public byte[] fetchOaLinks(String pmcId) {
        URI uri = URI.create("https://www.ncbi.nlm.nih.gov/pmc/utils/oa/oa.fcgi?id=" + pmcId);
        return throttledExecute(uri);
    }

    /**
     * Downloads a PDF or tgz file from the given URL. Handles both HTTPS and FTP-to-HTTPS
     * conversion for NCBI hosted files.
     */
    public byte[] downloadPdf(String url) {
        String httpsUrl = url.replace("ftp://ftp.ncbi.nlm.nih.gov/",
                "https://ftp.ncbi.nlm.nih.gov/");
        URI uri = URI.create(httpsUrl);
        return throttledExecute(uri);
    }

    /**
     * Acquires a semaphore permit and adds a delay between requests to stay
     * within NCBI's ~3 req/s rate limit.
     */
    private byte[] throttledExecute(URI uri) {
        try {
            ncbiThrottle.acquire();
            try {
                return executeWithRetry(uri);
            } finally {
                sleep(THROTTLE_DELAY_MS);
                ncbiThrottle.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for NCBI throttle", e);
        }
    }

    private byte[] executeWithRetry(URI uri) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return rest.get()
                        .uri(uri)
                        .exchange((req, res) -> {
                            HttpStatusCode status = res.getStatusCode();

                            if (status.is2xxSuccessful()) {
                                return res.bodyTo(byte[].class);
                            }

                            // PMC returns 404 when no records exist for a date range
                            // instead of the standard OAI-PMH noRecordsMatch error
                            if (status.value() == 404) {
                                return null;
                            }

                            if (status.value() == 429 || status.value() == 503) {
                                throw new PubmedRateLimitException(
                                        "PMC rate limit hit (HTTP %d) for %s".formatted(status.value(), uri));
                            }

                            throw new IllegalStateException(
                                    "PMC API call failed. HTTP %d for %s".formatted(status.value(), uri));
                        });
            } catch (PubmedRateLimitException e) {
                if (attempt == MAX_RETRIES) {
                    throw new RuntimeException("PMC rate limit exceeded after %d retries for %s"
                            .formatted(MAX_RETRIES, uri), e);
                }
                long backoffMs = INITIAL_BACKOFF_MS * (1L << (attempt - 1));
                log.warn("PMC rate limited (attempt {}/{}), backing off {}ms: {}",
                        attempt, MAX_RETRIES, backoffMs, uri);
                sleep(backoffMs);
            }
        }
        throw new RuntimeException("Unexpected state: exhausted retries for " + uri);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class PubmedRateLimitException extends RuntimeException {
        PubmedRateLimitException(String message) {
            super(message);
        }
    }
}
