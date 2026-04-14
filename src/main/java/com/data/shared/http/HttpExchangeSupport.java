package com.data.shared.http;

import com.data.oai.RetryableApiException;
import com.data.shared.exception.HarvestException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.IntPredicate;

/**
 * Shared HTTP utilities for pipeline clients (OAI-PMH, PMC S3, and others).
 * Eliminates duplicated URI building, retryable detection, and URL encoding
 * across ArXiv, Zenodo, and PubMed clients.
 */
public final class HttpExchangeSupport {

    private HttpExchangeSupport() {}

    /**
     * Builds a standard OAI-PMH ListRecords URI.
     * On the initial call (token == null), includes metadataPrefix, from, until, and optional set.
     * On subsequent calls, sends only the resumptionToken.
     */
    public static URI buildListRecordsUri(String baseUrl, String from, String until,
                                          String token, String metadataPrefix, String set) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromUriString(baseUrl)
                .queryParam("verb", "ListRecords");

        if (token == null) {
            b.queryParam("metadataPrefix", metadataPrefix)
                    .queryParam("from", from)
                    .queryParam("until", until);
            if (set != null) {
                b.queryParam("set", set);
            }
        } else {
            b.queryParam("resumptionToken", token);
        }

        return b.build(true).toUri();
    }

    public static URI buildListRecordsUri(String baseUrl, String from, String until,
                                          String token, String metadataPrefix) {
        return buildListRecordsUri(baseUrl, from, until, token, metadataPrefix, null);
    }

    /**
     * Throws {@link RetryableApiException} for transient HTTP errors (429, 502, 503, 504).
     * Callers use this inside their RestClient exchange handlers
     * so that Resilience4j @Retry can intercept and retry.
     */
    public static void throwIfRetryable(HttpStatusCode status, String context) {
        int code = status.value();
        if (code == 429 || code == 502 || code == 503 || code == 504) {
            throw new RetryableApiException("Retryable HTTP " + code + " for " + context);
        }
    }

    /**
     * Decodes and re-encodes a URL to safely handle pre-encoded or special characters
     * in download URLs.
     */
    public static URI toEncodedUri(String url) {
        String decoded = UriUtils.decode(url, StandardCharsets.UTF_8);
        return UriComponentsBuilder.fromUriString(decoded).encode().build().toUri();
    }

    /**
     * Throws {@link HarvestException} for a non-retryable HTTP failure.
     * Called after {@link #throwIfRetryable} to handle the remaining error cases.
     */
    public static HarvestException harvestException(HttpStatusCode status, String context) {
        return new HarvestException("HTTP call failed. HTTP " + status.value() + " for " + context);
    }

    /**
     * Executes a GET request via the given RestClient and returns the body as byte[].
     * <p>
     * Source-specific HTTP quirks are handled via {@code acceptStatus}:
     * <ul>
     *   <li>Zenodo passes {@code code -> code == 422} (OAI error responses that need parsing)</li>
     *   <li>PubMed passes {@code code -> code == 404} (no records = null body)</li>
     *   <li>ArXiv passes {@code code -> false} (no special codes)</li>
     * </ul>
     */
    public static byte[] executeExchange(RestClient rest, URI uri,
                                         IntPredicate acceptStatus, String context) {
        return rest.get()
                .uri(uri)
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status.is2xxSuccessful() || acceptStatus.test(status.value())) {
                        return res.bodyTo(byte[].class);
                    }
                    throwIfRetryable(status, context);
                    throw harvestException(status, context);
                });
    }

    /**
     * Executes a GET request via the given RestClient and returns the body as byte[],
     * or {@code null} when the response status matches {@code nullOnStatus}.
     * <p>
     * Use this overload when a specific non-2xx status (e.g. 404) should be treated
     * as a "not found" signal rather than an error — the body is discarded and
     * {@code null} is returned to the caller. All other non-2xx statuses are
     * handled the same as {@link #executeExchange}: retryable codes throw
     * {@link RetryableApiException}; permanent failures throw {@link HarvestException}.
     *
     * @param nullOnStatus predicate that returns {@code true} for status codes
     *                     that should yield a {@code null} result (not an error)
     */
    public static byte[] executeExchangeOrNull(RestClient rest, URI uri,
                                               IntPredicate nullOnStatus, String context) {
        return rest.get()
                .uri(uri)
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status.is2xxSuccessful()) {
                        return res.bodyTo(byte[].class);
                    }
                    if (nullOnStatus.test(status.value())) {
                        return null;
                    }
                    throwIfRetryable(status, context);
                    throw harvestException(status, context);
                });
    }
}
