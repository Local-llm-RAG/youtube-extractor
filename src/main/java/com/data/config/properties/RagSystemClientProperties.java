package com.data.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.api")
public class RagSystemClientProperties {
    /**
     * Base URL of the FastAPI server, e.g. http://localhost:8000
     */
    private String baseUrl = "http://localhost:8000";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
