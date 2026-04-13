package com.data.pmcs3.pipeline;

import com.data.config.properties.PmcS3Properties;
import com.data.pmcs3.client.PmcS3Client;
import com.data.startup.PostgresAdvisoryLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Scheduled entry point for the PMC S3 pipeline.
 *
 * <p>Uses a Postgres advisory lock so only one node in the cluster runs
 * the batch at a time. The initial run will be long (millions of articles),
 * so per-record progress is persisted by {@link PmcS3Facade} through the
 * tracker: restarts pick up exactly where the previous run stopped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PmcS3ProcessorService {

    /**
     * The PMC inventory bucket anchors its daily folders to 01:00 UTC
     * (not midnight) and typically publishes around 02:55 UTC the following
     * day. The formatter below is only used by the deterministic fallback
     * probe — the primary discovery path lists common prefixes from S3 and
     * is immune to any future schedule changes on the PMC side.
     */
    private static final DateTimeFormatter UTC_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd'T01-00Z'");
    private static final int MANIFEST_FALLBACK_DAYS = 7;

    private final PmcS3Properties props;
    private final PmcS3Facade facade;
    private final PmcS3Client client;
    private final PostgresAdvisoryLock advisoryLock;

    /**
     * Fires on the configured cron expression. Skips gracefully if another
     * node holds the advisory lock.
     */
    @Scheduled(cron = "${pmcs3.cron}")
    public void run() {
        long lockKey = props.advisoryLockKey();
        if (!advisoryLock.tryLock(lockKey)) {
            log.info("PMC S3 scheduled run skipped — advisory lock held by another process");
            return;
        }
        try {
            String manifestKey = findLatestAvailableManifestKey();
            if (manifestKey == null) {
                log.warn("PMC S3 run aborted — no inventory manifest found in the last {} days",
                        MANIFEST_FALLBACK_DAYS);
                return;
            }
            log.info("PMC S3 scheduled run starting. manifestKey={}", manifestKey);
            facade.processBatch(manifestKey);
        } catch (Exception e) {
            log.error("PMC S3 scheduled run failed: {}", e.getMessage(), e);
        } finally {
            advisoryLock.unlock(lockKey);
        }
    }

    /**
     * Finds the most recent published {@code manifest.json} key.
     *
     * <p>Primary strategy: ask S3 directly via {@code ListObjectsV2} under
     * the inventory prefix with {@code delimiter=/} and pick the
     * lexicographically largest day-folder common prefix. This survives any
     * future changes to PMC's publish hour and avoids burning N HTTP probes
     * on 404s.
     *
     * <p>Fallback strategy: if the list call fails or returns no prefixes,
     * deterministically walk backwards from today using the known
     * {@code 01-00Z} convention for up to {@code MANIFEST_FALLBACK_DAYS}.
     */
    private String findLatestAvailableManifestKey() {
        String fromListing = findViaListObjects();
        if (fromListing != null) {
            return fromListing;
        }
        log.warn("PMC S3 ListObjectsV2 discovery returned no prefixes — falling back to deterministic walk-back");
        return findViaDayWalkBack();
    }

    private String findViaListObjects() {
        try {
            String xml = client.listCommonPrefixes(props.inventoryPrefix());
            if (xml == null || xml.isBlank()) {
                return null;
            }
            String key = PmcS3InventoryManifestLocator.findLatestManifestKey(xml);
            if (key == null) {
                return null;
            }
            // Sanity-check that the manifest object itself actually exists.
            byte[] probe = client.downloadBytes(key);
            if (probe == null) {
                log.warn("PMC S3 inventory listing pointed at {} but manifest object not found", key);
                return null;
            }
            return key;
        } catch (Exception e) {
            log.warn("PMC S3 ListObjectsV2 discovery failed: {}", e.getMessage());
            return null;
        }
    }

    private String findViaDayWalkBack() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int d = 0; d < MANIFEST_FALLBACK_DAYS; d++) {
            LocalDate day = today.minusDays(d);
            String key = props.inventoryPrefix() + "/"
                    + day.format(UTC_DAY)
                    + "/manifest.json";
            byte[] probe = client.downloadBytes(key);
            if (probe != null) {
                return key;
            }
            log.debug("PMC S3 no manifest yet for {}", day);
        }
        return null;
    }
}
