package com.youtube.arxiv.oai;

import com.youtube.arxiv.oai.record.ArxivRecordEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArxivRepository extends JpaRepository<ArxivRecordEntity, Long> { // TODO, this has to point to right DAO
    @EntityGraph(attributePaths = {
            "document",
            "document.sections"
    })
    Optional<ArxivRecordEntity> findByArxivId(String arxivId);

}
