package com.data.shared.exception;

public class TranscriptRateLimitedException extends RuntimeException {
    public TranscriptRateLimitedException(Throwable cause) { super(cause); }
}
