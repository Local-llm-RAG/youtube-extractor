package com.data.oai.persistence.repository;

import com.data.oai.persistence.entity.RecordEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RecordSearchRepository extends JpaRepository<RecordEntity, Long> {
    @EntityGraph(attributePaths = {
            "document",
            "document.sections"
    })
    Optional<RecordEntity> findBySourceId(String arxivId);

}
