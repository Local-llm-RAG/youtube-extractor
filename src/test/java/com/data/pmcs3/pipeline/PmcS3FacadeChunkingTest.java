package com.data.pmcs3.pipeline;

import com.data.config.properties.HttpClientProperties;
import com.data.config.properties.PmcS3Properties;
import com.data.oai.persistence.PaperInternalService;
import com.data.pmcs3.client.PmcS3Client;
import com.data.pmcs3.inventory.InventoryEntry;
import com.data.pmcs3.inventory.InventoryService;
import com.data.pmcs3.metadata.PubmedArticleMetadata;
import com.data.pmcs3.metadata.MetadataService;
import com.data.pmcs3.persistence.PmcS3TrackerService;
import com.data.pmcs3.persistence.entity.PmcS3Tracker;
import com.data.shared.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that verify {@link PmcS3Facade} dispatches work in bounded chunks
 * rather than materializing all futures eagerly, and that every unprocessed
 * entry is processed exactly once regardless of chunking.
 */
class PmcS3FacadeChunkingTest {

    private InventoryService inventoryService;
    private MetadataService metadataService;
    private PmcS3Client client;
    private PmcS3TrackerService trackerService;
    private PaperInternalService paperInternalService;
    private PmcS3Facade facade;

    @BeforeEach
    void setUp() {
        inventoryService = mock(InventoryService.class);
        metadataService = mock(MetadataService.class);
        client = mock(PmcS3Client.class);
        trackerService = mock(PmcS3TrackerService.class);
        paperInternalService = mock(PaperInternalService.class);

        // Small batchSize / concurrency forces multi-chunk dispatch in the big test.
        PmcS3Properties props = new PmcS3Properties(
                "https://example/pmc",
                "inventory/",
                2,  // batchSize
                2,  // concurrency
                8,  // queue (unused here)
                "0 17 3 * * *",
                42L,
                new HttpClientProperties(10, 30, 60, null)
        );

        facade = new PmcS3Facade(
                client,
                inventoryService,
                metadataService,
                trackerService,
                paperInternalService,
                props
        );

        // Run tasks inline on the calling thread so the test is deterministic
        // and doesn't depend on the real pmcS3Executor bean.
        ReflectionTestUtils.setField(facade, "pmcS3Executor", new SameThreadExecutorService());
        // PmcS3Facade normally initializes this via @PostConstruct, but since
        // we instantiate directly (no Spring), invoke init() by hand.
        ReflectionTestUtils.invokeMethod(facade, "init");
    }

    @Test
    void emptyUnprocessedList_stillMarksBatchCompleted() {
        PmcS3Tracker tracker = newTracker();
        when(trackerService.getOrCreate("empty")).thenReturn(tracker);
        when(inventoryService.fetchInventory("empty")).thenReturn(List.of());
        when(paperInternalService.findAllSourceIdsByDataSource(DataSource.PMC_S3))
                .thenReturn(List.of());

        facade.processBatch("empty");

        verify(trackerService).updateDiscovered(tracker.getId(), 0);
        verify(trackerService).markCompleted(tracker.getId());
        verify(metadataService, never()).fetchMetadata(any());
    }

    @Test
    void sizeLessThanBatchSize_processesSingleChunk() {
        PmcS3Tracker tracker = newTracker();
        when(trackerService.getOrCreate("small")).thenReturn(tracker);
        // batchSize=2, one entry → one chunk sized 1.
        List<InventoryEntry> entries = List.of(entry("1"));
        stubPipeline("small", tracker, entries);

        facade.processBatch("small");

        verify(metadataService, times(1)).fetchMetadata(any());
        verify(trackerService, times(1)).incrementProcessed(tracker.getId());
        verify(trackerService).markCompleted(tracker.getId());
    }

    @Test
    void sizeEqualsBatchSize_processesSingleFullChunk() {
        PmcS3Tracker tracker = newTracker();
        when(trackerService.getOrCreate("exact")).thenReturn(tracker);
        // batchSize=2, two entries → one chunk sized 2.
        List<InventoryEntry> entries = List.of(entry("1"), entry("2"));
        stubPipeline("exact", tracker, entries);

        facade.processBatch("exact");

        verify(metadataService, times(2)).fetchMetadata(any());
        verify(trackerService, times(2)).incrementProcessed(tracker.getId());
        verify(trackerService).markCompleted(tracker.getId());
    }

