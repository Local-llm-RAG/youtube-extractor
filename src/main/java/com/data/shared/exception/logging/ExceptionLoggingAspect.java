package com.data.shared.exception.logging;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Cross-cutting aspect that intercepts all exceptions thrown from any Spring-managed bean
 * in the {@code com.data} package hierarchy and delegates them to the
 * {@link UniqueExceptionFileLogger} for deduplication and persistence.
 * <p>
 * This captures both exceptions that propagate to the caller (unhandled) and exceptions
 * that are thrown and later caught by upstream code. The aspect fires at the throw site,
 * so every unique exception pattern is recorded regardless of whether it is subsequently
 * handled.
 * <p>
 * The aspect does not interfere with exception propagation — it only observes.
 */
@Aspect
@Component
@RequiredArgsConstructor
class ExceptionLoggingAspect {

    private final ExceptionFileLogger exceptionFileLogger;

    @AfterThrowing(pointcut = "execution(* com.data..*(..)) && !within(com.data.shared.exception.logging..*) && !within(com.data.config..*)", throwing = "ex")
    void logException(Throwable ex) {
        exceptionFileLogger.log(ex);
    }
}
