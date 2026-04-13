package com.data.storage;

/**
 * Projection for embed_transcript_chunk rows used during S3 export.
 * Deliberately excludes the {@code embedding real[]} column to avoid loading
 * ~1.95 GB of float arrays into memory.
 */
public record EmbedChunkExportProjection(
        Long sectionId,
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
