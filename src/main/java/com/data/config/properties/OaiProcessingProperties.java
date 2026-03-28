package com.data.config.properties;

import com.data.oai.pipeline.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "oai.processing")
public record OaiProcessingProperties(int daysBack, List<DataSource> sources, int concurrency, int queue,
                                      HttpClientProperties httpClient) {}
