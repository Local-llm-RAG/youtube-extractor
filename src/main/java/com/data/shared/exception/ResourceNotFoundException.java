package com.data.shared.exception;

public class ResourceNotFoundException extends ApplicationException {
    public ResourceNotFoundException(String message) { super(message); }
    public ResourceNotFoundException(String message, Throwable cause) { super(message, cause); }
}
