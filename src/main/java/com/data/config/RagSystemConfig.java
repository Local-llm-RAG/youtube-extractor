package com.data.config;

import com.data.config.properties.RagSystemClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class RagSystemConfig {

    @Bean
    RestClient ragRestClient(RagSystemClientProperties props) {
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
    }

    @Bean
    WebClient pythonEmbeddingWebClient(RagSystemClientProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }
}
