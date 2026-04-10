package com.data.oai.persistence.repository;

import com.data.oai.persistence.entity.EmbedTranscriptChunkEntity;
import com.data.storage.EmbedChunkExportProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EmbedTranscriptChunkRepository extends JpaRepository<EmbedTranscriptChunkEntity, Long> {

    @Query("""
            SELECT new com.data.storage.EmbedChunkExportProjection(
                c.section.id, c.chunkIndex, c.chunkText, c.embeddingModel, c.dim,
                c.task, c.chunkTokens, c.chunkOverlap, c.spanStart, c.spanEnd)
            FROM EmbedTranscriptChunkEntity c
            WHERE c.section.id IN :sectionIds
            ORDER BY c.section.id, c.chunkIndex
            """)
    List<EmbedChunkExportProjection> findExportProjectionsBySectionIds(@Param("sectionIds") Collection<Long> sectionIds);
}
