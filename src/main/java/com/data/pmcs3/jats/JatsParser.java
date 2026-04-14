package com.data.pmcs3.jats;

import com.data.oai.shared.dto.Author;
import com.data.oai.shared.dto.PaperDocument;
import com.data.oai.shared.dto.Reference;
import com.data.oai.shared.dto.Section;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parses JATS XML (the native PMC article format) into the shared
 * {@link PaperDocument} DTO used by the rest of the persistence pipeline.
 *
 * <p>Unlike GROBID — which parses a third-party PDF rendering — JATS is the
 * authoritative publisher-supplied structured form, so all sections, references,
 * keywords, and authors are exactly as the publisher tagged them.
 *
 * <p>This parser uses Jsoup in XML mode (already a project dependency) so we
 * avoid pulling in a full JAXB / Dom parser stack.
 */
public final class JatsParser {

    private static final Pattern WS = Pattern.compile("\\s+");

    /**
     * Matches the first 4-digit year anywhere in a reference date string.
     * JATS {@code <year>} elements sometimes carry free text like "February 2014"
     * or "2020-03-15" which would blow past the {@code reference_mention.year}
     * VARCHAR(10) column — we only persist the year digits.
     */
    private static final Pattern YEAR_DIGITS = Pattern.compile("\\b(\\d{4})\\b");

    private JatsParser() {}

    /**
     * Parses the given JATS XML string into a {@link PaperDocument}, or
     * returns a minimal empty-body document if the input is null/blank.
     *
     * @param sourceId          the PMC numeric id used as {@link PaperDocument#sourceId()}
     * @param externalIdentifier  the PMID (or other external id)
     * @param jatsXml           the raw JATS XML content
     */
    public static PaperDocument parse(String sourceId, String externalIdentifier, String jatsXml) {
        if (jatsXml == null || jatsXml.isBlank()) {
            return PaperDocument.empty(sourceId, externalIdentifier);
        }

        Document jats = Jsoup.parse(jatsXml, "", Parser.xmlParser());

        String title = firstText(jats, "article-meta > title-group > article-title");
        String abstractText = firstText(jats, "article-meta > abstract");
        List<String> keywords = extractKeywords(jats);
        List<String> affiliations = extractAffiliations(jats);
        List<String> classCodes = extractClassCodes(jats);
        List<String> fundingList = extractFunding(jats);
        List<Section> sections = extractSections(jats);
        if (sections.isEmpty()) {
            String bodyText = firstText(jats, "body");
            sections = List.of(new Section("BODY", 1, bodyText == null ? "" : bodyText, List.of()));
        }
        List<Reference> references = extractReferences(jats);
        String docType = extractDocType(jats);

        return new PaperDocument(
                sourceId,
                externalIdentifier,
                title,
                abstractText,
                sections,
                jatsXml,
                null, // rawContent is supplied by the facade from the .txt file
                keywords,
                affiliations,
                classCodes,
                fundingList,
                references,
                docType
        );
    }

    /**
     * Reads the {@code xml:lang} attribute from the root {@code <article>} element.
     * Returns {@code null} if the attribute is missing.
     */
    public static String extractLanguage(String jatsXml) {
        if (jatsXml == null || jatsXml.isBlank()) return null;
        Document jats = Jsoup.parse(jatsXml, "", Parser.xmlParser());
        Element article = jats.selectFirst("article");
        if (article == null) return null;
        return Optional.of(article.attr("xml:lang"))
                .filter(s -> !s.isBlank())
                .or(() -> Optional.of(article.attr("lang")).filter(s -> !s.isBlank()))
                .orElse(null);
    }

    /**
     * Extracts authors from the JATS document. Delegates to
     * {@link JatsAuthorExtractor}. Useful for pipelines that want authors
     * separately from the section/reference payload.
     */
    public static List<Author> extractAuthors(String jatsXml) {
        if (jatsXml == null || jatsXml.isBlank()) return List.of();
        Document jats = Jsoup.parse(jatsXml, "", Parser.xmlParser());
        return JatsAuthorExtractor.extractAuthors(jats);
    }

    /**
     * Extracts the DOI from {@code <article-id pub-id-type="doi">}.
     */
    public static String extractDoi(String jatsXml) {
        if (jatsXml == null || jatsXml.isBlank()) return null;
        Document jats = Jsoup.parse(jatsXml, "", Parser.xmlParser());
        Element el = jats.selectFirst("article-id[pub-id-type=doi]");
        return el == null ? null : cleanText(el.text());
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private static List<Section> extractSections(Document jats) {
        Element body = jats.selectFirst("body");
        if (body == null) return List.of();

        // Top-level sections only. Nested subsections are flattened into their parent's text
        // so we preserve their content without duplicating titles as empty blocks.
        return body.children().stream()
                .filter(sec -> "sec".equals(sec.tagName()))
                .map(sec -> {
                    String title = firstText(sec, "title");
                    String text = collectSectionText(sec);
                    int level = inferLevel(sec);
                    return new Section(title == null ? "SECTION" : title, level, text, List.of());
                })
                .toList();
    }

    private static String collectSectionText(Element sec) {
        // Walk paragraph-like descendants preserving reading order.
        return sec.select("p, list-item, caption, statement").stream()
                .map(p -> cleanText(p.text()))
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("\n"))
                .trim();
    }

