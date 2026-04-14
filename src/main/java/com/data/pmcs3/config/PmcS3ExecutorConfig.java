package com.data.pmcs3.config;

import com.data.config.properties.PmcS3Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides a dedicated {@link ExecutorService} for the PMC S3 pipeline.
 *
 * <p>Uses Java 25 virtual threads: PMC S3 processing is overwhelmingly
 * I/O-bound (HTTPS GETs for JSON / XML / TXT blobs) so platform threads
 * would be wasted blocking on the network. Virtual threads let us fan out
 * thousands of in-flight downloads with negligible memory overhead.
 *
 * <p>The returned executor is unbounded by {@code concurrency} in terms of
 * thread count — concurrency is still bounded by the HTTP connection pool
 * sized in {@link PmcS3RestClientConfig} and the Resilience4j rate limiter.
 */
@Configuration
public class PmcS3ExecutorConfig {

    @Bean(name = "pmcS3Executor", destroyMethod = "shutdown")
    public ExecutorService pmcS3Executor(PmcS3Properties props) {
        // Virtual threads ignore pool sizing — the props are still used by the HTTP pool
        // and rate limiter — but we expose the executor as a named bean for clarity.
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("pmcs3-", 0).factory());
    }
}
