package com.data.shared.exception;

public class PdfDownloadException extends ApplicationException {
    public PdfDownloadException(String message) { super(message); }
    public PdfDownloadException(String message, Throwable cause) { super(message, cause); }
}
