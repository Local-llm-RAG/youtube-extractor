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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link PmcS3Facade} routes each skip scenario to the
 * correctly-typed tracker counter. The facade owns the mapping from runtime
 * condition (null metadata, bad license, duplicate, etc.) to the persisted
 * per-reason counter that powers the operator-facing skip breakdown log.
 */
class PmcS3FacadeSkipReasonTest {

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

        PmcS3Properties props = new PmcS3Properties(
                "https://example/pmc",
                "inventory/",
                10, // batchSize
                2,  // concurrency
                8,
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

        ReflectionTestUtils.setField(facade, "pmcS3Executor", new SameThreadExecutorService());
        ReflectionTestUtils.invokeMethod(facade, "init");
    }

    @Test
    void nullMetadata_incrementsMissingMetadataCounter() {
        PmcS3Tracker tracker = newTracker();
        stubDiscovery("miss-meta", tracker, List.of(entry("1")));
        when(metadataService.fetchMetadata(any())).thenReturn(null);

        facade.processBatch("miss-meta");

        verify(trackerService, times(1)).incrementSkippedMissingMetadata(tracker.getId());
        verify(trackerService, never()).incrementSkippedLicense(tracker.getId());
        verify(trackerService, never()).incrementSkippedMissingJats(tracker.getId());
        verify(trackerService, never()).incrementSkippedDuplicate(tracker.getId());
        verify(trackerService, never()).incrementSkippedIo(tracker.getId());
        verify(trackerService, never()).incrementProcessed(tracker.getId());
    }

    @Test
    void nonCommercialLicense_incrementsLicenseCounter() {
        PmcS3Tracker tracker = newTracker();
        stubDiscovery("lic", tracker, List.of(entry("1")));
        PubmedArticleMetadata meta = metaWithLicense("CC BY-NC");
        when(metadataService.fetchMetadata(any())).thenReturn(meta);

        facade.processBatch("lic");

        verify(trackerService, times(1)).incrementSkippedLicense(tracker.getId());
        verify(trackerService, never()).incrementSkippedMissingJats(tracker.getId());
        verify(trackerService, never()).incrementProcessed(tracker.getId());
    }

    @Test
    void metadataWithoutXmlUrl_incrementsMissingJatsCounter() {
        PmcS3Tracker tracker = newTracker();
        stubDiscovery("no-xml-url", tracker, List.of(entry("1")));
        // CC0 license passes the filter, but xml_url is null → early skip.
        PubmedArticleMetadata meta = new PubmedArticleMetadata(
                "PMC1", "PMID1", null, "CC0", null,
                "2026-01-01", "Title", "Journal", null, null, null
        );
        when(metadataService.fetchMetadata(any())).thenReturn(meta);

        facade.processBatch("no-xml-url");

        verify(trackerService, times(1)).incrementSkippedMissingJats(tracker.getId());
        // Critical: the early short-circuit must avoid the S3 JATS GET.
        verify(client, never()).downloadText(anyString());
        verify(trackerService, never()).incrementProcessed(tracker.getId());
    }

    @Test
    void blankJatsDownload_incrementsMissingJatsCounter() {
        PmcS3Tracker tracker = newTracker();
        stubDiscovery("blank-jats", tracker, List.of(entry("1")));
        PubmedArticleMetadata meta = metaWithLicense("CC0");
        when(metadataService.fetchMetadata(any())).thenReturn(meta);
        when(client.articleKey(anyString(), eq(1), eq("xml"))).thenReturn("xml-key");
        when(client.downloadText("xml-key")).thenReturn("   "); // blank

        facade.processBatch("blank-jats");

        verify(trackerService, times(1)).incrementSkippedMissingJats(tracker.getId());
        verify(trackerService, never()).incrementProcessed(tracker.getId());
    }

