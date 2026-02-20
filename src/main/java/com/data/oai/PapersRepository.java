package com.data.oai;

import com.data.oai.generic.common.record.RecordEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PapersRepository extends JpaRepository<RecordEntity, Long> { // TODO, this has to point to right DAO
    @EntityGraph(attributePaths = {
            "document",
            "document.sections"
    })
    Optional<RecordEntity> findByArxivId(String arxivId);

}
