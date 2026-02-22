package com.data.oai.generic;

import com.data.embedding.dto.EmbeddingDto;
import com.data.jpa.dao.EmbedTranscriptChunkEntity;
import com.data.oai.generic.common.paper.PaperDocumentEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EmbeddingMapper {

    public static List<EmbedTranscriptChunkEntity> toEntity(
            PaperDocumentEntity document,
            EmbeddingDto dto) {

        if (dto == null || dto.getChunks() == null || dto.getEmbeddings() == null) {
            return Collections.emptyList();
        }

        List<EmbedTranscriptChunkEntity> entities = new ArrayList<>();

        List<String> chunks = dto.getChunks();
        List<List<Float>> embeddings = dto.getEmbeddings();
        List<List<Integer>> spans = dto.getSpans();

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            List<Float> embeddingsForChunk = embeddings.get(i);

            Integer spanStart = null;
            Integer spanEnd = null;

            if (spans != null && spans.size() > i && spans.get(i) != null && spans.get(i).size() >= 2) {
                spanStart = spans.get(i).get(0);
                spanEnd = spans.get(i).get(1);
            }

            EmbedTranscriptChunkEntity entity =
                    EmbedTranscriptChunkEntity.builder()
                            .document(document)
                            .task(dto.getEmbeddingTask())
                            .chunkTokens(dto.getChunkTokens())
                            .chunkOverlap(dto.getChunkOverlap())
                            .embeddingModel(dto.getEmbeddingModel())
                            .dim(dto.getDim())
                            .chunkIndex(i)
                            .chunkText(chunkText)
                            .spanStart(spanStart)
                            .spanEnd(spanEnd)
                            .embedding(embeddingsForChunk)
                            .build();

            entities.add(entity);
        }

        return entities;
    }
}