package com.data.oai.generic;

import com.data.oai.DataSource;
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
                .orElseThrow(() -> new RuntimeException("Cannot find handler for current data source"));
    }
}