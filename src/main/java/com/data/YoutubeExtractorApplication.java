package com.data;

import com.data.config.properties.*;
import com.data.shared.exception.logging.ExceptionFileLoggerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(value = {
        ModelProperties.class, RagSystemClientProperties.class, EmbeddingProperties.class,
        ArxivOaiProps.class, GrobidProperties.class, ZenodoOaiProps.class, PubmedOaiProps.class,
        GptProperties.class, ArxivSearchProperties.class, OaiProcessingProperties.class,
        QdrantGrpcConfig.class, ExceptionFileLoggerProperties.class, PmcS3Properties.class
        StorageProperties.class
})
public class YoutubeExtractorApplication {

    public static void main(String[] args) {
        SpringApplication.run(YoutubeExtractorApplication.class, args);
    }

}
