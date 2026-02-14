package com.youtube.arxiv.oai;

import com.youtube.arxiv.oai.record.ArxivRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArxivRepository extends JpaRepository<ArxivRecordEntity, Long> { // TODO, this has to point to right DAO
}