    private static int inferLevel(Element sec) {
        int level = 1;
        Element parent = sec.parent();
        while (parent != null && !"body".equals(parent.tagName())) {
            if ("sec".equals(parent.tagName())) level++;
            parent = parent.parent();
        }
        return level;
    }

    private static List<String> extractKeywords(Document jats) {
        return jats.select("kwd-group > kwd").stream()
                .map(el -> cleanText(el.text()))
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private static List<String> extractAffiliations(Document jats) {
        return jats.select("aff").stream()
                .map(el -> cleanText(el.text()))
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private static List<String> extractClassCodes(Document jats) {
        return jats.select("subj-group > subject").stream()
                .map(el -> cleanText(el.text()))
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .toList();
    }

    /**
     * Extracts funding statements from {@code <funding-group>/<award-group>}.
     * Each award-group contributes one entry composed of the funder name
     * and (if present) the award ID.
     */
    private static List<String> extractFunding(Document jats) {
        List<String> out = new ArrayList<>();
        Elements awards = jats.select("funding-group > award-group");
        for (Element ag : awards) {
            String funder = firstText(ag, "funding-source");
            String awardId = firstText(ag, "award-id");
            String combined = Stream.of(funder, awardId)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining(" — "));
            if (!combined.isEmpty()) out.add(combined);
        }
        if (out.isEmpty()) {
            // Fallback: funding-statement under author-notes
            Element stmt = jats.selectFirst("funding-statement");
            if (stmt != null) {
                String t = cleanText(stmt.text());
                if (t != null && !t.isBlank()) out.add(t);
            }
        }
        return out;
    }

    private static List<Reference> extractReferences(Document jats) {
        List<Reference> out = new ArrayList<>();
        Elements refs = jats.select("ref-list > ref");
        int index = 0;
        for (Element ref : refs) {
            Element cit = ref.selectFirst("element-citation, mixed-citation, citation");
            if (cit == null) continue;

            String articleTitle = firstText(cit, "article-title");
            String source = firstText(cit, "source");
            String year = extractYearDigits(firstText(cit, "year"));
            String doi = firstText(cit, "pub-id[pub-id-type=doi]");
            String pmid = firstText(cit, "pub-id[pub-id-type=pmid]");

            List<String> authors = new ArrayList<>();
            for (Element name : cit.select("person-group > name, person-group > string-name, name, string-name")) {
                String surname = firstText(name, "surname");
                String given = firstText(name, "given-names");
                if (surname != null && !surname.isBlank()) {
                    authors.add(given == null || given.isBlank() ? surname : surname + ", " + given);
                } else {
                    String plain = cleanText(name.text());
                    if (plain != null && !plain.isBlank()) authors.add(plain);
                }
            }

            Map<String, String> idnos = new LinkedHashMap<>();
            if (doi != null && !doi.isBlank()) idnos.put("doi", doi);
            if (pmid != null && !pmid.isBlank()) idnos.put("pmid", pmid);

            List<String> urls = new ArrayList<>();
            for (Element link : cit.select("ext-link[ext-link-type=uri]")) {
                String href = link.attr("xlink:href");
                if (href != null && !href.isBlank()) urls.add(href);
            }

            out.add(new Reference(
                    index++,
                    articleTitle,
                    source,
                    doi,
                    urls,
                    authors,
                    year,
                    source,
                    idnos
            ));
        }
        return out;
    }

    private static String extractDocType(Document jats) {
        Element article = jats.selectFirst("article");
        if (article == null) return null;
        String t = article.attr("article-type");
        return (t == null || t.isBlank()) ? null : t.toLowerCase(Locale.ROOT);
    }

    private static String firstText(Element root, String cssQuery) {
        Element el = root.selectFirst(cssQuery);
        return el == null ? null : cleanText(el.text());
    }

    private static String firstText(Document doc, String cssQuery) {
        Element el = doc.selectFirst(cssQuery);
        return el == null ? null : cleanText(el.text());
    }

    /**
     * Extracts the first 4-digit year from a JATS reference date string.
     *
     * <p>JATS publishers sometimes populate {@code <year>} with free text like
     * {@code "February 2014"} or ISO-style {@code "2020-03-15"}. Our
     * {@code reference_mention.year} column is {@code VARCHAR(10)} and downstream
     * consumers only need the year digits, so we narrow the value here rather
     * than widening the column.
     *
     * @return the first 4-digit group found, or {@code null} if none is present
     *         (including {@code null} / blank input)
     */
    static String extractYearDigits(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Matcher m = YEAR_DIGITS.matcher(raw);
        return m.find() ? m.group(1) : null;
    }

    private static String cleanText(String raw) {
        if (raw == null) return null;
        String trimmed = WS.matcher(raw).replaceAll(" ").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
