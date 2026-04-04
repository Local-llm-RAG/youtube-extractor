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

    /**
     * Returns a PaperDocument with fallback title and abstract applied
     * when the GROBID-extracted values are null or blank.
     * Returns {@code this} if no fallback is needed.
     */
    public PaperDocument withFallbacks(String fallbackTitle, String fallbackAbstract) {
        boolean needTitle = (title == null || title.isBlank()) && fallbackTitle != null && !fallbackTitle.isBlank();
        boolean needAbstract = (abstractText == null || abstractText.isBlank()) && fallbackAbstract != null && !fallbackAbstract.isBlank();

        if (!needTitle && !needAbstract) {
            return this;
        }

        return new PaperDocument(
                sourceId,
                sourceIdentifier,
                needTitle ? fallbackTitle : title,
                needAbstract ? fallbackAbstract : abstractText,
                sections,
                teiXml,
                rawContent,
                keywords,
                affiliation,
                classCodes,
                references,
                docType
        );
    }
}
