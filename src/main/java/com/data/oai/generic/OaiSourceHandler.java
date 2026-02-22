package com.data.oai.generic;

import com.data.oai.DataSource;
import com.data.oai.generic.common.dto.Record;

import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.List;

public interface OaiSourceHandler {

    DataSource supports();

    List<Record> fetchMetadata(LocalDateTime startInclusive, LocalDateTime endInclusive);

    AbstractMap.SimpleEntry<String, byte[]> fetchPdfAndEnrich(Record record);
}