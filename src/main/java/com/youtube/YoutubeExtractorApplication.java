package com.youtube;

import com.youtube.config.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(value = {
        ModelProperties.class, RagSystemClientProperties.class, EmbeddingProperties.class,
        ArxivOaiProps.class, GrobidProperties.class, ZenodoOaiProps.class
})
public class YoutubeExtractorApplication {

    public static void main(String[] args) {
        SpringApplication.run(YoutubeExtractorApplication.class, args);
    }

}
