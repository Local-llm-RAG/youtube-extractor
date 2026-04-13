package com.data.oai.pipeline;

import com.data.shared.DataSource;
import com.data.shared.exception.UnsupportedDataSourceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OaiSourceRegistry {

    private final List<OaiSourceHandler> handlers;

    public OaiSourceHandler get(DataSource dataSource) {
        return handlers.stream().filter(handler -> handler.supports() == dataSource)
                .findFirst()
                .orElseThrow(() -> new UnsupportedDataSourceException("Cannot find handler for current data source"));
    }
}
