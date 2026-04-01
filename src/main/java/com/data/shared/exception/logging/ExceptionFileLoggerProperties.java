package com.data.shared.exception.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "exception-logger")
public record ExceptionFileLoggerProperties(String filePath) {

    private static final String DEFAULT_FILE_PATH = "logs/unique-exceptions.log";

    public ExceptionFileLoggerProperties {
        if (filePath == null || filePath.isBlank()) {
            filePath = DEFAULT_FILE_PATH;
        }
    }
}
