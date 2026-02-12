package com.youtube.external.rest.arxiv.dto;

import java.util.List;

public record ArxivPaperDocument(
        String arxivId,
        String oaiIdentifier,
        String title,
        List<String> authors,
        String abstractText,
        List<Section> sections,
        String teiXmlRaw
) {
    public ArxivPaperDocument {
        sections = (sections == null || sections.isEmpty())
                ? List.of(new Section("BODY", null, ""))
                : List.copyOf(sections);
        authors = (authors == null) ? List.of() : List.copyOf(authors);
    }
}
