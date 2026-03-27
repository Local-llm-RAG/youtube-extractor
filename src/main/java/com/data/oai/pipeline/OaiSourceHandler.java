package com.data.oai.pipeline;

import com.data.oai.shared.dto.PdfContent;
import com.data.oai.shared.dto.Record;

import java.time.LocalDate;
import java.util.List;

public interface OaiSourceHandler {

    DataSource supports();

    List<Record> fetchMetadata(LocalDate startInclusive, LocalDate endInclusive);

    PdfContent fetchPdfAndEnrich(Record record);
}