    @Test
    void duplicateOnPersist_incrementsDuplicateCounter() {
        PmcS3Tracker tracker = newTracker();
        stubDiscovery("dup", tracker, List.of(entry("1")));
        stubHappyPathUpToPersist();
        doThrow(new DataIntegrityViolationException("dup source_identifier"))
                .when(paperInternalService).persistState(any(), any(), any(), anyString());

        facade.processBatch("dup");

        verify(trackerService, times(1)).incrementSkippedDuplicate(tracker.getId());
        verify(trackerService, never()).incrementSkippedIo(tracker.getId());
        verify(trackerService, never()).incrementProcessed(tracker.getId());
    }

    @Test
    void genericExceptionOnPersist_incrementsIoCounter() {
        PmcS3Tracker tracker = newTracker();
        stubDiscovery("io", tracker, List.of(entry("1")));
        stubHappyPathUpToPersist();
        doThrow(new RuntimeException("boom"))
                .when(paperInternalService).persistState(any(), any(), any(), anyString());

        facade.processBatch("io");

        verify(trackerService, times(1)).incrementSkippedIo(tracker.getId());
        verify(trackerService, never()).incrementSkippedDuplicate(tracker.getId());
        verify(trackerService, never()).incrementProcessed(tracker.getId());
    }

    // --- helpers ---------------------------------------------------------

    private void stubDiscovery(String manifestKey, PmcS3Tracker tracker, List<InventoryEntry> entries) {
        when(trackerService.getOrCreate(manifestKey)).thenReturn(tracker);
        when(inventoryService.fetchInventory(manifestKey)).thenReturn(entries);
        when(paperInternalService.findAllSourceIdsByDataSource(DataSource.PMC_S3))
                .thenReturn(List.of());
        // Fresh snapshot for the skip-breakdown log line at end of batch.
        when(trackerService.findById(tracker.getId())).thenReturn(Optional.of(tracker));
    }

    /**
     * Makes the pipeline pass metadata/license/JATS gates so tests that want
     * to exercise persist-time failures (duplicate, generic) actually reach
     * {@link PaperInternalService#persistState}.
     */
    private void stubHappyPathUpToPersist() {
        PubmedArticleMetadata meta = metaWithLicense("CC0");
        when(metadataService.fetchMetadata(any())).thenReturn(meta);
        when(client.articleKey(anyString(), eq(1), eq("xml"))).thenReturn("xml-key");
        when(client.articleKey(anyString(), eq(1), eq("txt"))).thenReturn("txt-key");
        when(client.articleKey(anyString(), eq(1), eq("pdf"))).thenReturn("pdf-key");
        String jats = "<article><front><article-meta></article-meta></front>"
                + "<body><sec><title>S</title><p>content</p></sec></body></article>";
        when(client.downloadText("xml-key")).thenReturn(jats);
        when(client.downloadText("txt-key")).thenReturn("raw body text");
        when(client.urlFor(anyString())).thenReturn("https://pmc/pdf");
    }

    private static PubmedArticleMetadata metaWithLicense(String licenseCode) {
        return new PubmedArticleMetadata(
                "PMC1", "PMID1", "10.1/doi", licenseCode,
                "https://creativecommons.org/publicdomain/zero/1.0/",
                "2026-01-01", "Title", "Journal", "https://pmc/pdf", null, "https://pmc/xml"
        );
    }

    private static InventoryEntry entry(String pmcId) {
        return new InventoryEntry(pmcId, 1, "PMC" + pmcId + ".1");
    }

    private static PmcS3Tracker newTracker() {
        PmcS3Tracker t = new PmcS3Tracker();
        t.setId(1L);
        t.setBatchId("test");
        t.setSkippedLicense(0);
        t.setSkippedMissingMetadata(0);
        t.setSkippedMissingJats(0);
        t.setSkippedDuplicate(0);
        t.setSkippedIo(0);
        t.setSkippedInterrupted(0);
        return t;
    }

    private static final class SameThreadExecutorService extends AbstractExecutorService {
        private volatile boolean shutdown = false;

        @Override public void execute(Runnable command) { command.run(); }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }
}
