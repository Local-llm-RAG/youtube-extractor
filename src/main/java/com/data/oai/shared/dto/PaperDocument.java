package com.data.oai.shared.dto;

import java.util.ArrayList;
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
                ? List.of(new Section("BODY", null, "", new ArrayList<>()))
                : List.copyOf(sections);
    }
}
