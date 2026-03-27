package com.data.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oai.processing")
public record OaiProcessingProperties(int daysBack) {}
