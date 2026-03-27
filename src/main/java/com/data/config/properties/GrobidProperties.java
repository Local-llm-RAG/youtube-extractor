package com.data.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grobid")
public record GrobidProperties(
        String baseUrl,
        String fulltextEndpoint,
        Client client,
        Options options
) {
    public record Client(int concurrency, int queue) {}

    public record Options(
            boolean consolidateHeader,
            boolean consolidateCitations,
            boolean segmentSentences,
            boolean includeRawCitations,
            boolean includeRawAffiliations
    ) {}
}
