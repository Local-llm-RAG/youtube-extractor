package com.data.shared.exception;

public class GrobidProcessingException extends ApplicationException {
    public GrobidProcessingException(String message) { super(message); }
    public GrobidProcessingException(String message, Throwable cause) { super(message, cause); }
}
