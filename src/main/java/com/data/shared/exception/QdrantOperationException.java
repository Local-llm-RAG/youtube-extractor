package com.data.shared.exception;

public class QdrantOperationException extends ApplicationException {
    public QdrantOperationException(String message) { super(message); }
    public QdrantOperationException(String message, Throwable cause) { super(message, cause); }
}
