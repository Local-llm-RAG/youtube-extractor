package com.data.oai.grobid.tei;

import com.data.oai.shared.dto.PaperDocument;
import com.data.oai.shared.dto.Reference;
import com.data.oai.shared.dto.Section;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.List;

import static com.data.oai.grobid.tei.GrobidTeiUtils.firstText;
import static com.data.oai.grobid.tei.GrobidTeiUtils.normalizeWs;

/**
 * Main entry point for mapping GROBID TEI-XML to {@link PaperDocument}.
 * Delegates extraction work to focused classes:
 * <ul>
 *   <li>{@link GrobidSectionExtractor} — section extraction</li>
 *   <li>{@link GrobidReferenceExtractor} — reference extraction</li>
 *   <li>{@link GrobidTextExtractor} — plain text, keywords, affiliations, class codes, doc type</li>
 * </ul>
 */
public final class GrobidTeiMapperJsoup {

    private static final String NO_CONTENT_MARKER = "NO_CONTENT";

    private GrobidTeiMapperJsoup() {}

    public static PaperDocument toPaperDocument(String arxivId, String oaiIdentifier, String teiXml) {
        if (teiXml == null || teiXml.isBlank()) {
            return new PaperDocument(arxivId, oaiIdentifier, null, null,
                    List.of(new Section("BODY", 1, "", List.of())), teiXml, NO_CONTENT_MARKER,
                    List.of(), List.of(), List.of(), List.of(), null);
        }

        Document tei = Jsoup.parse(teiXml, "", Parser.xmlParser());

        String title = GrobidTeiUtils.cleanText(firstText(tei, "teiHeader titleStmt > title"));
        if (GrobidTeiUtils.isBlank(title)) title = GrobidTeiUtils.cleanText(firstText(tei, "teiHeader sourceDesc biblStruct analytic title"));

        String abstractText = GrobidTeiUtils.cleanText(firstText(tei, "teiHeader profileDesc abstract"));
        List<String> keywords = GrobidTextExtractor.extractKeywords(tei);

        List<String> affiliation = GrobidTextExtractor.extractAffiliations(tei);

        List<String> classCodes = GrobidTextExtractor.extractClassCodes(tei);

        List<Reference> references = GrobidReferenceExtractor.extractReferences(tei);

        String docType = GrobidTextExtractor.extractDocType(tei);

        // Improved: recursive, preserves nested divs and avoids dropping subsection text.
        List<Section> sections = GrobidSectionExtractor.extractSections(tei);
        if (sections.isEmpty() || sections.stream().allMatch(s -> s.getText().isBlank())) {
            String bodyText = normalizeWs(firstText(tei, "text > body"));
            sections = List.of(new Section("BODY", 1, bodyText == null ? "" : bodyText, new ArrayList<>()));
        }

        // Improved: no re-parse; preserve order better; avoid table dumps but keep captions.
        String rawContent = GrobidTextExtractor.teiToPlainText(tei);

        return new PaperDocument(
                arxivId,
                oaiIdentifier,
                title,
                abstractText,
                sections,
                teiXml,
                rawContent,
                keywords,
                affiliation,
                classCodes,
                references,
                docType
        );
    }
}
