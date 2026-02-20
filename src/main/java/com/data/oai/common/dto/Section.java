package com.data.oai.common.dto;

public record Section(
        String title,
        Integer level,
        String text
) {
    public Section {
        title = (title == null || title.isBlank()) ? "UNTITLED" : title.trim();
        text = (text == null) ? "" : text.trim();
    }
}
