package com.data.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class YouTubeConfig {

    @Bean
    public HttpRequestInitializer youtubeHttpRequestInitializer() {
        return request -> {
            request.setConnectTimeout(30_000);
            request.setReadTimeout(30_000);
        };
    }

    @Bean
    public YouTube youTubeClient(
            HttpRequestInitializer youtubeHttpRequestInitializer,
            @Value("${youtube.application-name:youtube-extractor}") String applicationName
    ) throws Exception {
        return new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(),
                youtubeHttpRequestInitializer
        ).setApplicationName(applicationName).build();
    }
}
