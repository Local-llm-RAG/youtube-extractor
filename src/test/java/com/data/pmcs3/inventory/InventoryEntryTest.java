package com.data.pmcs3.inventory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryEntryTest {

    @Test
    void parsesFlatMetadataKey() {
        InventoryEntry entry = InventoryEntry.fromS3Key("metadata/PMC10009416.1.json");
        assertThat(entry).isNotNull();
        assertThat(entry.pmcId()).isEqualTo("10009416");
        assertThat(entry.version()).isEqualTo(1);
        assertThat(entry.keyBase()).isEqualTo("PMC10009416.1");
    }

    @Test
    void parsesMultiDigitVersion() {
        InventoryEntry entry = InventoryEntry.fromS3Key("metadata/PMC42.17.json");
        assertThat(entry).isNotNull();
        assertThat(entry.pmcId()).isEqualTo("42");
        assertThat(entry.version()).isEqualTo(17);
        assertThat(entry.keyBase()).isEqualTo("PMC42.17");
    }

    @Test
    void parsesLargePmcId() {
        InventoryEntry entry = InventoryEntry.fromS3Key("metadata/PMC6467555.1.json");
        assertThat(entry).isNotNull();
        assertThat(entry.pmcId()).isEqualTo("6467555");
        assertThat(entry.version()).isEqualTo(1);
    }

    @Test
    void rejectsKeysOutsideMetadataPrefix() {
        // Per-article directory path — not the inventory shape.
        assertThat(InventoryEntry.fromS3Key("PMC10009416.1/PMC10009416.1.json")).isNull();
        assertThat(InventoryEntry.fromS3Key("PMC10009416.1/PMC10009416.1.xml")).isNull();
        // No "metadata/" prefix at all.
        assertThat(InventoryEntry.fromS3Key("PMC10009416.1.json")).isNull();
    }

    @Test
    void rejectsNonJsonKeys() {
        assertThat(InventoryEntry.fromS3Key("metadata/PMC10009416.1.xml")).isNull();
        assertThat(InventoryEntry.fromS3Key("metadata/PMC10009416.1.txt")).isNull();
        assertThat(InventoryEntry.fromS3Key("metadata/PMC10009416.1.pdf")).isNull();
    }

    @Test
    void rejectsNestedPathsUnderMetadata() {
        // Metadata dir is flat — any further slash is rejected.
        assertThat(InventoryEntry.fromS3Key("metadata/sub/PMC10009416.1.json")).isNull();
    }

    @Test
    void rejectsMissingVersionComponent() {
        assertThat(InventoryEntry.fromS3Key("metadata/PMC10009416.json")).isNull();
        assertThat(InventoryEntry.fromS3Key("metadata/PMC10009416..json")).isNull();
    }

    @Test
    void rejectsNonNumericIdOrVersion() {
        assertThat(InventoryEntry.fromS3Key("metadata/PMCabcd.1.json")).isNull();
        assertThat(InventoryEntry.fromS3Key("metadata/PMC10009416.v1.json")).isNull();
        assertThat(InventoryEntry.fromS3Key("metadata/PMC10009416.1a.json")).isNull();
    }

    @Test
    void rejectsBlankAndNullInput() {
        assertThat(InventoryEntry.fromS3Key(null)).isNull();
        assertThat(InventoryEntry.fromS3Key("")).isNull();
        assertThat(InventoryEntry.fromS3Key("   ")).isNull();
    }

    @Test
    void rejectsManifestAndOtherInventoryFiles() {
        assertThat(InventoryEntry.fromS3Key("inventory-reports/manifest.json")).isNull();
        assertThat(InventoryEntry.fromS3Key("metadata/manifest.json")).isNull();
        assertThat(InventoryEntry.fromS3Key("metadata/hive/symlink.txt")).isNull();
    }

    @Test
    void rejectsKeyBaseWithoutPmcPrefix() {
        assertThat(InventoryEntry.fromS3Key("metadata/XYZ10009416.1.json")).isNull();
    }
}
