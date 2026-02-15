package com.youtube.arxiv.oai.dto;

import java.util.List;

public record ArxivPaperDocument(
        String arxivId,
        String oaiIdentifier,
        String title,
        String abstractText,
        List<Section> sections,
        String teiXmlRaw,
        String rawContent,
        List<String> keywords,
        List<String> affiliation,
        List<String> classCodes,
        List<String> references,
        String docType) {
    public ArxivPaperDocument {
        sections = (sections == null || sections.isEmpty())
                ? List.of(new Section("BODY", null, ""))
                : List.copyOf(sections);
    }
}
