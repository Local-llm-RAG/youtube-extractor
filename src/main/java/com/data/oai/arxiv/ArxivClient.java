package com.data.oai.arxiv;

import com.data.oai.shared.util.OaiHttpSupport;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Service
public class ArxivClient {

    private final RestClient rest;

    public ArxivClient(@Qualifier("oaiRestClient") RestClient rest) {
        this.rest = rest;
    }

    @Retry(name = "arxiv")
    @RateLimiter(name = "arxiv")
    public byte[] listRecords(String baseUrl, String from, String until,
                              String token, String metadataPrefix) {
        URI uri = OaiHttpSupport.buildListRecordsUri(baseUrl, from, until, token, metadataPrefix);
        return OaiHttpSupport.executeOaiExchange(rest, uri, code -> false, uri.toString());
    }

    @Retry(name = "arxiv")
    @RateLimiter(name = "arxiv")
    public byte[] downloadFile(String url) {
        URI uri = OaiHttpSupport.toEncodedUri(url);
        return OaiHttpSupport.executeOaiExchange(rest, uri, code -> false, url);
    }
}
