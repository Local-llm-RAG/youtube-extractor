package com.data.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the PMC S3 direct integration pipeline.
 *
 * <p>The pipeline accesses the PMC Open Access S3 bucket over plain HTTPS
 * (no AWS SDK), uses a daily inventory CSV for discovery, and processes
 * articles in batches with tracker-based progress persistence.
 */
@ConfigurationProperties(prefix = "pmcs3")
public record PmcS3Properties(
    String bucketBaseUrl,
    String inventoryPrefix,
    int batchSize,
    int concurrency,
    String cron,
    long advisoryLockKey,
    HttpClientProperties httpClient
) {}
