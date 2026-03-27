package com.data.oai.persistence.repository;

import com.data.oai.persistence.entity.SectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SectionRepository extends JpaRepository<SectionEntity, Long> {
    List<SectionEntity> findAllByDocument_IdOrderByIdAsc(Long documentId);
}
