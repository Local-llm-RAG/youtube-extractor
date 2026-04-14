package com.data.oai.zenodo;

import com.data.config.properties.ZenodoOaiProps;
import com.data.shared.http.HttpExchangeSupport;
import com.data.shared.exception.HarvestException;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Service
public class ZenodoClient {

    private final RestClient rest;
    private final ZenodoOaiProps props;

    public ZenodoClient(@Qualifier("oaiRestClient") RestClient rest, ZenodoOaiProps props) {
        this.rest = rest;
        this.props = props;
    }

    @Retry(name = "zenodo")
    @RateLimiter(name = "zenodo")
    public byte[] listRecords(String baseUrl, String from, String until,
                              String token, String metadataPrefix) {
        URI uri = HttpExchangeSupport.buildListRecordsUri(baseUrl, from, until, token, metadataPrefix);
        // Zenodo returns 422 for certain OAI error responses that need parsing
        return HttpExchangeSupport.executeExchange(rest, uri, code -> code == 422, uri.toString());
    }

    @Retry(name = "zenodo")
    @RateLimiter(name = "zenodo")
    public ZenodoRecord getRecord(String recordId) {
        URI uri = URI.create(props.apiBaseUrl() + recordId);

        return rest.get()
                .uri(uri)
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status.is2xxSuccessful()) {
                        return res.bodyTo(ZenodoRecord.class);
                    }
                    HttpExchangeSupport.throwIfRetryable(status, "recordId " + recordId);
                    throw new HarvestException(
                            "Zenodo getRecord failed. HTTP " + status.value() + " for recordId " + recordId);
                });
    }

    @Retry(name = "zenodo")
    @RateLimiter(name = "zenodo")
    public byte[] downloadFile(String url) {
        URI uri = HttpExchangeSupport.toEncodedUri(url);
        return HttpExchangeSupport.executeExchange(rest, uri, code -> false, url);
    }
}
