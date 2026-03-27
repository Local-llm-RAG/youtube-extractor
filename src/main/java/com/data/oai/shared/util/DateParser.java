package com.data.oai.shared.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility for parsing date strings (ISO instant or ISO local date) to {@link LocalDate}.
 */
public final class DateParser {

    private DateParser() {}

    public static LocalDate parseToLocalDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(raw)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();
        } catch (DateTimeParseException ignored) {
            return LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }
}
