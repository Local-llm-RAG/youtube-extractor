package com.data.pmcs3.pipeline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PmcS3InventoryManifestLocatorTest {

    @Test
    void returnsNullForNullInput() {
        assertThat(PmcS3InventoryManifestLocator.findLatestManifestKey(null)).isNull();
    }

    @Test
    void returnsNullForBlankInput() {
        assertThat(PmcS3InventoryManifestLocator.findLatestManifestKey("   ")).isNull();
    }

    @Test
    void returnsNullWhenNoCommonPrefixes() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <Name>pmc-oa-opendata</Name>
                  <Prefix>inventory-reports/pmc-oa-opendata/metadata/</Prefix>
                  <KeyCount>0</KeyCount>
                  <IsTruncated>false</IsTruncated>
                </ListBucketResult>
                """;
        assertThat(PmcS3InventoryManifestLocator.findLatestManifestKey(xml)).isNull();
    }

    @Test
    void picksLatestByIsoSortOrder() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <Name>pmc-oa-opendata</Name>
                  <Prefix>inventory-reports/pmc-oa-opendata/metadata/</Prefix>
                  <Delimiter>/</Delimiter>
                  <KeyCount>3</KeyCount>
                  <IsTruncated>false</IsTruncated>
                  <CommonPrefixes>
                    <Prefix>inventory-reports/pmc-oa-opendata/metadata/2026-04-09T01-00Z/</Prefix>
                  </CommonPrefixes>
                  <CommonPrefixes>
                    <Prefix>inventory-reports/pmc-oa-opendata/metadata/2026-04-11T01-00Z/</Prefix>
                  </CommonPrefixes>
                  <CommonPrefixes>
                    <Prefix>inventory-reports/pmc-oa-opendata/metadata/2026-04-10T01-00Z/</Prefix>
                  </CommonPrefixes>
                </ListBucketResult>
                """;

        String key = PmcS3InventoryManifestLocator.findLatestManifestKey(xml);

        assertThat(key).isEqualTo(
                "inventory-reports/pmc-oa-opendata/metadata/2026-04-11T01-00Z/manifest.json");
    }

    @Test
    void toleratesOrderingIndependentOfDocumentOrder() {
        // Latest appears first in document order — still picked.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ListBucketResult>
                  <CommonPrefixes>
                    <Prefix>inventory-reports/pmc-oa-opendata/metadata/2026-12-31T01-00Z/</Prefix>
                  </CommonPrefixes>
                  <CommonPrefixes>
                    <Prefix>inventory-reports/pmc-oa-opendata/metadata/2026-01-01T01-00Z/</Prefix>
                  </CommonPrefixes>
                </ListBucketResult>
                """;

        String key = PmcS3InventoryManifestLocator.findLatestManifestKey(xml);

        assertThat(key).isEqualTo(
                "inventory-reports/pmc-oa-opendata/metadata/2026-12-31T01-00Z/manifest.json");
    }

    @Test
    void toleratesAlternativePublishHour() {
        // Defensive: if PMC ever switches to a non-01-00Z hour, string ordering
        // within the same day still works as long as the hour component is
        // consistent within the window.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ListBucketResult>
                  <CommonPrefixes>
                    <Prefix>inventory-reports/pmc-oa-opendata/metadata/2026-04-10T01-00Z/</Prefix>
                  </CommonPrefixes>
                  <CommonPrefixes>
                    <Prefix>inventory-reports/pmc-oa-opendata/metadata/2026-04-11T03-00Z/</Prefix>
                  </CommonPrefixes>
                </ListBucketResult>
                """;

        String key = PmcS3InventoryManifestLocator.findLatestManifestKey(xml);

        assertThat(key).isEqualTo(
                "inventory-reports/pmc-oa-opendata/metadata/2026-04-11T03-00Z/manifest.json");
    }

    @Test
    void stripsTrailingSlashBeforeAppendingManifestFilename() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ListBucketResult>
                  <CommonPrefixes>
                    <Prefix>inventory-reports/pmc-oa-opendata/metadata/2026-04-11T01-00Z/</Prefix>
                  </CommonPrefixes>
                </ListBucketResult>
                """;

        String key = PmcS3InventoryManifestLocator.findLatestManifestKey(xml);

        // No double slash before manifest.json.
        assertThat(key).doesNotContain("//manifest.json");
        assertThat(key).endsWith("/manifest.json");
    }

    @Test
    void ignoresWhitespaceOnlyPrefixEntries() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ListBucketResult>
                  <CommonPrefixes>
                    <Prefix>   </Prefix>
                  </CommonPrefixes>
                  <CommonPrefixes>
                    <Prefix>inventory-reports/pmc-oa-opendata/metadata/2026-04-11T01-00Z/</Prefix>
                  </CommonPrefixes>
                </ListBucketResult>
                """;

        String key = PmcS3InventoryManifestLocator.findLatestManifestKey(xml);

        assertThat(key).isEqualTo(
                "inventory-reports/pmc-oa-opendata/metadata/2026-04-11T01-00Z/manifest.json");
    }

    @Test
    void skipsHivePartitionSiblingPrefix() {
        // S3 Inventory stores a "hive/" partition metadata folder alongside
        // the day folders. It must be filtered out or the locator ends up
        // pointing at a non-existent manifest inside hive/.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ListBucketResult>
                  <CommonPrefixes>
                    <Prefix>inventory-reports/pmc-oa-opendata/metadata/hive/</Prefix>
                  </CommonPrefixes>
                  <CommonPrefixes>
                    <Prefix>inventory-reports/pmc-oa-opendata/metadata/2026-04-11T01-00Z/</Prefix>
                  </CommonPrefixes>
                </ListBucketResult>
                """;

        String key = PmcS3InventoryManifestLocator.findLatestManifestKey(xml);

        assertThat(key).isEqualTo(
                "inventory-reports/pmc-oa-opendata/metadata/2026-04-11T01-00Z/manifest.json");
    }

    @Test
    void returnsNullWhenOnlyHivePrefixPresent() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ListBucketResult>
                  <CommonPrefixes>
                    <Prefix>inventory-reports/pmc-oa-opendata/metadata/hive/</Prefix>
                  </CommonPrefixes>
                </ListBucketResult>
                """;

        assertThat(PmcS3InventoryManifestLocator.findLatestManifestKey(xml)).isNull();
    }

    @Test
    void rejectsMalformedTimestampLikeSegments() {
        // Looks date-ish but doesn't match YYYY-MM-DDTHH-MMZ exactly.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ListBucketResult>
                  <CommonPrefixes>
                    <Prefix>inventory-reports/pmc-oa-opendata/metadata/2026-4-11T1-00Z/</Prefix>
                  </CommonPrefixes>
                  <CommonPrefixes>
                    <Prefix>inventory-reports/pmc-oa-opendata/metadata/2026-04-11/</Prefix>
                  </CommonPrefixes>
                  <CommonPrefixes>
                    <Prefix>inventory-reports/pmc-oa-opendata/metadata/2026-04-11T01-00Z/</Prefix>
                  </CommonPrefixes>
                </ListBucketResult>
                """;

        String key = PmcS3InventoryManifestLocator.findLatestManifestKey(xml);

        assertThat(key).isEqualTo(
                "inventory-reports/pmc-oa-opendata/metadata/2026-04-11T01-00Z/manifest.json");
    }
}
