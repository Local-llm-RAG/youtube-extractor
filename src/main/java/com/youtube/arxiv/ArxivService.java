package com.youtube.arxiv;

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

@Service
@Slf4j
public class ArxivService {
    @Value("${arxiv.searchUrL}")
    private String arxivSearchUrl;

    public List<ArxivPaper> search(String searchQuery, int start, int maxResults) {
        try {
            String query = encode(searchQuery, UTF_8);
            String url = arxivSearchUrl + query
                    + "&start=" + start
                    + "&max_results=" + maxResults;

            log.info("Search initiated for {}", url);

            URL feedUrl = URI.create(url).toURL();

            SyndFeed feed = new SyndFeedInput().build(new XmlReader(feedUrl));

            return feed.getEntries()
                    .stream().map(ArxivPaper::mapEntry)
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
