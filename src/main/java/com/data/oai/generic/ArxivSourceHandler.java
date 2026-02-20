package com.data.oai.generic;

import com.data.oai.DataSource;
import com.data.oai.arxiv.ArxivOaiService;
import com.data.oai.generic.common.dto.Record;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ArxivSourceHandler implements OaiSourceHandler {

    private final ArxivOaiService arxivOaiService;

    @Override
    public DataSource supports() {
        return DataSource.ARXIV;
    }

    @Override
    public List<Record> fetchMetadata(LocalDateTime startInclusive, LocalDateTime endInclusive) {
        return arxivOaiService.getArxivPapersMetadata(
                startInclusive.toString(),
                endInclusive.toLocalDate().toString() // keep your existing semantics if needed
        );
    }

    @Override
    public byte[] fetchPdfAndEnrich(Record record) {
        return arxivOaiService.getPdf(record.getArxivId());
    }
}