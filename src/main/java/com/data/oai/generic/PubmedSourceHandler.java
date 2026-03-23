package com.data.oai.generic;

import com.data.oai.DataSource;
import com.data.oai.generic.common.dto.Record;
import com.data.oai.pubmed.PubmedOaiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PubmedSourceHandler implements OaiSourceHandler {

    private final PubmedOaiService pubmedOaiService;

    @Override
    public DataSource supports() {
        return DataSource.PUBMED;
    }

    @Override
    public List<Record> fetchMetadata(LocalDate startInclusive, LocalDate endInclusive) {
        return pubmedOaiService.getPubmedPapersMetadata(
                startInclusive.toString(),
                endInclusive.toString()
        );
    }

    @Override
    public AbstractMap.SimpleEntry<String, byte[]> fetchPdfAndEnrich(Record record) {
        return pubmedOaiService.getPdf(record.getSourceId());
    }
}
