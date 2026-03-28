package com.data.config.properties;

public record HttpClientProperties(
    int connectTimeoutSeconds,
    int responseTimeoutSeconds,
    int idleEvictionSeconds,
    Integer validateAfterInactivitySeconds
) {}
