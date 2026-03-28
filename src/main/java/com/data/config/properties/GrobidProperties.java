package com.data.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grobid")
public record GrobidProperties(
        String baseUrl,
        String fulltextEndpoint,
        HttpClientProperties httpClient,
        Options options
) {
    public record Options(
            boolean consolidateHeader,
            boolean consolidateCitations,
            boolean segmentSentences,
            boolean includeRawCitations,
            boolean includeRawAffiliations
    ) {}
}
