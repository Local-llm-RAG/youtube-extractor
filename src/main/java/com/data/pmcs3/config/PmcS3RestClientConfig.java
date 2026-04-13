package com.data.pmcs3.config;

import com.data.config.properties.HttpClientProperties;
import com.data.config.properties.PmcS3Properties;
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

/**
 * Dedicated {@link RestClient} for the PMC S3 pipeline. Uses its own
 * connection pool sized by {@code pmcs3.concurrency} so it does not
 * contend with the OAI-PMH pool.
 */
@Configuration
public class PmcS3RestClientConfig {

    private static final int POOL_HEADROOM = 8;

    @Bean(name = "pmcS3RestClient")
    public RestClient pmcS3RestClient(RestClient.Builder builder, PmcS3Properties props) {
        HttpClientProperties http = props.httpClient();
        int maxConnections = props.concurrency() + POOL_HEADROOM;

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
