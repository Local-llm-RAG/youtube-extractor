package com.data.shared.exception;

public class HarvestException extends ApplicationException {
    public HarvestException(String message) { super(message); }
    public HarvestException(String message, Throwable cause) { super(message, cause); }
}
