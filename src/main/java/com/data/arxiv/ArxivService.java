package com.data.arxiv;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URL;
import java.util.List;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.nonNull;

@Service
@Slf4j
public class ArxivService {
    @Value("${arxiv.searchUrl}")
    private String arxivSearchUrl;

    public List<Paper> search(ArxivSearchRequest request) {
        String finalQuery;

        if (nonNull(request.getTerms()) && !request.getTerms().isEmpty()) {
            finalQuery = request.getTerms().stream()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(q -> q.contains(":") ? q : "all:" + q)   // optional: auto all:
                    .distinct()
                    .reduce((a, b) -> a + " AND " + b)
                    .orElse("");
        } else {
            finalQuery = "";
        }

        if (finalQuery.isBlank()) {
            throw new IllegalArgumentException("Search query must not be empty");
        }

        try {
            String query = encode(finalQuery, UTF_8);
            String url = arxivSearchUrl + query
                    + "&start=" + request.getStart()
                    + "&max_results=" + request.getLimit();

            log.info("Search initiated for {}", url);

            URL feedUrl = URI.create(url).toURL();

            SyndFeed feed = new SyndFeedInput().build(new XmlReader(feedUrl));

            return feed.getEntries()
                    .stream().map(Paper::mapEntry)
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
