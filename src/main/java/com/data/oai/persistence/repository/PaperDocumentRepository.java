package com.data.oai.persistence.repository;

import com.data.oai.persistence.entity.PaperDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaperDocumentRepository extends JpaRepository<PaperDocumentEntity, Long> {

    @Query(value = """
            SELECT COALESCE(SUM(
                OCTET_LENGTH(COALESCE(tei_xml, '')) +
                OCTET_LENGTH(COALESCE(raw_content, '')) +
                OCTET_LENGTH(COALESCE(title, '')) +
                OCTET_LENGTH(COALESCE(abstract, ''))
            ), 0)
            + (SELECT COALESCE(SUM(OCTET_LENGTH(COALESCE(text, ''))), 0) FROM document_section)
            FROM record_document
            """, nativeQuery = true)
    long sumStoredContentBytes();
}
