package com.data.arxiv;

import com.data.oai.DataSource;
import com.data.oai.generic.GenericFacade;
import com.data.oai.generic.common.record.RecordRepository;
import com.data.oai.generic.common.tracker.Tracker;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Service
@Slf4j
public class ArxivService {
    @Value("${arxiv.searchUrl}")
    private String arxivSearchUrl;

    private final RecordRepository recordRepository;
    private final GenericFacade genericFacade;

    public ArxivService(RecordRepository recordRepository, GenericFacade genericFacade) {
        this.recordRepository = recordRepository;
        this.genericFacade = genericFacade;
    }

    public List<Paper> search(ArxivSearchRequest request) {
        String finalQuery;

        if (nonNull(request.getTerms()) && !request.getTerms().isEmpty()) {
            finalQuery = request.getTerms().stream()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(q -> q.contains(":") ? q : "all:" + q)
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

            List<Paper> papers = feed.getEntries()
                    .stream()
                    .map(Paper::mapEntry)
                    .toList();

            hydrateMissingFromSearch(papers);

            return papers;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void hydrateMissingFromSearch(List<Paper> papers) {
        List<String> ids = papers.stream()
                .map(p -> ArxivIdExtractor.extract(p.getId(), p.getAbsUrl()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return;
        }

        Set<String> existing = new HashSet<>(recordRepository.findExistingArxivIds(ids, DataSource.ARXIV));

        Set<String> missing = new HashSet<>(ids);
        missing.removeAll(existing);

        if (missing.isEmpty()) {
            log.info("Search hydrate: all {} papers already in DB", ids.size());

            return;
        }

        log.info("Search hydrate: missing {} / {} papers, will populate via OAI+GROBID", missing.size(), ids.size());

        Map<LocalDate, Set<String>> missingByDay = papers.stream()
                .map(p -> new AbstractMap.SimpleEntry<>(
                        dayOf(p),
                        ArxivIdExtractor.extract(p.getId(), p.getAbsUrl())
                ))
                .filter(e -> nonNull(e.getValue()))
                .filter(e -> missing.contains(e.getValue()))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toSet())
                ));

        for (Map.Entry<LocalDate, Set<String>> e : missingByDay.entrySet()) {
            LocalDate day = e.getKey();
            Set<String> onlyIds = e.getValue();

            Tracker tracker = genericFacade.getTracker(day, DataSource.ARXIV);

            if (isNull(tracker)) {
                tracker = Tracker.builder()
                        .dateStart(day)
                        .dateEnd(day.plusDays(1))
                        .dataSource(DataSource.ARXIV)
                        .allPapersForPeriod(0)
                        .processedPapersForPeriod(0)
                        .build();
            }

            genericFacade.processCollectedArxivRecord(tracker, onlyIds);
        }
    }

    private LocalDate dayOf(Paper p) {
        if (nonNull(p.getPublished())) {
            return p.getPublished().atZone(ZoneOffset.UTC).toLocalDate();
        }
        return LocalDate.now(ZoneOffset.UTC);
    }
}