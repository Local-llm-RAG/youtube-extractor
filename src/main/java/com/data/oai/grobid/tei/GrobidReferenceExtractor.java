package com.data.oai.grobid.tei;

import com.data.oai.shared.util.DoiNormalizer;
import com.data.oai.shared.dto.Reference;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.Year;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.data.oai.grobid.tei.GrobidTeiUtils.*;

/**
 * Extracts references from TEI-XML documents parsed by GROBID.
 */
final class GrobidReferenceExtractor {

    private GrobidReferenceExtractor() {}

    private static final int MIN_VALID_YEAR = 1900;
    private static final int MAX_VALID_YEAR = Year.now().getValue();

    private static final Pattern DOI =
            Pattern.compile("(?i)\\b10\\.\\d{4,9}/[-._;()/:A-Z0-9]+\\b");

    /**
     * Improved references extraction:
     * - handles back listBibl biblStruct
     * - falls back to back listBibl bibl
     * - handles references divs
     * - captures more venue-ish data into idnos map (publisher/place/volume/issue/pages)
     */
    public static List<Reference> extractReferences(Document tei) {
        List<Reference> out = new ArrayList<>();
        int idx = 0;

        // biblStruct first
        Elements biblStructs = tei.select(
                "back listBibl biblStruct, " +
                        "back div[type=references] biblStruct, " +
                        "back div[subtype=references] biblStruct"
        );

        for (Element bibl : biblStructs) {
            idx++;
            Reference r = parseBiblStruct(idx, bibl);
            if (r != null) out.add(r);
        }

        // fallback: plain bibl (non-structured refs)
        Elements bibls = tei.select(
                "back listBibl > bibl, " +
                        "back div[type=references] > bibl, " +
                        "back div[subtype=references] > bibl"
        );

        for (Element bibl : bibls) {
            idx++;
            Reference r = parseBibl(idx, bibl);
            if (r != null) out.add(r);
        }

        return out.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                r -> safe(r.analyticTitle()) + "::" + safe(r.monogrTitle()) + "::" +
                                        safe(r.doi()) + "::" + firstOrEmpty(r.urls()) + "::" + safe(r.year()),
                                r -> r,
                                (a, b) -> a,
                                LinkedHashMap::new
                        ),
                        m -> List.copyOf(m.values())
                ));
    }

    private static Reference parseBiblStruct(int idx, Element bibl) {
        String analyticTitle = textOrNull(bibl.selectFirst("analytic > title"));
        String monogrTitle   = textOrNull(bibl.selectFirst("monogr > title"));
        String bestTitle = !isBlank(analyticTitle) ? analyticTitle : monogrTitle;

        String doi = DoiNormalizer.normalize(extractDoi(bibl));

        List<String> urls = bibl.select("ptr[target], ref[target], idno[type=url]")
                .stream()
                .map(e -> {
                    if (e.hasAttr("target")) return normalizeWs(e.attr("target"));
                    return normalizeWs(e.text());
                })
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        List<String> authors = bibl.select("author persName")
                .eachText()
                .stream()
                .map(GrobidTeiUtils::normalizeWs)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        String year = extractYear(bibl);

        String venue = textOrNull(bibl.selectFirst("monogr > title"));

        Map<String, String> idnos = extractIdnos(bibl);

        putIfNonBlank(idnos, "publisher", textOrNull(bibl.selectFirst("monogr imprint publisher")));
        putIfNonBlank(idnos, "pubplace", textOrNull(bibl.selectFirst("monogr imprint pubPlace")));
        putIfNonBlank(idnos, "date", textOrNull(bibl.selectFirst("monogr imprint date[when], monogr imprint date")));
        for (Element scope : bibl.select("monogr imprint biblScope[unit]")) {
            String unit = normalizeWs(scope.attr("unit")).toLowerCase(Locale.ROOT);
            String val = normalizeWs(scope.hasAttr("from") ? scope.attr("from") : scope.text());
            if (!unit.isBlank() && !val.isBlank()) {
                idnos.put("biblscope_" + unit, val);
            }
        }

        if (isBlank(bestTitle) && isBlank(doi) && urls.isEmpty() && authors.isEmpty()) return null;

        return new Reference(idx, analyticTitle, monogrTitle, doi, urls, authors, year, venue, idnos);
    }

    private static Reference parseBibl(int idx, Element bibl) {
        String raw = normalizeWs(bibl.text());
        if (raw.isBlank()) return null;

        String doi = DoiNormalizer.normalize(firstRegex(DOI, raw));

        Map<String, String> idnos = new LinkedHashMap<>();
        if (doi != null) idnos.put("doi", doi);

        idnos.put("raw_reference", raw);

        List<String> urls = bibl.select("ptr[target], ref[target]")
                .stream()
                .map(e -> normalizeWs(e.attr("target")))
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        return new Reference(idx, null, null, doi, urls, List.of(), null, null, idnos);
    }

    private static String extractYear(Element bibl) {
        Element date = bibl.selectFirst("imprint date[when], imprint date, date[when], date");
        if (date != null) {
            String raw = normalizeWs(date.hasAttr("when") ? date.attr("when") : date.text());
            if (raw != null && raw.length() >= 4) {
                String candidate = raw.substring(0, 4);
                if (isValidYear(candidate)) return candidate;
            }
        }
        // fallback: parse year from raw reference if present
        Element raw = bibl.selectFirst("note[type=raw_reference], note[type=rawRef], note[type=raw]");
        String rawText = raw != null ? raw.text() : bibl.text();
        String y = firstRegex(Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b"), rawText);
        if (y != null && !isValidYear(y)) return null;
        return y;
    }

    /**
     * Validates that a year candidate is a 4-digit number between 1900 and the current year.
     */
    private static boolean isValidYear(String candidate) {
        if (candidate == null || candidate.length() != 4) return false;
        try {
            int year = Integer.parseInt(candidate);
            return year >= MIN_VALID_YEAR && year <= MAX_VALID_YEAR;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Map<String, String> extractIdnos(Element bibl) {
        Map<String, String> idnos = new LinkedHashMap<>();
        for (Element idnoEl : bibl.select("idno")) {
            String type = normalizeWs(idnoEl.attr("type"));
            String val  = normalizeWs(idnoEl.text());
            if (!val.isBlank()) {
                idnos.put(type.isBlank() ? "unknown" : type.toLowerCase(Locale.ROOT), val);
            }
        }
        return idnos;
    }

    private static String extractDoi(Element bibl) {
        Element doiEl = bibl.selectFirst("idno[type=DOI], idno[type=doi]");
        if (doiEl != null) {
            String hit = firstRegex(DOI, doiEl.text());
            if (!isBlank(hit)) return hit;
        }

        Element raw = bibl.selectFirst("note[type=raw_reference], note[type=rawRef], note[type=raw]");
        String rawText = raw != null ? raw.text() : bibl.text();
        return firstRegex(DOI, rawText);
    }
}
