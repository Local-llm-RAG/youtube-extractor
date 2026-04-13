package com.data.pmcs3.inventory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InventoryService#dedupeByPmcIdKeepingHighestVersion}.
 *
 * <p>The PMC S3 inventory emits one row per {@code (pmcId, version)} tuple,
 * but our database uniquely keys {@code source_record} on the bare PMC id.
 * Dedup has to collapse multi-version articles down to the highest version
 * before they hit persistence.
 */
class InventoryServiceTest {

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
}
