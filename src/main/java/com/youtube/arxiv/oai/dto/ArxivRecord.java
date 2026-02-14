package com.youtube.arxiv.oai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ArxivRecord {
    private String oaiIdentifier;
    private String datestamp;
    private String title;
    private String abstractText;
    private String comments;
    private String journalRef;
    private String doi;
    private String license;
    private final List<String> categories = new ArrayList<>();
    private final List<ArxivAuthor> authors = new ArrayList<>();
    private String arxivId;
    private ArxivPaperDocument document;

    public ArxivAuthor lastAuthor() {
        return authors.getLast();
    }

    public static String extractArxivIdFromOai(String oaiIdentifier) {
        if (oaiIdentifier == null) return null;
        int idx = oaiIdentifier.lastIndexOf(':');
        String id = (idx >= 0) ? oaiIdentifier.substring(idx + 1) : oaiIdentifier;
        return id.replaceAll("v\\d+$", "");
    }
}
