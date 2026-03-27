package com.data.oai.persistence.repository;

import com.data.oai.persistence.entity.RecordAuthorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecordAuthorRepository extends JpaRepository<RecordAuthorEntity, Long> {
    List<RecordAuthorEntity> findAllByRecord_IdOrderByIdAsc(Long recordId);
}
