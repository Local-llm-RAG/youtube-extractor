package com.youtube.arxiv.oai.paper;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ArxivPaperDocumentRepository extends JpaRepository<ArxivPaperDocumentEntity, Long> {
}
