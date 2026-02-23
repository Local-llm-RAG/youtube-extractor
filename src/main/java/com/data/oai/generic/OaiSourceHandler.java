package com.data.oai.generic;

import com.data.oai.DataSource;
import com.data.oai.generic.common.dto.Record;

import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.List;

public interface OaiSourceHandler {

    DataSource supports();

    List<Record> fetchMetadata(LocalDate startInclusive, LocalDate endInclusive);

    AbstractMap.SimpleEntry<String, byte[]> fetchPdfAndEnrich(Record record);
}