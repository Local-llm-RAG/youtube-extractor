package com.data.embedding.qdrant;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class QdrantHealthIndicator implements HealthIndicator {

    private final QdrantGrpcClient qdrant;

    public QdrantHealthIndicator(QdrantGrpcClient qdrant) {
        this.qdrant = qdrant;
    }

    @Override
    public Health health() {
        try {
            if (qdrant.healthCheck()) {
                return Health.up().withDetail("qdrant", "SERVING").build();
            }
            return Health.down().withDetail("qdrant", "NOT_SERVING").build();
        } catch (Exception e) {
            return Health.down(e).withDetail("qdrant", "UNREACHABLE").build();
        }
    }
}
