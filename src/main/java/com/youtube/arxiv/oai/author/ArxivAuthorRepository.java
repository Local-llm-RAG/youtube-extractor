package com.youtube.arxiv.oai.author;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArxivAuthorRepository extends JpaRepository<ArxivAuthorEntity, Long> {
    List<ArxivAuthorEntity> findAllByRecord_IdOrderByIdAsc(Long recordId);
}
