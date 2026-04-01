package com.data.shared.exception.logging;

import java.util.regex.Pattern;

/**
 * Normalizes exception messages by replacing variable parts (IDs, UUIDs, URLs, numbers, etc.)
 * with placeholders, producing a stable "message template" suitable for deduplication.
 */
final class ExceptionMessageNormalizer {

    private ExceptionMessageNormalizer() {}

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[^\\s,)\"']+");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    // Matches standalone numbers (integers or decimals), including negative numbers.
    // Uses word boundaries to avoid replacing numbers embedded in class names or identifiers.
    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("\\b-?\\d+(\\.\\d+)?\\b");

    // Matches hex strings that are at least 8 characters long (common for hashes, object IDs)
    private static final Pattern HEX_ID_PATTERN =
            Pattern.compile("\\b[0-9a-fA-F]{8,}\\b");

    /**
     * Normalizes a message by replacing variable parts with placeholders.
     * Order matters: more specific patterns (UUID, URL, email) are replaced first
     * before the generic number pattern.
     *
     * @param message the raw exception message (may be null)
     * @return a normalized template string, or "&lt;no message&gt;" if the input is null/blank
     */
    static String normalize(String message) {
        if (message == null || message.isBlank()) {
            return "<no message>";
        }

        String result = message;
        result = UUID_PATTERN.matcher(result).replaceAll("<UUID>");
        result = URL_PATTERN.matcher(result).replaceAll("<URL>");
        result = EMAIL_PATTERN.matcher(result).replaceAll("<EMAIL>");
        result = HEX_ID_PATTERN.matcher(result).replaceAll("<HEX>");
        result = NUMBER_PATTERN.matcher(result).replaceAll("<N>");

        return result;
    }
}
