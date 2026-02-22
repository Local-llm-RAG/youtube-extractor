package com.data.oai.generic.common.dto;

import java.util.List;

public record PaperDocument(
        String sourceId,
        String sourceIdentifier,
        String title,
        String abstractText,
        List<Section> sections,
        String teiXml,
        String rawContent,
        List<String> keywords,
        List<String> affiliation,
        List<String> classCodes,
        List<Reference> references,
        String docType) {
    public PaperDocument {
        sections = (sections == null || sections.isEmpty())
                ? List.of(new Section("BODY", null, ""))
                : List.copyOf(sections);
    }
}
