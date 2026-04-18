package com.data.oai.grobid;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Config-shape regression test for the Resilience4j {@code grobid} retry instance.
 *
 * <p>The pipeline's tolerance to transient GROBID errors is an operational
 * property — the only place we can assert it without spinning up a Spring
 * context is the {@code application.yml} that wires the retry. If someone
 * loosens the retry budget, widens the window, or drops a critical retry
 * exception type, this test fails loudly.
 */
class GrobidRetryConfigTest {

    @Test
    void shouldBindGrobidRetryInstance_withBoundedBackoffAndCorrectExceptionSet() throws IOException {
        Map<String, Object> retryInstance = loadGrobidRetryInstance();

        // Bounded attempts: 3 total (1 initial + 2 retries).
        assertThat(retryInstance).containsEntry("maxAttempts", 3);

        // Fast initial retry — we want to ride through a single transient hiccup quickly.
        assertThat(retryInstance).containsEntry("waitDuration", "500ms");

        // Exponential backoff with a hard cap so a flapping backend can't
        // stretch a single record's processing time unbounded.
        assertThat(retryInstance).containsEntry("enableExponentialBackoff", true);
        assertThat(retryInstance).containsEntry("exponentialBackoffMultiplier", 2.0);
        assertThat(retryInstance).containsEntry("exponentialMaxWaitDuration", "2s");

        // Retry exceptions must include every transient failure mode the
        // RestClient / GROBID pipeline can surface: generic 5xx, connect
        // failures, raw IO, and our explicit 503/504 wrapper.
        @SuppressWarnings("unchecked")
        List<String> retryExceptions = (List<String>) retryInstance.get("retryExceptions");
        assertThat(retryExceptions).containsExactlyInAnyOrder(
                "org.springframework.web.client.ResourceAccessException",
                "org.springframework.web.client.HttpServerErrorException",
                "java.io.IOException",
                "java.net.ConnectException",
                "com.data.shared.exception.GrobidServiceUnavailableException"
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadGrobidRetryInstance() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertThat(in)
                    .as("application.yml must be on the test classpath")
                    .isNotNull();
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> resilience4j = (Map<String, Object>) root.get("resilience4j");
            Map<String, Object> retry = (Map<String, Object>) resilience4j.get("retry");
            Map<String, Object> instances = (Map<String, Object>) retry.get("instances");
            Map<String, Object> grobid = (Map<String, Object>) instances.get("grobid");
            assertThat(grobid)
                    .as("resilience4j.retry.instances.grobid must be defined")
                    .isNotNull();
            return grobid;
        }
    }
}
