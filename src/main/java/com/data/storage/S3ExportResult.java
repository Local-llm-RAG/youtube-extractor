package com.data.storage;

import com.data.oai.pipeline.DataSource;

/**
 * Result of a single S3 export operation for one data source.
 *
 * @param dataSource        the data source that was exported
 * @param recordCount       number of records written to S3
 * @param s3Key             the S3 object key where the JSONL file was written, or null if no records were exported
 * @param sizeLimitReached  true if the export was truncated because the maxSize limit was hit
 */
public record S3ExportResult(
        DataSource dataSource,
        int recordCount,
        String s3Key,
        boolean sizeLimitReached
) {}
