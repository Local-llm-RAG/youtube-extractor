package com.data.oai.grobid;

import com.data.shared.exception.GrobidServiceUnavailableException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioral test for the GROBID retry policy. Exercises the retry decorator
 * directly (no Spring context) using a {@link RetryConfig} that mirrors the
 * production {@code application.yml} {@code resilience4j.retry.instances.grobid}
 * block.
 *
 * <p>If the YAML is ever changed, {@link GrobidRetryConfigTest} will fail first —
 * this class then needs to be updated to keep in sync.
 *
 * <p>The failure/success scenarios validated here match the user-requested
 * contract for the GROBID bounded retry:
 * <ul>
 *   <li>Transient 5xx → retried → eventual success.</li>
 *   <li>Persistent 5xx → retries exhausted → final exception propagates.</li>
 *   <li>Client 4xx → NOT retried → attempt count remains 1.</li>
 *   <li>Transient I/O → retried like a 5xx.</li>
 * </ul>
 */
class GrobidRetryBehaviorTest {

    /**
     * Mirror of the {@code resilience4j.retry.instances.grobid} block in
     * {@code src/main/resources/application.yml}. Kept in this test file rather
     * than loaded from YAML because the retry annotations wire to a
     * {@link RetryRegistry} at Spring time and cannot be easily exercised
     * outside the context; programmatic construction gives the same guarantees
     * without the bootstrap cost.
     */
    private static RetryConfig grobidRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialBackoff(Duration.ofMillis(500), 2.0, Duration.ofSeconds(2)))
                .retryExceptions(
                        ResourceAccessException.class,
                        HttpServerErrorException.class,
                        IOException.class,
                        java.net.ConnectException.class,
                        GrobidServiceUnavailableException.class
                )
                .build();
    }

    @Test
    void shouldSucceedOnThirdAttempt_whenFirstTwoReturn503() {
        Retry retry = RetryRegistry.of(grobidRetryConfig()).retry("grobid");
        AtomicInteger attempts = new AtomicInteger(0);

        Supplier<String> call = Retry.decorateSupplier(retry, () -> {
            int n = attempts.incrementAndGet();
            if (n < 3) {
                throw HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE, "unavailable", null, null, null);
            }
            return "ok";
        });

        assertThat(call.get()).isEqualTo("ok");
        assertThat(attempts.get())
                .as("first two attempts fail with 503, third succeeds")
                .isEqualTo(3);
    }

    @Test
    void shouldExhaustRetriesAndPropagateException_whenAll503() {
        Retry retry = RetryRegistry.of(grobidRetryConfig()).retry("grobid");
        AtomicInteger attempts = new AtomicInteger(0);

        Supplier<String> call = Retry.decorateSupplier(retry, () -> {
            attempts.incrementAndGet();
            throw HttpServerErrorException.create(
                    HttpStatus.SERVICE_UNAVAILABLE, "unavailable", null, null, null);
        });

        assertThatThrownBy(call::get).isInstanceOf(HttpServerErrorException.class);
        assertThat(attempts.get())
                .as("every attempt fails — retry budget should be 3 total attempts")
                .isEqualTo(3);
    }

    @Test
    void shouldNotRetry_when4xxClientError() {
        Retry retry = RetryRegistry.of(grobidRetryConfig()).retry("grobid");
        AtomicInteger attempts = new AtomicInteger(0);

        Supplier<String> call = Retry.decorateSupplier(retry, () -> {
            attempts.incrementAndGet();
            throw HttpClientErrorException.create(
                    HttpStatus.BAD_REQUEST, "bad request", null, null, null);
        });

        assertThatThrownBy(call::get).isInstanceOf(HttpClientErrorException.class);
        assertThat(attempts.get())
                .as("4xx is not in retryExceptions — must fail on first attempt")
                .isEqualTo(1);
    }

    @Test
    void shouldRetry_whenIOException() {
        Retry retry = RetryRegistry.of(grobidRetryConfig()).retry("grobid");
        AtomicInteger attempts = new AtomicInteger(0);

        Supplier<String> call = Retry.decorateSupplier(retry, () -> {
            int n = attempts.incrementAndGet();
            if (n < 2) {
                throw new RuntimeException(new IOException("connection reset"));
            }
            return "ok";
        });

        // Resilience4j unwraps only checked retry exceptions when matching —
        // use a Retry.decorateCheckedSupplier path for true IOException retry.
        AtomicInteger attempts2 = new AtomicInteger(0);
        io.github.resilience4j.core.functions.CheckedSupplier<String> checked = () -> {
            int n = attempts2.incrementAndGet();
            if (n < 2) {
                throw new IOException("connection reset");
            }
            return "ok";
        };
        try {
            String result = Retry.decorateCheckedSupplier(retry, checked).get();
            assertThat(result).isEqualTo("ok");
        } catch (Throwable t) {
            throw new AssertionError("should not throw", t);
        }
        assertThat(attempts2.get())
                .as("IOException is retryable — should succeed on second attempt")
                .isEqualTo(2);
    }

    @Test
    void shouldRetry_whenGrobidServiceUnavailableException() {
        Retry retry = RetryRegistry.of(grobidRetryConfig()).retry("grobid");
        AtomicInteger attempts = new AtomicInteger(0);

        Supplier<String> call = Retry.decorateSupplier(retry, () -> {
            int n = attempts.incrementAndGet();
            if (n < 2) {
                throw new GrobidServiceUnavailableException("503 wrapped");
            }
            return "ok";
        });

        assertThat(call.get()).isEqualTo("ok");
        assertThat(attempts.get())
                .as("wrapped 503 exception is retryable")
                .isEqualTo(2);
    }
}
