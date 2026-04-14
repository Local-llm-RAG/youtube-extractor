package com.data.oai.shared.dto;

import java.util.ArrayList;
import java.util.List;

public record PaperDocument(
        String sourceId,
        String sourceIdentifier,
        String title,
        String abstractText,
        List<Section> sections,
        String sourceXml,
        String rawContent,
        List<String> keywords,
        List<String> affiliation,
        List<String> classCodes,
        List<String> fundingList,
        List<Reference> references,
        String docType) {
    public PaperDocument {
        sections = (sections == null || sections.isEmpty())
                ? List.of(new Section("BODY", null, "", new ArrayList<>()))
                : List.copyOf(sections);
    }

    /**
     * Returns a minimal document with no content for the given identifiers.
     * All text fields are {@code null}; all list fields are empty.
     * Used when the upstream source supplies no parseable content.
     */
    public static PaperDocument empty(String sourceId, String externalIdentifier) {
        return new PaperDocument(
                sourceId,
                externalIdentifier,
                null,
                null,
                List.of(new Section("BODY", 1, "", List.of())),
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        );
    }

    /**
     * Returns a copy of this document with the {@code rawContent} field replaced.
     * Returns {@code this} unchanged when {@code rawContent} is {@code null},
     * preserving the same null-means-absent semantics as the previous facade helper.
     */
    public PaperDocument withRawContent(String rawContent) {
        if (rawContent == null) return this;
        return new PaperDocument(
                sourceId,
                sourceIdentifier,
                title,
                abstractText,
                sections,
                sourceXml,
                rawContent,
                keywords,
                affiliation,
                classCodes,
                fundingList,
                references,
                docType
        );
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
                sourceXml,
                rawContent,
                keywords,
                affiliation,
                classCodes,
                fundingList,
                references,
                docType
        );
    }
}
