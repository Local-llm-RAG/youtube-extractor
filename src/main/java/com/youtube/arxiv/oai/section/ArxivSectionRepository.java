package com.youtube.arxiv.oai.section;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArxivSectionRepository extends JpaRepository<ArxivSectionEntity, Long> {
    List<ArxivSectionEntity> findAllByDocument_IdOrderByIdAsc(Long documentId);
}
