package com.data.shared.license;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for {@link LicenseFilter}. These lock in the currently
 * shipped license policy:
 *
 * <ul>
 *   <li>ArXiv uses an exact-URL whitelist of three licenses.</li>
 *   <li>Zenodo and PubMed call {@code isPermissiveLicense(rejectND=true, rejectSA=false)}.</li>
 *   <li>PMC S3 uses its own filter ({@code PmcS3LicenseFilter}) — not exercised here.</li>
 * </ul>
 *
 * Every license string currently observed in the production corpus is covered
 * under both {@code rejectND=true} and {@code rejectND=false}, so any future
 * edit that narrows or widens acceptance will trip this suite.
 */
class LicenseFilterTest {

    @Nested
    @DisplayName("isPermissiveLicense with rejectND=true, rejectSA=false")
    class PermissiveRejectNd {

        // ----- observed commercially-valid licenses must be accepted -----

        @Test
        void shouldAccept_whenCcBy4UrlWithLegalcodeSuffix() {
            assertThat(LicenseFilter.isPermissiveLicense(
                    "https://creativecommons.org/licenses/by/4.0/legalcode", true, false)).isTrue();
        }

        @Test
        void shouldAccept_whenCcBy4UrlWithoutSuffix() {
            assertThat(LicenseFilter.isPermissiveLicense(
                    "https://creativecommons.org/licenses/by/4.0/", true, false)).isTrue();
        }

        @Test
        void shouldAccept_whenCcBySa4UrlWithLegalcodeSuffix() {
            assertThat(LicenseFilter.isPermissiveLicense(
                    "https://creativecommons.org/licenses/by-sa/4.0/legalcode", true, false)).isTrue();
        }

        @Test
        void shouldAccept_whenCc0UrlWithLegalcodeSuffix() {
            assertThat(LicenseFilter.isPermissiveLicense(
                    "https://creativecommons.org/publicdomain/zero/1.0/legalcode", true, false)).isTrue();
        }

        @Test
        void shouldAccept_whenHttpSchemeNormalizedToHttps() {
            assertThat(LicenseFilter.isPermissiveLicense(
                    "http://creativecommons.org/licenses/by/4.0/", true, false)).isTrue();
        }

        @Test
        void shouldAccept_whenMitShortCode() {
            assertThat(LicenseFilter.isPermissiveLicense("MIT", true, false)).isTrue();
        }

        @Test
        void shouldAccept_whenApache2ShortCode() {
            assertThat(LicenseFilter.isPermissiveLicense("Apache-2.0", true, false)).isTrue();
        }

        @Test
        void shouldAccept_whenBsd2Clause() {
            assertThat(LicenseFilter.isPermissiveLicense("BSD-2-Clause", true, false)).isTrue();
        }

        @Test
        void shouldAccept_whenBsd3Clause() {
            assertThat(LicenseFilter.isPermissiveLicense("BSD-3-Clause", true, false)).isTrue();
        }

        // ----- NoDerivatives variants must be rejected -----

        @Test
        void shouldReject_whenCcByNd4Url() {
            assertThat(LicenseFilter.isPermissiveLicense(
                    "https://creativecommons.org/licenses/by-nd/4.0/", true, false)).isFalse();
        }

        @Test
        void shouldReject_whenCcByNcNd4Url() {
            assertThat(LicenseFilter.isPermissiveLicense(
                    "https://creativecommons.org/licenses/by-nc-nd/4.0/", true, false)).isFalse();
        }

        @Test
        void shouldReject_whenCcByNdShortCode() {
            assertThat(LicenseFilter.isPermissiveLicense("CC-BY-ND", true, false)).isFalse();
        }

        // ----- NonCommercial variants must always be rejected regardless of rejectND -----

        @Test
        void shouldReject_whenCcByNc4Url() {
            assertThat(LicenseFilter.isPermissiveLicense(
                    "https://creativecommons.org/licenses/by-nc/4.0/", true, false)).isFalse();
        }

        @Test
        void shouldReject_whenCcByNcSa4Url() {
            assertThat(LicenseFilter.isPermissiveLicense(
                    "https://creativecommons.org/licenses/by-nc-sa/4.0/", true, false)).isFalse();
        }

        // ----- GPL family must always be rejected -----

        @Test
        void shouldReject_whenGpl3() {
            assertThat(LicenseFilter.isPermissiveLicense("GPL-3.0", true, false)).isFalse();
        }

        @Test
        void shouldReject_whenAgpl3() {
            assertThat(LicenseFilter.isPermissiveLicense("AGPL-3.0", true, false)).isFalse();
        }

        @Test
        void shouldReject_whenLgpl() {
            assertThat(LicenseFilter.isPermissiveLicense("LGPL-2.1", true, false)).isFalse();
        }

        // ----- null / blank -----

        @Test
        void shouldReject_whenNull() {
            assertThat(LicenseFilter.isPermissiveLicense(null, true, false)).isFalse();
        }

        @Test
        void shouldReject_whenBlank() {
            assertThat(LicenseFilter.isPermissiveLicense("   ", true, false)).isFalse();
        }
    }

    @Nested
    @DisplayName("isPermissiveLicense parity between rejectND=true and rejectND=false on observed corpus")
    class ObservedCorpusParity {

