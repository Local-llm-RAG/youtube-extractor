package com.data.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        boolean enabled,
        ExportMode exportMode,
        S3Props s3
) {
    public enum ExportMode { FULL, INCREMENTAL }

    public record S3Props(
            String accessKey,
            String secretKey,
            String bucketName,
            String region,
            String keyPrefix
    ) {}
}
