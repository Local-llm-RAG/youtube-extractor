package com.data.shared.exception;

public class UnsupportedDataSourceException extends ApplicationException {
    public UnsupportedDataSourceException(String message) { super(message); }
    public UnsupportedDataSourceException(String message, Throwable cause) { super(message, cause); }
}
