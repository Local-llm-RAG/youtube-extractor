package com.data.oai.shared.dto;

import com.data.embedding.dto.EmbeddingDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class Section {
    String title;
    String text;
    List<EmbeddingDto> embeddings;
}
