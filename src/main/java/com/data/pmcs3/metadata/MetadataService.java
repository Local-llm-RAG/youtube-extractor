package com.data.pmcs3.metadata;

import com.data.pmcs3.client.PmcS3Client;
import com.data.pmcs3.inventory.InventoryEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Fetches and deserializes the per-article JSON metadata accompanying each
 * PMC S3 article. This is the source of truth for license, identifiers, and
 * companion file URLs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PmcS3Client client;

    /**
     * Downloads and parses the JSON metadata for the given inventory entry,
     * or returns {@code null} if the file is missing or cannot be parsed.
     */
    public PubmedArticleMetadata fetchMetadata(InventoryEntry entry) {
        String key = client.metadataKey(entry.pmcId(), entry.version());
        String json = client.downloadText(key);
        if (json == null || json.isBlank()) {
            log.debug("No PMC S3 metadata JSON at key={}", key);
            return null;
        }
        try {
            return MAPPER.readValue(json, PubmedArticleMetadata.class);
        } catch (IOException e) {
            log.warn("Failed to parse PMC S3 metadata at key={}: {}", key, e.getMessage());
            return null;
        }
    }
}
