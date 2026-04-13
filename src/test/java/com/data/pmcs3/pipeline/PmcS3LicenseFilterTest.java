package com.data.pmcs3.pipeline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PmcS3LicenseFilterTest {

    @Test
    void acceptsCc0() {
        assertThat(PmcS3LicenseFilter.isAcceptable("CC0")).isTrue();
        assertThat(PmcS3LicenseFilter.isAcceptable("cc0")).isTrue();
    }

    @Test
    void acceptsCcByAllVariants() {
        assertThat(PmcS3LicenseFilter.isAcceptable("CC BY")).isTrue();
        assertThat(PmcS3LicenseFilter.isAcceptable("CC BY 4.0")).isTrue();
        assertThat(PmcS3LicenseFilter.isAcceptable("CC-BY")).isTrue();
        assertThat(PmcS3LicenseFilter.isAcceptable("CCBY")).isTrue();
    }

    @Test
    void acceptsCcByShareAlike() {
        assertThat(PmcS3LicenseFilter.isAcceptable("CC BY-SA")).isTrue();
        assertThat(PmcS3LicenseFilter.isAcceptable("CC-BY-SA 4.0")).isTrue();
    }

    @Test
    void rejectsNonCommercial() {
        assertThat(PmcS3LicenseFilter.isAcceptable("CC BY-NC")).isFalse();
        assertThat(PmcS3LicenseFilter.isAcceptable("CC-BY-NC-SA")).isFalse();
        assertThat(PmcS3LicenseFilter.isAcceptable("CC BY-NC-ND")).isFalse();
    }

    @Test
    void rejectsNoDerivatives() {
        assertThat(PmcS3LicenseFilter.isAcceptable("CC BY-ND")).isFalse();
    }

    @Test
    void rejectsUnknownOrEmpty() {
        assertThat(PmcS3LicenseFilter.isAcceptable(null)).isFalse();
        assertThat(PmcS3LicenseFilter.isAcceptable("")).isFalse();
        assertThat(PmcS3LicenseFilter.isAcceptable("   ")).isFalse();
        assertThat(PmcS3LicenseFilter.isAcceptable("PROPRIETARY")).isFalse();
        assertThat(PmcS3LicenseFilter.isAcceptable("NO-CC CODE")).isFalse();
    }
}
