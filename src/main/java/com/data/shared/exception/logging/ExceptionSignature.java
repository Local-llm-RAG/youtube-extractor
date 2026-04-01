package com.data.shared.exception.logging;

/**
 * Represents a unique exception "signature" composed of the exception class name
 * and a normalized message template. Two exceptions with the same signature are
 * considered duplicates for logging purposes.
 *
 * @param exceptionClass the fully qualified class name of the exception
 * @param normalizedMessage the message after variable-part normalization
 */
record ExceptionSignature(String exceptionClass, String normalizedMessage) {

    static ExceptionSignature from(Throwable throwable) {
        return new ExceptionSignature(
                throwable.getClass().getName(),
                ExceptionMessageNormalizer.normalize(throwable.getMessage())
        );
    }
}
