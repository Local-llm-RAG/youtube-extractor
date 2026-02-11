package com.youtube.external.rest.arxiv.dto;

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

    // new
    private String arxivId;
    private PaperDocument document;
    public ArxivAuthor lastAuthor() {
        return authors.get(authors.size() - 1);
    }
}
