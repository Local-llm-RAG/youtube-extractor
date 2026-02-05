package com.youtube.arxiv;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndLink;
import com.rometools.rome.feed.synd.SyndPerson;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Getter
@Setter
@Accessors(chain = true)
public class ArxivPaper {
    private String id;
    private String title;
    private String summary;
    private Instant published;
    private List<String> authors;
    private String absUrl;
    private String pdfUrl;

    public static ArxivPaper mapEntry(SyndEntry entry) {
        String id = entry.getUri();
        String title = normalize(entry.getTitle());
        String summary = nonNull(entry.getDescription()) ? normalize(entry.getDescription().getValue()) : "";

        Instant published = nonNull(entry.getPublishedDate()) ? entry.getPublishedDate().toInstant() : null;

        List<String> authors = entry.getAuthors().stream()
                .map(SyndPerson::getName)
                .filter(s -> nonNull(s) && !s.isBlank())
                .toList();

        String absUrl = null;
        String pdfUrl = null;

        if (nonNull(entry.getLinks())) {
            for (SyndLink link : entry.getLinks()) {
                String href = link.getHref();
                if (isNull(href) || href.isBlank()) {
                    continue;
                }

                if ("application/pdf".equalsIgnoreCase(link.getType())) {
                    pdfUrl = href;
                } else if ("alternate".equalsIgnoreCase(link.getRel())) {
                    absUrl = href;
                }
            }
        }

        // Fallbacks: sometimes absUrl can be inferred from id
        if (isNull(absUrl) && nonNull(id) && id.contains("arxiv.org/abs/")) {
            absUrl = id;
        }

        if (isNull(pdfUrl) && nonNull(absUrl) && absUrl.contains("/abs/")) {
            pdfUrl = absUrl.replace("/abs/", "/pdf/") + ".pdf";
        }

        return new ArxivPaper()
                .setId(id)
                .setTitle(title)
                .setSummary(summary)
                .setPublished(published)
                .setAuthors(authors)
                .setAbsUrl(absUrl)
                .setPdfUrl(pdfUrl);
    }

    private static String normalize(String s) {
        return isNull(s) ? "" : s.replaceAll("\\s+", " ").trim();
    }
}
