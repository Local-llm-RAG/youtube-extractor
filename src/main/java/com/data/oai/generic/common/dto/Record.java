package com.data.oai.generic.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Record {
    private String oaiIdentifier;
    private String datestamp;
    private String comments;
    private String journalRef;
    private String doi;
    private String license;
    private final List<String> categories = new ArrayList<>();
    private final List<Author> authors = new ArrayList<>();
    private String sourceId;
    private String language;

    public Author lastAuthor() {
        return authors.getLast();
    }

    public static String extractIdFromOai(String oaiIdentifier) {
        if (oaiIdentifier == null || oaiIdentifier.isBlank()) {
            return null;
        }
        String[] parts = oaiIdentifier.split(":", 3);
        String localId = parts.length == 3 ? parts[2] : oaiIdentifier;
        if (localId.matches(".*v\\d+$")) {
            return localId.replaceAll("v\\d+$", "");
        }

        return localId;
    }
}
