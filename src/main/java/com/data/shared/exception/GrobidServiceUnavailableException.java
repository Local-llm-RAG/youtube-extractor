package com.data.shared.exception;

public class GrobidServiceUnavailableException extends ApplicationException {
    public GrobidServiceUnavailableException(String message) { super(message); }
    public GrobidServiceUnavailableException(String message, Throwable cause) { super(message, cause); }
}
