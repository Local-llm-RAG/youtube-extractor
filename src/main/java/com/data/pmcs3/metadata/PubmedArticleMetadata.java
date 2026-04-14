package com.data.pmcs3.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Subset of the PMC S3 per-article JSON metadata we care about.
 *
 * <p>The raw JSON contains many more fields; we deserialize only what drives
 * license filtering and data mapping. Unknown fields are ignored for forward
 * compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PubmedArticleMetadata(
        String pmcid,
        String pmid,
        String doi,
        String licenseCode,
        String licenseUrl,
        String publicationDate,
        String articleTitle,
        String journalTitle,
        String pdfUrl,
        String textUrl,
        String xmlUrl
) {}
