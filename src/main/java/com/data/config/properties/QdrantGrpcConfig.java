package com.data.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "qdrant.grpc")
public class QdrantGrpcConfig {

    private String host = "localhost";
    private Integer port = 6334;
    private String apiKey;
}