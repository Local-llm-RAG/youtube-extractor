package com.data.health;

import com.data.embedding.QdrantGrpsClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class QdrantHealthIndicator implements HealthIndicator {

    private final QdrantGrpsClient qdrant;

    public QdrantHealthIndicator(QdrantGrpsClient qdrant) {
        this.qdrant = qdrant;
    }

    @Override
    public Health health() {
        try {
            if (qdrant.healthCheck()) {
                return Health.up().withDetail("qdrant", "SERVING").build();
            }
            return Health.down().withDetail("qdrant", health().getDetails()).build();
        } catch (Exception e) {
            return Health.down(e).withDetail("qdrant", "UNREACHABLE").build();
        }
    }
}
