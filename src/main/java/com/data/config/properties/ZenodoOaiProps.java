package com.data.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oai.zenodo")
public record ZenodoOaiProps(
        String baseUrl,
        String metadataPrefix
) {}
