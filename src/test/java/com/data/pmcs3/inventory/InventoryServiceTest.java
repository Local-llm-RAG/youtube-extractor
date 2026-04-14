package com.data.pmcs3.inventory;

import com.data.pmcs3.client.PmcS3Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InventoryService}.
 *
 * <p>Split into two concerns:
 * <ul>
 *   <li>{@link InventoryService#dedupeByPmcIdKeepingHighestVersion} — in-memory
 *       collapse of multi-version inventory rows.</li>
 *   <li>{@link InventoryService#fetchInventory} — JSON manifest parsing +
 *       gzipped CSV row-shape handling via a mocked {@link PmcS3Client}.</li>
 * </ul>
 */
class InventoryServiceTest {

    private static final String MANIFEST_KEY =
            "inventory-reports/pmc-oa-opendata/metadata/2026-04-10T00-00Z/manifest.json";

    // ------------------------------------------------------------------
    // dedupeByPmcIdKeepingHighestVersion
    // ------------------------------------------------------------------

    @Test
    void keepsHigherVersionWhenTwoVersionsOfSamePmcId() {
        InventoryEntry v1 = new InventoryEntry("7744736", 1, "PMC7744736.1");
        InventoryEntry v2 = new InventoryEntry("7744736", 2, "PMC7744736.2");

        List<InventoryEntry> result = InventoryService.dedupeByPmcIdKeepingHighestVersion(List.of(v1, v2));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).version()).isEqualTo(2);
        assertThat(result.get(0).keyBase()).isEqualTo("PMC7744736.2");
    }

    @Test
    void keepsHighestOfThreeVersionsRegardlessOfOrder() {
        InventoryEntry v1 = new InventoryEntry("42", 1, "PMC42.1");
        InventoryEntry v2 = new InventoryEntry("42", 2, "PMC42.2");
        InventoryEntry v3 = new InventoryEntry("42", 3, "PMC42.3");

        // Out of order to prove we don't rely on input ordering.
        List<InventoryEntry> result = InventoryService.dedupeByPmcIdKeepingHighestVersion(List.of(v2, v1, v3));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).version()).isEqualTo(3);
    }

    @Test
    void preservesSingletonUnchanged() {
        InventoryEntry only = new InventoryEntry("10009416", 1, "PMC10009416.1");

        List<InventoryEntry> result = InventoryService.dedupeByPmcIdKeepingHighestVersion(List.of(only));

        assertThat(result).containsExactly(only);
    }

    @Test
    void keepsUnrelatedPmcIdsSideBySide() {
        InventoryEntry a = new InventoryEntry("1", 1, "PMC1.1");
        InventoryEntry b = new InventoryEntry("2", 1, "PMC2.1");
        InventoryEntry c = new InventoryEntry("3", 1, "PMC3.1");

        List<InventoryEntry> result = InventoryService.dedupeByPmcIdKeepingHighestVersion(List.of(a, b, c));

        assertThat(result).hasSize(3);
        assertThat(result).extracting(InventoryEntry::pmcId).containsExactlyInAnyOrder("1", "2", "3");
    }

    @Test
    void dedupesDuplicatesMixedWithSingletons() {
        InventoryEntry dup1v1 = new InventoryEntry("100", 1, "PMC100.1");
        InventoryEntry dup1v2 = new InventoryEntry("100", 2, "PMC100.2");
        InventoryEntry single = new InventoryEntry("200", 1, "PMC200.1");
        InventoryEntry dup2v1 = new InventoryEntry("300", 1, "PMC300.1");
        InventoryEntry dup2v3 = new InventoryEntry("300", 3, "PMC300.3");
        InventoryEntry dup2v2 = new InventoryEntry("300", 2, "PMC300.2");

        List<InventoryEntry> result = InventoryService.dedupeByPmcIdKeepingHighestVersion(
                List.of(dup1v1, dup1v2, single, dup2v1, dup2v3, dup2v2));

        assertThat(result).hasSize(3);
        assertThat(result).extracting(InventoryEntry::pmcId).containsExactlyInAnyOrder("100", "200", "300");
        // Highest version wins for each deduped group.
        assertThat(result).filteredOn(e -> "100".equals(e.pmcId())).singleElement()
                .satisfies(e -> assertThat(e.version()).isEqualTo(2));
        assertThat(result).filteredOn(e -> "300".equals(e.pmcId())).singleElement()
                .satisfies(e -> assertThat(e.version()).isEqualTo(3));
        // Singleton passes through untouched.
        assertThat(result).filteredOn(e -> "200".equals(e.pmcId())).singleElement()
                .satisfies(e -> assertThat(e.version()).isEqualTo(1));
    }

    @Test
    void handlesEmptyAndNullInputs() {
        assertThat(InventoryService.dedupeByPmcIdKeepingHighestVersion(List.of())).isEmpty();
        assertThat(InventoryService.dedupeByPmcIdKeepingHighestVersion(null)).isEmpty();
    }

    // ------------------------------------------------------------------
    // fetchInventory — manifest JSON parsing
    // ------------------------------------------------------------------

    @Test
    void extractsBothDataKeys_whenManifestReferencesTwoFiles() throws IOException {
        PmcS3Client client = mock(PmcS3Client.class);
        String manifest = """
                {
                  "sourceBucket": "pmc-oa-opendata",
                  "files": [
                    {"key": "inventory-reports/data/part-0.csv.gz", "size": 123},
                    {"key": "inventory-reports/data/part-1.csv.gz", "size": 456}
                  ]
                }
                """;
        when(client.downloadText(MANIFEST_KEY)).thenReturn(manifest);
        // Return an empty (but valid) gzipped CSV for both data files — the test
        // cares that BOTH keys were requested, not what comes out of them.
        when(client.downloadBytes("inventory-reports/data/part-0.csv.gz")).thenReturn(gzip(""));
        when(client.downloadBytes("inventory-reports/data/part-1.csv.gz")).thenReturn(gzip(""));

        InventoryService service = new InventoryService(client, new ObjectMapper());
        List<InventoryEntry> result = service.fetchInventory(MANIFEST_KEY);

        // Both data files were fetched (verifies both keys were extracted in order).
        assertThat(result).isEmpty();
        org.mockito.Mockito.verify(client).downloadBytes("inventory-reports/data/part-0.csv.gz");
        org.mockito.Mockito.verify(client).downloadBytes("inventory-reports/data/part-1.csv.gz");
    }

    @Test
    void returnsEmpty_whenManifestFilesArrayIsEmpty() {
        PmcS3Client client = mock(PmcS3Client.class);
        when(client.downloadText(MANIFEST_KEY)).thenReturn("{\"files\": []}");

        InventoryService service = new InventoryService(client, new ObjectMapper());
        List<InventoryEntry> result = service.fetchInventory(MANIFEST_KEY);

        assertThat(result).isEmpty();
        // No data file downloads should have been attempted.
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).downloadBytes(anyString());
    }

    @Test
    void returnsEmpty_whenManifestIsStructurallyValidButMissingFilesField() {
        PmcS3Client client = mock(PmcS3Client.class);
        // Structurally valid JSON, but no "files" field — readTree(...).path("files")
        // returns a MissingNode whose spliterator is empty.
        when(client.downloadText(MANIFEST_KEY)).thenReturn("{\"sourceBucket\": \"pmc-oa-opendata\"}");

        InventoryService service = new InventoryService(client, new ObjectMapper());
        List<InventoryEntry> result = service.fetchInventory(MANIFEST_KEY);

        assertThat(result).isEmpty();
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).downloadBytes(anyString());
    }

    @Test
    void returnsEmpty_whenManifestIsMalformedJson() {
        PmcS3Client client = mock(PmcS3Client.class);
        when(client.downloadText(MANIFEST_KEY)).thenReturn("not json at all");

        InventoryService service = new InventoryService(client, new ObjectMapper());

        // Must not throw — best-effort pipeline semantics.
        List<InventoryEntry> result = service.fetchInventory(MANIFEST_KEY);

        assertThat(result).isEmpty();
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).downloadBytes(anyString());
    }

    // ------------------------------------------------------------------
    // fetchInventory — CSV row shape (no header, bucket col 0, key col 1)
    // ------------------------------------------------------------------

    @Test
    void parsesHeaderlessCsvAndDropsRowsThatDoNotMatchMetadataKeyPattern() throws IOException {
        PmcS3Client client = mock(PmcS3Client.class);
        String manifest = """
                {"files": [{"key": "data/part-0.csv.gz"}]}
                """;
        // Three rows, NO header. Col 0 = bucket, col 1 = key.
        // Row 3's key points at a .txt file and should be rejected by
        // InventoryEntry.fromS3Key, so the final list must have only 2 entries.
        String csv =
                "pmc-oa-opendata,metadata/PMC10009416.1.json\n" +
                "pmc-oa-opendata,metadata/PMC10009417.2.json\n" +
                "pmc-oa-opendata,oa_comm_txt/PMC10009418.5.txt\n";

        when(client.downloadText(MANIFEST_KEY)).thenReturn(manifest);
        when(client.downloadBytes(eq("data/part-0.csv.gz"))).thenReturn(gzip(csv));

        InventoryService service = new InventoryService(client, new ObjectMapper());
        List<InventoryEntry> result = service.fetchInventory(MANIFEST_KEY);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(InventoryEntry::pmcId)
                .containsExactly("10009416", "10009417");
        assertThat(result).extracting(InventoryEntry::version)
                .containsExactly(1, 2);
        assertThat(result).extracting(InventoryEntry::keyBase)
                .containsExactly("PMC10009416.1", "PMC10009417.2");
    }

    @Test
    void parsesQuotedCsvFieldsWithoutSplittingOnInternalCommas() throws IOException {
        PmcS3Client client = mock(PmcS3Client.class);
        String manifest = """
                {"files": [{"key": "data/part-0.csv.gz"}]}
                """;
        // One row where the bucket column is a quoted field containing a comma.
        // If the CSV parser naively split on every comma, the key column would
        // end up as "bar" rather than "metadata/PMC42.1.json" and the row would
        // be discarded — proving CsvMapper is honoring quoted-field semantics.
        String csv = "\"foo,bar\",metadata/PMC42.1.json\n";

        when(client.downloadText(MANIFEST_KEY)).thenReturn(manifest);
        when(client.downloadBytes(eq("data/part-0.csv.gz"))).thenReturn(gzip(csv));

        InventoryService service = new InventoryService(client, new ObjectMapper());
        List<InventoryEntry> result = service.fetchInventory(MANIFEST_KEY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).pmcId()).isEqualTo("42");
        assertThat(result.get(0).version()).isEqualTo(1);
        assertThat(result.get(0).keyBase()).isEqualTo("PMC42.1");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static byte[] gzip(String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(baos)) {
            gz.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }
}
