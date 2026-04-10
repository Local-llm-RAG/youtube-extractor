package com.data.storage;

import java.time.LocalDate;
import java.util.List;

/**
 * Nested record structure for JSONL export of research paper data.
 * Maps the full source_record graph: document, authors, category, sections (with embedding chunks), and references.
 */
public record PaperExportDto(
        // source_record fields
        String sourceIdentifier,
        String oaiIdentifier,
        String doi,
        String license,
        String pdfUrl,
        String language,
        String dataSource,
        LocalDate datestamp,
        String comments,
        String journalRef,
        // record_document fields
        String title,
        String abstractText,
        String rawContent,
        String docType,
        List<String> keywordList,
        List<String> affiliationList,
        List<String> classCodeList,
        // record_author (ordered by pos)
        List<AuthorDto> authors,
        // record_category
        String category,
        // document_section (ordered by pos, with embedding chunks nested inside)
        List<SectionDto> sections,
        // reference_mention (ordered by ref_index)
        List<ReferenceDto> references
) {

    public record AuthorDto(
            int pos,
            String firstName,
            String lastName
    ) {}

    public record SectionDto(
            int pos,
            Integer level,
            String title,
            String text,
            List<EmbeddingChunkDto> chunks
    ) {}

    public record EmbeddingChunkDto(
            int chunkIndex,
            String chunkText,
            String embeddingModel,
            int dim,
            String task,
            Integer chunkTokens,
            Integer chunkOverlap,
            Integer spanStart,
            Integer spanEnd
    ) {}

    public record ReferenceDto(
            int refIndex,
            String title,
            String doi,
            String year,
            String venue,
            List<String> authors,
            List<String> urls,
            List<String> idnos
    ) {}
}
