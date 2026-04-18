package com.data.pmcs3.jats;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JatsParser#extractPublicationDate(String)}.
 *
 * <p>Locks in the priority order: epub &gt; ppub &gt; publication-format=electronic
 * &gt; publication-format=print &gt; first pub-date, and the ISO-8601 formatting
 * with month/day defaults.
 */
class JatsParserPublicationDateTest {

    @Test
    void shouldPreferEpub_whenBothEpubAndPpubPresent() {
        String jats = """
                <?xml version="1.0"?>
                <article>
                  <front>
                    <article-meta>
                      <pub-date pub-type="ppub"><year>2023</year><month>01</month><day>15</day></pub-date>
                      <pub-date pub-type="epub"><year>2024</year><month>06</month><day>01</day></pub-date>
                    </article-meta>
                  </front>
                </article>
                """;
        assertThat(JatsParser.extractPublicationDate(jats)).isEqualTo("2024-06-01");
    }

    @Test
    void shouldFallBackToPpub_whenOnlyPpubPresent() {
        String jats = """
                <article><front><article-meta>
                  <pub-date pub-type="ppub"><year>2022</year><month>03</month><day>15</day></pub-date>
                </article-meta></front></article>
                """;
        assertThat(JatsParser.extractPublicationDate(jats)).isEqualTo("2022-03-15");
    }

    @Test
    void shouldFallBackToPublicationFormatElectronic_whenNoTypedPubDate() {
        String jats = """
                <article><front><article-meta>
                  <pub-date publication-format="electronic"><year>2025</year><month>11</month><day>20</day></pub-date>
                </article-meta></front></article>
                """;
        assertThat(JatsParser.extractPublicationDate(jats)).isEqualTo("2025-11-20");
    }

    @Test
    void shouldFallBackToFirstPubDate_whenNoKnownAttributes() {
        String jats = """
                <article><front><article-meta>
                  <pub-date><year>2021</year><month>07</month><day>04</day></pub-date>
                </article-meta></front></article>
                """;
        assertThat(JatsParser.extractPublicationDate(jats)).isEqualTo("2021-07-04");
    }

    @Test
    void shouldDefaultMonthAndDayToOne_whenOnlyYearPresent() {
        String jats = """
                <article><front><article-meta>
                  <pub-date pub-type="epub"><year>2020</year></pub-date>
                </article-meta></front></article>
                """;
        assertThat(JatsParser.extractPublicationDate(jats)).isEqualTo("2020-01-01");
    }

    @Test
    void shouldDefaultDayToOne_whenOnlyYearAndMonthPresent() {
        String jats = """
                <article><front><article-meta>
                  <pub-date pub-type="epub"><year>2020</year><month>08</month></pub-date>
                </article-meta></front></article>
                """;
        assertThat(JatsParser.extractPublicationDate(jats)).isEqualTo("2020-08-01");
    }

    @Test
    void shouldNormalizeInvalidDayToFirstOfMonth() {
        // JATS publishers occasionally emit invalid day values; we normalize to day=01
        // rather than throwing or losing the year/month.
        String jats = """
                <article><front><article-meta>
                  <pub-date pub-type="epub"><year>2023</year><month>02</month><day>31</day></pub-date>
                </article-meta></front></article>
                """;
        assertThat(JatsParser.extractPublicationDate(jats)).isEqualTo("2023-02-01");
    }

    @Test
    void shouldReturnNull_whenYearMissing() {
        String jats = """
                <article><front><article-meta>
                  <pub-date pub-type="epub"><month>06</month><day>15</day></pub-date>
                </article-meta></front></article>
                """;
        assertThat(JatsParser.extractPublicationDate(jats)).isNull();
    }

    @Test
    void shouldReturnNull_whenYearNotNumeric() {
        String jats = """
                <article><front><article-meta>
                  <pub-date pub-type="epub"><year>MMXX</year></pub-date>
                </article-meta></front></article>
                """;
        assertThat(JatsParser.extractPublicationDate(jats)).isNull();
    }

    @Test
    void shouldReturnNull_whenNoPubDateInArticleMeta() {
        String jats = """
                <article><front><article-meta>
                  <article-id pub-id-type="doi">10.1/x</article-id>
                </article-meta></front></article>
                """;
        assertThat(JatsParser.extractPublicationDate(jats)).isNull();
    }

    @Test
    void shouldReturnNull_whenJatsXmlIsNullOrBlank() {
        assertThat(JatsParser.extractPublicationDate(null)).isNull();
        assertThat(JatsParser.extractPublicationDate("")).isNull();
        assertThat(JatsParser.extractPublicationDate("   ")).isNull();
    }

    @Test
    void shouldReturnNull_whenNoArticleMetaElement() {
        String jats = "<article><body><p>orphan</p></body></article>";
        assertThat(JatsParser.extractPublicationDate(jats)).isNull();
    }

    @Test
    void shouldPadSingleDigitMonthAndDay_toIso8601() {
        String jats = """
                <article><front><article-meta>
                  <pub-date pub-type="epub"><year>2024</year><month>3</month><day>7</day></pub-date>
                </article-meta></front></article>
                """;
        assertThat(JatsParser.extractPublicationDate(jats)).isEqualTo("2024-03-07");
    }
}
