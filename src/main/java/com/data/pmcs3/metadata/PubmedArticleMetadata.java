package com.data.pmcs3.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Subset of the PMC S3 per-article JSON metadata we care about.
 *
 * <p>The raw JSON contains many more fields; we deserialize only what drives
 * license filtering and data mapping. Unknown fields are ignored for forward
 * compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PubmedArticleMetadata(
        @JsonProperty("pmcid") String pmcid,
        @JsonProperty("pmid") String pmid,
        @JsonProperty("doi") String doi,
        @JsonProperty("license_code") String licenseCode,
        @JsonProperty("license_url") String licenseUrl,
        @JsonProperty("publication_date") String publicationDate,
        @JsonProperty("article_title") String articleTitle,
        @JsonProperty("journal_title") String journalTitle,
        @JsonProperty("pdf_url") String pdfUrl,
        @JsonProperty("text_url") String textUrl,
        @JsonProperty("xml_url") String xmlUrl
) {}
