package com.youtube.jpa.repository;

import com.youtube.jpa.dao.arxiv.ArxivPaperDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArxivPaperDocumentRepository extends JpaRepository<ArxivPaperDocumentEntity, Long> {
}
