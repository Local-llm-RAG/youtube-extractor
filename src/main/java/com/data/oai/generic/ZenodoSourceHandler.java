package com.data.oai.generic;

import com.data.oai.DataSource;
import com.data.oai.generic.common.dto.Record;
import com.data.oai.zenodo.ZenodoOaiService;
import com.data.oai.zenodo.ZenodoRecord;
import com.data.oai.zenodo.ZenodoRecordFilePicker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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
    public List<Record> fetchMetadata(LocalDate startInclusive, LocalDate endInclusive) {
        // keep your current "plusHours" semantics
        return zenodoOaiService.getZenodoPapersMetadata(
                startInclusive.toString(),
                endInclusive.toString()
        );
    }

    @Override
    public AbstractMap.SimpleEntry<String, byte[]> fetchPdfAndEnrich(Record record) {
        var map = zenodoOaiService.getPdf(record.getSourceId());
        ZenodoRecord zenodoRecord = map.getKey();
        ZenodoRecord.FileEntry pdf = ZenodoRecordFilePicker.pickPdfUrl(zenodoRecord);
        return new AbstractMap.SimpleEntry<>(pdf.getLinks().getSelf(), map.getValue());
    }
}