package com.data.pmcs3.jats;

import com.data.oai.shared.dto.Author;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts authors from a JATS XML document.
 *
 * <p>JATS uses {@code <contrib contrib-type="author">} blocks inside
 * {@code <contrib-group>}. Names live in {@code <name><surname/><given-names/></name>}.
 * ORCID identifiers appear as {@code <contrib-id contrib-id-type="orcid">}.
 */
public final class JatsAuthorExtractor {

    private JatsAuthorExtractor() {}

    public static List<Author> extractAuthors(Document jats) {
        List<Author> authors = new ArrayList<>();
        Elements contribs = jats.select("contrib[contrib-type=author]");
        for (Element contrib : contribs) {
            Author a = new Author();
            a.setFirstName(textOrNull(contrib.selectFirst("name > given-names")));
            a.setLastName(textOrNull(contrib.selectFirst("name > surname")));
            a.setOrcid(extractOrcid(contrib));
            if (a.getFirstName() != null || a.getLastName() != null) {
                authors.add(a);
            }
        }
        return authors;
    }

    /**
     * Extracts an ORCID iD from a {@code <contrib>} element. Handles both
     * full URL forms and bare 16-digit forms. Returns the normalized bare
     * form (e.g. {@code "0000-0002-1825-0097"}) or {@code null} if not present.
     */
    private static String extractOrcid(Element contrib) {
        Element orcidEl = contrib.selectFirst("contrib-id[contrib-id-type=orcid]");
        if (orcidEl == null) return null;
        String raw = orcidEl.text();
        if (raw.isBlank()) return null;
        raw = raw.trim();
        // Strip any URL prefix (http://orcid.org/, https://orcid.org/)
        int idx = raw.lastIndexOf('/');
        if (idx >= 0) raw = raw.substring(idx + 1);
        return raw.isEmpty() ? null : raw;
    }

    private static String textOrNull(Element element) {
        if (element == null) return null;
        String text = element.text();
        return text.isBlank() ? null : text.trim();
    }
}
