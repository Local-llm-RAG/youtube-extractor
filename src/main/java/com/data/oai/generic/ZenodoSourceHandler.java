package com.data.oai.generic;

import com.data.oai.DataSource;
import com.data.oai.generic.common.dto.Record;
import com.data.oai.zenodo.ZenodoOaiService;
import com.data.oai.zenodo.ZenodoRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ZenodoSourceHandler implements OaiSourceHandler {

    private final ZenodoOaiService zenodoOaiService;

    @Override
    public DataSource supports() {
        return DataSource.ZENODO;
    }

    @Override
    public List<Record> fetchMetadata(LocalDateTime startInclusive, LocalDateTime endInclusive) {
        // keep your current "plusHours" semantics
        return zenodoOaiService.getZenodoPapersMetadata(
                startInclusive.plusHours(1).toString(),
                startInclusive.plusHours(2).toString()
        );
    }

    @Override
    public AbstractMap.SimpleEntry<String, byte[]> fetchPdfAndEnrich(Record record) {
        var map = zenodoOaiService.getPdf(record.getArxivId());
        ZenodoRecord zenodoRecord = map.getKey();
        record.setLanguage(zenodoRecord.getMetadata().getLanguage());
        return new AbstractMap.SimpleEntry<>(zenodoRecord.getLinks().getSelf(), map.getValue());
    }
}