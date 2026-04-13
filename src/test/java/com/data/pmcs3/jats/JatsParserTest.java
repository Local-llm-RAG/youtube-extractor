package com.data.pmcs3.jats;

import com.data.oai.shared.dto.Author;
import com.data.oai.shared.dto.PaperDocument;
import com.data.oai.shared.dto.Reference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JatsParserTest {

    private static final String FULL_JATS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article xml:lang="en" article-type="research-article">
              <front>
                <article-meta>
                  <article-id pub-id-type="doi">10.1234/example.5678</article-id>
                  <title-group>
                    <article-title>A Complete Test Article</article-title>
                  </title-group>
                  <contrib-group>
                    <contrib contrib-type="author">
                      <contrib-id contrib-id-type="orcid">https://orcid.org/0000-0002-1825-0097</contrib-id>
                      <name>
                        <surname>Curie</surname>
                        <given-names>Marie</given-names>
                      </name>
                    </contrib>
                    <contrib contrib-type="author">
                      <name>
                        <surname>Einstein</surname>
                        <given-names>Albert</given-names>
                      </name>
                    </contrib>
                  </contrib-group>
                  <aff id="a1">Institute of Science</aff>
                  <abstract>
                    <p>A short abstract describing the work.</p>
                  </abstract>
                  <kwd-group>
                    <kwd>physics</kwd>
                    <kwd>quantum mechanics</kwd>
                  </kwd-group>
                  <funding-group>
                    <award-group>
                      <funding-source>National Science Foundation</funding-source>
                      <award-id>NSF-12345</award-id>
                    </award-group>
                  </funding-group>
                  <article-categories>
                    <subj-group>
                      <subject>Research Article</subject>
                    </subj-group>
                  </article-categories>
                </article-meta>
              </front>
              <body>
                <sec sec-type="intro">
                  <title>Introduction</title>
                  <p>Introductory paragraph explaining the problem.</p>
                </sec>
                <sec sec-type="methods">
                  <title>Methods</title>
                  <p>We used a telescope.</p>
                  <p>And a spectrometer.</p>
                </sec>
              </body>
              <back>
                <ref-list>
                  <ref id="R1">
                    <element-citation publication-type="journal">
                      <person-group person-group-type="author">
                        <name><surname>Newton</surname><given-names>Isaac</given-names></name>
                      </person-group>
                      <article-title>Principia</article-title>
                      <source>Royal Society</source>
                      <year>1687</year>
                      <pub-id pub-id-type="doi">10.5555/principia.1687</pub-id>
                    </element-citation>
                  </ref>
                </ref-list>
              </back>
            </article>
            """;

    @Test
    void parsesTitleAbstractAndKeywords() {
        PaperDocument doc = JatsParser.parse("1234", "PMID:5678", FULL_JATS);

        assertThat(doc.title()).isEqualTo("A Complete Test Article");
        assertThat(doc.abstractText()).contains("short abstract");
        assertThat(doc.keywords()).containsExactly("physics", "quantum mechanics");
        assertThat(doc.affiliation()).contains("Institute of Science");
        assertThat(doc.classCodes()).contains("Research Article");
    }

    @Test
    void parsesFundingGroupIntoFundingList() {
        PaperDocument doc = JatsParser.parse("1234", "PMID:5678", FULL_JATS);
        assertThat(doc.fundingList())
                .anyMatch(s -> s.contains("National Science Foundation"))
                .anyMatch(s -> s.contains("NSF-12345"));
    }

    @Test
    void parsesSectionsWithTitlesAndOrder() {
        PaperDocument doc = JatsParser.parse("1234", "PMID:5678", FULL_JATS);
        assertThat(doc.sections()).hasSize(2);
        assertThat(doc.sections().get(0).getTitle()).isEqualTo("Introduction");
        assertThat(doc.sections().get(1).getTitle()).isEqualTo("Methods");
        assertThat(doc.sections().get(1).getText()).contains("telescope").contains("spectrometer");
    }

    @Test
    void parsesReferencesWithDoiAndAuthors() {
        PaperDocument doc = JatsParser.parse("1234", "PMID:5678", FULL_JATS);
        assertThat(doc.references()).hasSize(1);
        Reference ref = doc.references().get(0);
        assertThat(ref.analyticTitle()).isEqualTo("Principia");
        assertThat(ref.doi()).isEqualTo("10.5555/principia.1687");
        assertThat(ref.year()).isEqualTo("1687");
        assertThat(ref.authors()).anyMatch(s -> s.contains("Newton"));
    }

    @Test
    void extractsLanguageFromXmlLang() {
        assertThat(JatsParser.extractLanguage(FULL_JATS)).isEqualTo("en");
    }

    @Test
    void extractsDocTypeFromArticleType() {
        PaperDocument doc = JatsParser.parse("1234", "PMID:5678", FULL_JATS);
        assertThat(doc.docType()).isEqualTo("research-article");
    }

    @Test
    void extractsAuthorsWithOrcid() {
        List<Author> authors = JatsParser.extractAuthors(FULL_JATS);
        assertThat(authors).hasSize(2);

        Author first = authors.get(0);
        assertThat(first.getFirstName()).isEqualTo("Marie");
        assertThat(first.getLastName()).isEqualTo("Curie");
        assertThat(first.getOrcid()).isEqualTo("0000-0002-1825-0097");

        Author second = authors.get(1);
        assertThat(second.getFirstName()).isEqualTo("Albert");
        assertThat(second.getLastName()).isEqualTo("Einstein");
        assertThat(second.getOrcid()).isNull();
    }

    @Test
    void extractsDoiFromArticleMeta() {
        assertThat(JatsParser.extractDoi(FULL_JATS)).isEqualTo("10.1234/example.5678");
    }

    @Test
    void extractYearDigitsHandlesVariousFormats() {
        // Clean 4-digit year passes through unchanged.
        assertThat(JatsParser.extractYearDigits("2019")).isEqualTo("2019");
        // Free-text month + year — keep only the year digits.
        assertThat(JatsParser.extractYearDigits("February 2014")).isEqualTo("2014");
        // ISO-style date — first 4-digit group wins.
        assertThat(JatsParser.extractYearDigits("2020-03-15")).isEqualTo("2020");
        // No digits at all → null.
        assertThat(JatsParser.extractYearDigits("n.d.")).isNull();
        // Empty / blank / null → null.
        assertThat(JatsParser.extractYearDigits("")).isNull();
        assertThat(JatsParser.extractYearDigits("   ")).isNull();
        assertThat(JatsParser.extractYearDigits(null)).isNull();
        // Multiple years — first match wins.
        assertThat(JatsParser.extractYearDigits("circa 1998, revised 2001")).isEqualTo("1998");
    }

    @Test
    void parseNarrowsFreeTextYearInReferences() {
        String jats = """
                <?xml version="1.0" encoding="UTF-8"?>
                <article>
                  <back>
                    <ref-list>
                      <ref id="R1">
                        <element-citation publication-type="journal">
                          <article-title>A winter paper</article-title>
                          <source>Journal</source>
                          <year>February 2014</year>
                        </element-citation>
                      </ref>
                      <ref id="R2">
                        <mixed-citation publication-type="journal">
                          <article-title>An ISO paper</article-title>
                          <source>Journal</source>
                          <year>2020-03-15</year>
                        </mixed-citation>
                      </ref>
                      <ref id="R3">
                        <element-citation publication-type="journal">
                          <article-title>Undated</article-title>
                          <source>Journal</source>
                          <year>n.d.</year>
                        </element-citation>
                      </ref>
                    </ref-list>
                  </back>
                </article>
                """;
        PaperDocument doc = JatsParser.parse("1", "PMID:1", jats);
        assertThat(doc.references()).hasSize(3);
        assertThat(doc.references().get(0).year()).isEqualTo("2014");
        assertThat(doc.references().get(1).year()).isEqualTo("2020");
        assertThat(doc.references().get(2).year()).isNull();
    }

    @Test
    void handlesEmptyXmlGracefully() {
        PaperDocument doc = JatsParser.parse("1234", "PMID:5678", null);
        assertThat(doc).isNotNull();
        assertThat(doc.title()).isNull();
        assertThat(doc.sections()).hasSize(1);
        assertThat(doc.sections().get(0).getTitle()).isEqualTo("BODY");

        PaperDocument emptyDoc = JatsParser.parse("1234", "PMID:5678", "   ");
        assertThat(emptyDoc).isNotNull();
        assertThat(emptyDoc.title()).isNull();
    }
}
