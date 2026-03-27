package com.data.oai.pubmed;

import com.data.config.properties.PubmedOaiProps;
import com.data.oai.shared.util.OaiHttpSupport;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Service
public class PubmedClient {

    private static final String NCBI_FTP_PREFIX = "ftp://ftp.ncbi.nlm.nih.gov/";
    private static final String NCBI_HTTPS_PREFIX = "https://ftp.ncbi.nlm.nih.gov/";

    private final RestClient rest;
    private final PubmedOaiProps props;

    public PubmedClient(@Qualifier("oaiRestClient") RestClient rest, PubmedOaiProps props) {
        this.rest = rest;
        this.props = props;
    }

    @Retry(name = "pubmed")
    @RateLimiter(name = "pubmed")
    public byte[] listRecords(String baseUrl, String from, String until,
                              String token, String metadataPrefix, String set) {
        URI uri = OaiHttpSupport.buildListRecordsUri(baseUrl, from, until, token, metadataPrefix, set);
        // PMC returns 404 when no records exist for a date range — treat as null body
        return OaiHttpSupport.executeOaiExchange(rest, uri, code -> code == 404, uri.toString());
    }

    @Retry(name = "pubmed")
    @RateLimiter(name = "pubmed")
    public byte[] fetchOaLinks(String pmcId) {
        URI uri = URI.create(props.oaServiceUrl() + "?id=" + pmcId);
        return OaiHttpSupport.executeOaiExchange(rest, uri, code -> false, pmcId);
    }

    @Retry(name = "pubmed")
    @RateLimiter(name = "pubmed")
    public byte[] downloadPdf(String url) {
        String httpsUrl = url.replace(NCBI_FTP_PREFIX, NCBI_HTTPS_PREFIX);
        URI uri = URI.create(httpsUrl);
        return OaiHttpSupport.executeOaiExchange(rest, uri, code -> false, url);
    }
}
