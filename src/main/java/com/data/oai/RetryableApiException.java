package com.data.oai;

public class RetryableApiException extends RuntimeException {
    public RetryableApiException(String message) {
        super(message);
    }
}
