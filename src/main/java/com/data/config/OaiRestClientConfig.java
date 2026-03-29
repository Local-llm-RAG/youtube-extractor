package com.data.config;

import com.data.config.properties.GrobidProperties;
import com.data.config.properties.HttpClientProperties;
import com.data.config.properties.OaiProcessingProperties;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OaiRestClientConfig {

    private static final int POOL_HEADROOM = 2;

    @Bean(name = "grobidRestClient")
    public RestClient grobidRestClient(RestClient.Builder builder, GrobidProperties grobidProps,
                                       OaiProcessingProperties oaiProps) {
        return buildRestClient(builder, oaiProps.concurrency(), grobidProps.httpClient());
    }

    @Bean(name = "oaiRestClient")
    public RestClient oaiRestClient(RestClient.Builder builder, OaiProcessingProperties oaiProps) {
        return buildRestClient(builder, oaiProps.concurrency() + POOL_HEADROOM, oaiProps.httpClient());
    }

    private RestClient buildRestClient(RestClient.Builder builder, int maxConnections,
                                       HttpClientProperties http) {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(maxConnections);
        cm.setDefaultMaxPerRoute(maxConnections);
        if (http.validateAfterInactivitySeconds() != null) {
            cm.setValidateAfterInactivity(TimeValue.ofSeconds(http.validateAfterInactivitySeconds()));
        }

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(http.connectTimeoutSeconds()))
                .setResponseTimeout(Timeout.ofSeconds(http.responseTimeoutSeconds()))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .evictIdleConnections(TimeValue.ofSeconds(http.idleEvictionSeconds()))
                .disableAutomaticRetries()
                .build();

        return builder
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }
}