        /**
         * Every license string observed in the production DB as of the Session 1
         * audit (2026-04-17) should be accepted under BOTH {@code rejectND=true}
         * and {@code rejectND=false}. Any divergence here would be a rejection
         * of a commercially-valid license — which is the scenario the user's
         * safety guard explicitly forbids.
         */
        @Test
        void shouldAcceptObservedCorpusUnderBothModes_zenodo() {
            String[] zenodoLicenses = {
                    "https://creativecommons.org/licenses/by/4.0/legalcode",
                    "https://creativecommons.org/licenses/by-sa/4.0/legalcode",
                    "https://creativecommons.org/publicdomain/zero/1.0/legalcode"
            };
            for (String license : zenodoLicenses) {
                assertThat(LicenseFilter.isPermissiveLicense(license, true, false))
                        .as("rejectND=true should accept Zenodo license %s", license).isTrue();
                assertThat(LicenseFilter.isPermissiveLicense(license, false, false))
                        .as("rejectND=false should also accept Zenodo license %s", license).isTrue();
            }
        }

        @Test
        void shouldAcceptObservedCorpusUnderBothModes_pubmed() {
            String pubmedLicense = "https://creativecommons.org/licenses/by/4.0/";
            assertThat(LicenseFilter.isPermissiveLicense(pubmedLicense, true, false)).isTrue();
            assertThat(LicenseFilter.isPermissiveLicense(pubmedLicense, false, false)).isTrue();
        }
    }

    @Nested
    @DisplayName("isAcceptableByUrlWhitelist (ArXiv path)")
    class UrlWhitelist {

        @Test
        void shouldAccept_whenCcBy4() {
            assertThat(LicenseFilter.isAcceptableByUrlWhitelist(
                    "https://creativecommons.org/licenses/by/4.0/")).isTrue();
        }

        @Test
        void shouldAccept_whenCcBySa4() {
            assertThat(LicenseFilter.isAcceptableByUrlWhitelist(
                    "https://creativecommons.org/licenses/by-sa/4.0/")).isTrue();
        }

        @Test
        void shouldAccept_whenCc0() {
            assertThat(LicenseFilter.isAcceptableByUrlWhitelist(
                    "https://creativecommons.org/publicdomain/zero/1.0/")).isTrue();
        }

        @Test
        void shouldAccept_whenHttpSchemeNormalizedToHttps() {
            assertThat(LicenseFilter.isAcceptableByUrlWhitelist(
                    "http://creativecommons.org/licenses/by/4.0/")).isTrue();
        }

        @Test
        void shouldReject_whenCcByNd() {
            assertThat(LicenseFilter.isAcceptableByUrlWhitelist(
                    "https://creativecommons.org/licenses/by-nd/4.0/")).isFalse();
        }

        @Test
        void shouldReject_whenCcByNc() {
            assertThat(LicenseFilter.isAcceptableByUrlWhitelist(
                    "https://creativecommons.org/licenses/by-nc/4.0/")).isFalse();
        }

        @Test
        void shouldReject_whenUrlWithTrailingLegalcodeSuffix() {
            // Exact-URL whitelist — Zenodo's legalcode suffix is NOT accepted here.
            // That's intentional; ArXiv strictly uses the plain canonical URL form.
            assertThat(LicenseFilter.isAcceptableByUrlWhitelist(
                    "https://creativecommons.org/licenses/by/4.0/legalcode")).isFalse();
        }

        @Test
        void shouldReject_whenNull() {
            assertThat(LicenseFilter.isAcceptableByUrlWhitelist(null)).isFalse();
        }

        @Test
        void shouldReject_whenBlank() {
            assertThat(LicenseFilter.isAcceptableByUrlWhitelist("   ")).isFalse();
        }
    }

    @Nested
    @DisplayName("normalizeLicense")
    class Normalize {

        @Test
        void shouldReturnNull_whenNull() {
            assertThat(LicenseFilter.normalizeLicense(null)).isNull();
        }

        @Test
        void shouldUpgradeHttpToHttps() {
            assertThat(LicenseFilter.normalizeLicense("http://creativecommons.org/licenses/by/4.0/"))
                    .isEqualTo("https://creativecommons.org/licenses/by/4.0/");
        }

        @Test
        void shouldTrimWhitespace() {
            assertThat(LicenseFilter.normalizeLicense("   https://creativecommons.org/licenses/by/4.0/   "))
                    .isEqualTo("https://creativecommons.org/licenses/by/4.0/");
        }
    }

    @Nested
    @DisplayName("looksLikeLicenseUrl")
    class LooksLikeUrl {

        @Test
        void shouldReturnTrue_whenHttps() {
            assertThat(LicenseFilter.looksLikeLicenseUrl("https://example.org/license")).isTrue();
        }

        @Test
        void shouldReturnTrue_whenHttp() {
            assertThat(LicenseFilter.looksLikeLicenseUrl("http://example.org/license")).isTrue();
        }

        @Test
        void shouldReturnFalse_whenPlainText() {
            assertThat(LicenseFilter.looksLikeLicenseUrl("CC-BY 4.0")).isFalse();
        }

        @Test
        void shouldReturnFalse_whenNull() {
            assertThat(LicenseFilter.looksLikeLicenseUrl(null)).isFalse();
        }

        @Test
        void shouldReturnFalse_whenBlank() {
            assertThat(LicenseFilter.looksLikeLicenseUrl("   ")).isFalse();
        }
    }
}