    @Test
    void sizeGreaterThanBatchSize_processesAllEntriesAcrossMultipleChunks() {
        PmcS3Tracker tracker = newTracker();
        when(trackerService.getOrCreate("big")).thenReturn(tracker);
        // batchSize=2, five entries → three chunks sized 2, 2, 1.
        List<InventoryEntry> entries = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> entry(Integer.toString(i)))
                .toList();
        stubPipeline("big", tracker, entries);

        facade.processBatch("big");

        verify(metadataService, times(5)).fetchMetadata(any());
        verify(trackerService, times(5)).incrementProcessed(tracker.getId());
        verify(trackerService).markCompleted(tracker.getId());
        // Sanity: none skipped (all metadata non-null, license CC0, all keys resolved).
        verify(trackerService, never()).incrementSkippedLicense(tracker.getId());
        verify(trackerService, never()).incrementSkippedMissingMetadata(tracker.getId());
        verify(trackerService, never()).incrementSkippedMissingJats(tracker.getId());
        verify(trackerService, never()).incrementSkippedDuplicate(tracker.getId());
        verify(trackerService, never()).incrementSkippedIo(tracker.getId());
        verify(trackerService, never()).incrementSkippedInterrupted(tracker.getId());
    }

    // --- helpers ---------------------------------------------------------

    private static InventoryEntry entry(String pmcId) {
        return new InventoryEntry(pmcId, 1, "PMC" + pmcId + ".1");
    }

    private static PmcS3Tracker newTracker() {
        PmcS3Tracker t = new PmcS3Tracker();
        t.setId(1L);
        t.setBatchId("test");
        return t;
    }

    /**
     * Stubs out inventory discovery plus every per-article dependency with
     * benign, non-null responses so {@link PmcS3Facade#processBatch} reaches
     * {@code trackerService.incrementProcessed(...)} for every entry.
     */
    private void stubPipeline(String manifestKey, PmcS3Tracker tracker, List<InventoryEntry> entries) {
        when(inventoryService.fetchInventory(manifestKey)).thenReturn(entries);
        when(paperInternalService.findAllSourceIdsByDataSource(DataSource.PMC_S3))
                .thenReturn(List.of());

        // Every entry gets a CC0 article that the pipeline will happily process.
        // xml_url must be non-blank so the early author-manuscript shortcut
        // in PmcS3Facade.processOneInternal does not skip the record.
        PubmedArticleMetadata meta = new PubmedArticleMetadata(
                "PMC1", "PMID1", "10.1/doi", "CC0", "https://creativecommons.org/publicdomain/zero/1.0/",
                "2026-01-01", "Title", "Journal", "https://pmc/pdf", null, "https://pmc/xml"
        );
        when(metadataService.fetchMetadata(any())).thenReturn(meta);

        // Per-article asset keys; values are arbitrary — facade only checks non-blank.
        when(client.articleKey(anyString(), eq(1), eq("xml"))).thenReturn("xml-key");
        when(client.articleKey(anyString(), eq(1), eq("txt"))).thenReturn("txt-key");
        when(client.articleKey(anyString(), eq(1), eq("pdf"))).thenReturn("pdf-key");
        // Minimal JATS XML with an article-meta stub so JatsParser doesn't NPE.
        String jats = "<article><front><article-meta></article-meta></front>"
                + "<body><sec><title>S</title><p>content</p></sec></body></article>";
        when(client.downloadText("xml-key")).thenReturn(jats);
        when(client.downloadText("txt-key")).thenReturn("raw body text");
        when(client.urlFor(anyString())).thenReturn("https://pmc/pdf");
    }

    /**
     * Minimal {@link ExecutorService} that runs tasks synchronously on the
     * calling thread — keeps the test deterministic and avoids bringing up
     * the real {@code pmcS3Executor} bean.
     */
    private static final class SameThreadExecutorService extends AbstractExecutorService {
        private volatile boolean shutdown = false;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
