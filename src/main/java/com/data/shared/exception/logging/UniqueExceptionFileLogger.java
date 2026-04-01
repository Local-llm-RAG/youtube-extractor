package com.data.shared.exception.logging;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
class UniqueExceptionFileLogger implements ExceptionFileLogger {

    private static final String ENTRY_SEPARATOR = "===";
    private static final String EXCEPTION_PREFIX = "Exception: ";
    private static final String PATTERN_PREFIX = "Pattern:   ";
    private static final int MAX_STACK_FRAMES = 5;

    private final Path filePath;
    private final Set<ExceptionSignature> knownSignatures = ConcurrentHashMap.newKeySet();

    UniqueExceptionFileLogger(ExceptionFileLoggerProperties properties) {
        this.filePath = Path.of(properties.filePath());
    }

    @PostConstruct
    void init() {
        try {
            ensureParentDirectoryExists();
            loadExistingSignatures();
            log.info("UniqueExceptionFileLogger initialized with {} known signatures from {}",
                    knownSignatures.size(), filePath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to initialize UniqueExceptionFileLogger at {}", filePath.toAbsolutePath(), e);
        }
    }

    /**
     * Logs an exception if its signature has not been seen before.
     * Thread-safe: concurrent calls will not produce duplicate entries.
     *
     * @param throwable the exception to potentially log
     */
    @Override
    public void log(Throwable throwable) {
        if (throwable == null) {
            return;
        }

        ExceptionSignature signature = ExceptionSignature.from(throwable);

        // ConcurrentHashMap.newKeySet().add() is atomic — returns false if already present
        if (!knownSignatures.add(signature)) {
            return;
        }

        writeEntry(throwable, signature);
    }

    private void writeEntry(Throwable throwable, ExceptionSignature signature) {
        String entry = formatEntry(throwable, signature);
        try {
            synchronized (this) {
                ensureParentDirectoryExists();
                try (BufferedWriter writer = Files.newBufferedWriter(filePath,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    writer.write(entry);
                    writer.flush();
                }
            }
        } catch (IOException e) {
            log.error("Failed to write unique exception entry to {}", filePath.toAbsolutePath(), e);
        }
    }

    private String formatEntry(Throwable throwable, ExceptionSignature signature) {
        var sb = new StringBuilder();
        sb.append(ENTRY_SEPARATOR).append(" [").append(Instant.now()).append("] ").append(ENTRY_SEPARATOR).append('\n');
        sb.append(EXCEPTION_PREFIX).append(signature.exceptionClass()).append('\n');
        sb.append(PATTERN_PREFIX).append(signature.normalizedMessage()).append('\n');
        sb.append("Example:   ").append(throwable.getMessage() == null ? "<null>" : throwable.getMessage()).append('\n');

        StackTraceElement[] stackTrace = throwable.getStackTrace();
        if (stackTrace != null && stackTrace.length > 0) {
            sb.append("StackTrace (first ").append(Math.min(stackTrace.length, MAX_STACK_FRAMES)).append(" frames):\n");
            for (int i = 0; i < Math.min(stackTrace.length, MAX_STACK_FRAMES); i++) {
                sb.append("  at ").append(stackTrace[i]).append('\n');
            }
        }

        if (throwable.getCause() != null) {
            sb.append("Caused by: ").append(throwable.getCause().getClass().getName())
                    .append(": ").append(throwable.getCause().getMessage()).append('\n');
        }

        sb.append('\n');
        return sb.toString();
    }

    private void loadExistingSignatures() throws IOException {
        if (!Files.exists(filePath)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String exceptionClass = null;
            String normalizedMessage = null;
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith(EXCEPTION_PREFIX)) {
                    exceptionClass = line.substring(EXCEPTION_PREFIX.length()).trim();
                } else if (line.startsWith(PATTERN_PREFIX)) {
                    normalizedMessage = line.substring(PATTERN_PREFIX.length()).trim();
                }

                // When we have both parts of a signature, register it
                if (exceptionClass != null && normalizedMessage != null) {
                    knownSignatures.add(new ExceptionSignature(exceptionClass, normalizedMessage));
                    exceptionClass = null;
                    normalizedMessage = null;
                }
            }
        }
    }

    private void ensureParentDirectoryExists() throws IOException {
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }
}
