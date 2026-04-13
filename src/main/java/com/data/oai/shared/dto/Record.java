package com.data.oai.shared.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Record {
    private String externalIdentifier;
    private String datestamp;
    private String comments;
    private String journalRef;
    private String doi;
    private String license;
    private final List<String> categories = new ArrayList<>();
    private final List<Author> authors = new ArrayList<>();
    private String sourceId;
    private String language;
    private String title;
    private String abstractText;

    public Author lastAuthor() {
        return authors.getLast();
    }

    public static String extractIdFromOai(String externalIdentifier) {
        if (externalIdentifier == null || externalIdentifier.isBlank()) {
            return null;
        }
        String[] parts = externalIdentifier.split(":", 3);
        String localId = parts.length == 3 ? parts[2] : externalIdentifier;
        if (localId.matches(".*v\\d+$")) {
            return localId.replaceAll("v\\d+$", "");
        }

        return localId;
    }
}
