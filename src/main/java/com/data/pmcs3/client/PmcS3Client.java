package com.data.pmcs3.client;

import com.data.config.properties.PmcS3Properties;
import com.data.oai.RetryableApiException;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * HTTP client for the PMC Open Access S3 bucket. Uses plain HTTPS — no AWS SDK.
 *
 * <p>The bucket is public and requires no authentication. All reads are simple
 * {@code GET /{key}} requests against {@code https://pmc-oa-opendata.s3.amazonaws.com}.
 *
 * <p>PMC article layout (live now):
 * <pre>
 *   metadata/PMC{id}.{version}.json           — Article JSON metadata (inventory source of truth)
 *   PMC{id}.{version}/PMC{id}.{version}.xml   — JATS XML
 *   PMC{id}.{version}/PMC{id}.{version}.txt   — Plain-text rendering
 *   PMC{id}.{version}/PMC{id}.{version}.pdf   — PDF
 * </pre>
 */
@Slf4j
@Service
public class PmcS3Client {

    private final PmcS3Properties props;
    private final RestClient rest;

    public PmcS3Client(PmcS3Properties props,
                       @Qualifier("pmcS3RestClient") RestClient rest) {
        this.props = props;
        this.rest = rest;
    }

    /**
     * Returns the URL that would be used for a given S3 key.
     */
    public String urlFor(String key) {
        return props.bucketBaseUrl() + "/" + key;
    }

    /**
     * Downloads the object at the given key as raw bytes, or {@code null}
     * if the object does not exist (HTTP 404).
     */
    @Retry(name = "pmcs3")
    @RateLimiter(name = "pmcs3")
    public byte[] downloadBytes(String key) {
        URI uri = URI.create(urlFor(key));
        return rest.get()
                .uri(uri)
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status.is2xxSuccessful()) {
                        return res.bodyTo(byte[].class);
                    }
                    if (status.value() == 404) {
                        log.debug("PMC S3 object not found: {}", key);
                        return null;
                    }
                    // Retryable: rate limits and transient gateway/server errors.
                    int code = status.value();
                    if (code == 429 || code == 502 || code == 503 || code == 504) {
                        throw new RetryableApiException(
                                "PMC S3 transient HTTP " + code + " for key=" + key);
                    }
                    throw new PmcS3HttpException(
                            "PMC S3 GET failed. HTTP " + code + " for key=" + key);
                });
    }

    /**
     * Downloads the object at the given key and decodes it as UTF-8 text,
     * or {@code null} if the object does not exist.
     */
    public String downloadText(String key) {
        byte[] bytes = downloadBytes(key);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Executes an S3 {@code ListObjectsV2} request against the bucket root with
     * the given prefix and {@code /} delimiter, returning the raw XML response
     * body. Used to dynamically discover the newest daily inventory folder
     * without hardcoding the publish-hour convention.
     *
     * @param prefix S3 key prefix to list (a trailing slash will be appended if missing)
     * @return raw XML body from the S3 list response, or {@code null} on 404
     */
    @Retry(name = "pmcs3")
    @RateLimiter(name = "pmcs3")
    public String listCommonPrefixes(String prefix) {
        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        URI uri = URI.create(props.bucketBaseUrl()
                + "/?list-type=2"
                + "&prefix=" + normalizedPrefix
                + "&delimiter=/");
        return rest.get()
                .uri(uri)
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status.is2xxSuccessful()) {
                        byte[] body = res.bodyTo(byte[].class);
                        return body == null ? null : new String(body, StandardCharsets.UTF_8);
                    }
                    if (status.value() == 404) {
                        log.debug("PMC S3 list returned 404 for prefix={}", normalizedPrefix);
                        return null;
                    }
                    int code = status.value();
                    if (code == 429 || code == 502 || code == 503 || code == 504) {
                        throw new RetryableApiException(
                                "PMC S3 transient HTTP " + code + " for list prefix=" + normalizedPrefix);
                    }
                    throw new PmcS3HttpException(
                            "PMC S3 LIST failed. HTTP " + code + " for prefix=" + normalizedPrefix);
                });
    }

    /**
     * Returns the S3 key for a given per-article asset living inside the
     * article directory (JATS XML, plain text, PDF).
     *
     * <p>Layout: {@code PMC{id}.{version}/PMC{id}.{version}.{extension}}.
     *
     * <p>NOTE: do not use this for the metadata JSON — the inventory is the
     * source of truth and points at the flat {@code metadata/} location. Use
     * {@link #metadataKey(String, int)} instead.
     *
     * @param pmcId      numeric PMC id (without {@code PMC} prefix), e.g. {@code "10009416"}
     * @param version    article version, e.g. {@code 1}
     * @param extension  file extension without leading dot, one of {@code xml|txt|pdf}
     */
    public String articleKey(String pmcId, int version, String extension) {
        String base = "PMC" + pmcId + "." + version;
        return base + "/" + base + "." + extension;
    }

    /**
     * Returns the S3 key for the per-article metadata JSON living under the
     * flat {@code metadata/} prefix, e.g. {@code metadata/PMC10009416.1.json}.
     *
     * <p>This is the path emitted by the PMC S3 Inventory CSV and is the
     * authoritative location for the article JSON. The per-article directory
     * may also contain a {@code .json} file, but pipeline code should fetch
     * from the inventory-aligned path to stay consistent with discovery.
     *
     * @param pmcId    numeric PMC id (without {@code PMC} prefix)
     * @param version  article version
     */
    public String metadataKey(String pmcId, int version) {
        return "metadata/PMC" + pmcId + "." + version + ".json";
    }

    /**
     * Runtime exception raised on unexpected non-2xx, non-404 HTTP responses.
     * Allows Resilience4j to retry transient failures (via {@code java.io.IOException}
     * configured in application.yml) while surface-raising permanent ones.
     */
    public static class PmcS3HttpException extends RuntimeException {
        public PmcS3HttpException(String message) { super(message); }
    }
}
