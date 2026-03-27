package com.data.oai.persistence.repository;

import com.data.oai.persistence.entity.PaperDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperDocumentRepository extends JpaRepository<PaperDocumentEntity, Long> {
}
