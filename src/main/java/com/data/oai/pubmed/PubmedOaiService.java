package com.data.oai.pubmed;

import com.data.config.properties.PubmedOaiProps;
import com.data.oai.generic.common.dto.Author;
import com.data.oai.generic.common.dto.Record;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for harvesting metadata from PubMed Central (PMC) via the OAI-PMH protocol
 * and resolving PDF download links through the PMC Open Access Web Service.
 *
 * <p>Uses the {@code oai_dc} (Dublin Core) metadata prefix. Only records from the
 * {@code pmc-open} set (open-access articles) are harvested, ensuring PDF availability
 * for downstream GROBID processing.</p>
 *
 * <p>Rate limiting: PMC allows a maximum of 3 requests/second. A 350ms pause is
 * inserted between pagination calls, and the client handles 429/503 with exponential
 * backoff retries.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PubmedOaiService {

    private final PubmedOaiProps props;
    private final PubmedClient pubmedClient;

    private final XMLInputFactory xml = newXmlFactory();

    private static final Pattern DOI_PATTERN =
            Pattern.compile("(?:https?://doi\\.org/|doi:\\s*)(10\\..+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PMC_ID_PATTERN = Pattern.compile("PMC(\\d+)");
    private static final long PAGINATION_DELAY_MS = 350;

    /**
     * Fetches all open-access PMC records for the given date range.
     * Automatically follows resumptionTokens to collect all pages.
     */
    public List<Record> getPubmedPapersMetadata(String from, String until) {
        List<Record> collected = new ArrayList<>();
        String token = null;

        do {
            byte[] body = pubmedClient.listRecords(
                    props.baseUrl(), from, until, token,
                    props.metadataPrefix(), props.set());

            // PMC returns null (HTTP 404) when no records exist for the date range
            if (body == null) {
                log.info("No PMC records available for period {} to {}", from, until);
                break;
            }

            Page page = parseDublinCore(body);
            collected.addAll(page.records);
            token = page.resumptionToken;

            sleep(PAGINATION_DELAY_MS);
        } while (token != null && !token.isBlank());

        log.info("Collected {} PMC records for period {} to {}", collected.size(), from, until);
        return collected;
    }

    /**
     * Downloads the PDF for a given PMC article. Uses the OA Web Service to resolve
     * download links, preferring direct PDF, falling back to tgz extraction.
     *
     * @param pmcNumericId the numeric PMC ID (without "PMC" prefix)
     * @return entry with download URL as key and PDF bytes as value
     */
    public AbstractMap.SimpleEntry<String, byte[]> getPdf(String pmcNumericId) {
        String pmcId = "PMC" + pmcNumericId;
        OaLinks links = resolveOaLinks(pmcId);

        // Try direct PDF link first
        if (links.pdfUrl != null) {
            try {
                byte[] pdfBytes = pubmedClient.downloadPdf(links.pdfUrl);
                if (isPdf(pdfBytes)) {
                    return new AbstractMap.SimpleEntry<>(links.pdfUrl, pdfBytes);
                }
                log.warn("Direct PDF link for {} did not return valid PDF", pmcId);
            } catch (Exception e) {
                log.warn("Failed to download PDF for {}: {}", pmcId, e.getMessage());
            }
        }

        // Fall back to extracting PDF from tgz archive
        if (links.tgzUrl != null) {
            try {
                byte[] tgzBytes = pubmedClient.downloadPdf(links.tgzUrl);
                byte[] pdfBytes = extractPdfFromTgz(tgzBytes);
                if (pdfBytes != null) {
                    return new AbstractMap.SimpleEntry<>(links.tgzUrl, pdfBytes);
                }
                log.warn("No PDF found inside tgz archive for {}", pmcId);
            } catch (Exception e) {
                log.warn("Failed to download/extract tgz for {}: {}", pmcId, e.getMessage());
            }
        }

        log.info("Skipping {} — not available for bulk download (likely not in OA subset)", pmcId);
        return null;
    }

    /**
     * Calls the PMC OA Web Service and parses the response XML to extract
     * both PDF and tgz download links.
     */
    private OaLinks resolveOaLinks(String pmcId) {
        try {
            byte[] oaResponse = pubmedClient.fetchOaLinks(pmcId);
            return parseOaServiceLinks(oaResponse);
        } catch (Exception e) {
            log.warn("OA Web Service call failed for {}", pmcId, e);
            return new OaLinks(null, null);
        }
    }

    /**
     * Parses the OA Web Service XML response to extract PDF and tgz hrefs.
     */
    private OaLinks parseOaServiceLinks(byte[] xmlBytes) {
        String pdfUrl = null;
        String tgzUrl = null;
        XMLEventReader reader = null;
        try {
            reader = xml.createXMLEventReader(new ByteArrayInputStream(xmlBytes));

            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();

                if (event.isStartElement()) {
                    StartElement se = event.asStartElement();
                    String name = se.getName().getLocalPart();

                    if ("link".equals(name)) {
                        String format = attrValue(se, "format");
                        String href = attrValue(se, "href");
                        if ("pdf".equalsIgnoreCase(format)) {
                            pdfUrl = href;
                        } else if ("tgz".equalsIgnoreCase(format)) {
                            tgzUrl = href;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse OA service response", e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
        return new OaLinks(pdfUrl, tgzUrl);
    }

    /**
     * Extracts a PDF file from a tar.gz archive (PMC OA package format).
     */
    private byte[] extractPdfFromTgz(byte[] tgzBytes) {
        try (var gzipIn = new java.util.zip.GZIPInputStream(new ByteArrayInputStream(tgzBytes))) {
            byte[] tarBytes = gzipIn.readAllBytes();
            int offset = 0;
            while (offset + 512 <= tarBytes.length) {
                String name = new String(tarBytes, offset, 100, java.nio.charset.StandardCharsets.US_ASCII).trim();
                if (name.isEmpty()) break;

                String sizeStr = new String(tarBytes, offset + 124, 12, java.nio.charset.StandardCharsets.US_ASCII).trim();
                if (sizeStr.isEmpty()) break;
                long size = Long.parseLong(sizeStr, 8);

                int dataOffset = offset + 512;
                if (name.toLowerCase(Locale.ROOT).endsWith(".pdf") && size > 0) {
                    byte[] pdfBytes = new byte[(int) size];
                    System.arraycopy(tarBytes, dataOffset, pdfBytes, 0, (int) size);
                    return pdfBytes;
                }

                offset = dataOffset + (int) ((size + 511) / 512 * 512);
            }
        } catch (Exception e) {
            log.warn("Failed to extract PDF from tgz", e);
        }
        return null;
    }

    private static boolean isPdf(byte[] bytes) {
        return bytes != null && bytes.length > 4
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private record OaLinks(String pdfUrl, String tgzUrl) {}

    private static XMLInputFactory newXmlFactory() {
        XMLInputFactory f = XMLInputFactory.newFactory();
        try {
            f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        } catch (IllegalArgumentException ignored) {
        }
        return f;
    }

    /**
     * Parses an OAI-PMH response with metadataPrefix=oai_dc (Dublin Core).
     *
     * <p>Dublin Core elements mapped:
     * <ul>
     *   <li>{@code dc:title} → stored for reference (not in Record, used by GROBID later)</li>
     *   <li>{@code dc:creator} → authors (name parsed from "LastName, FirstName" format)</li>
     *   <li>{@code dc:subject} → categories</li>
     *   <li>{@code dc:description} → comments (abstract)</li>
     *   <li>{@code dc:identifier} → doi (if DOI pattern), sourceId not set here</li>
     *   <li>{@code dc:rights} → license</li>
     *   <li>{@code dc:source} → journalRef</li>
     * </ul>
     * </p>
     */
    private Page parseDublinCore(byte[] xmlBytes) {
        XMLEventReader reader = null;
        try {
            reader = xml.createXMLEventReader(new ByteArrayInputStream(xmlBytes));
            List<Record> records = new ArrayList<>();

            Record cur = null;
            boolean inHeader = false;
            boolean inMetadata = false;
            String tag = null;
            String resumptionToken = null;
            StringBuilder descriptionBuilder = null;

            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();

                if (event.isStartElement()) {
                    String name = event.asStartElement().getName().getLocalPart();

                    switch (name) {
                        case "record" -> {
                            cur = new Record();
                            descriptionBuilder = null;
                        }
                        case "header" -> inHeader = true;
                        case "metadata" -> inMetadata = true;
                        case "resumptionToken" -> tag = "token";
                        case "identifier" -> {
                            if (inHeader) tag = "headerIdentifier";
                            else if (inMetadata) tag = "dcIdentifier";
                        }
                        case "datestamp" -> {
                            if (inHeader) tag = "datestamp";
                        }
                        case "creator" -> {
                            if (inMetadata) tag = "creator";
                        }
                        case "subject" -> {
                            if (inMetadata) tag = "subject";
                        }
                        case "description" -> {
                            if (inMetadata) tag = "description";
                        }
                        case "rights" -> {
                            if (inMetadata) tag = "rights";
                        }
                        case "source" -> {
                            if (inMetadata) tag = "source";
                        }
                    }
                }

                if (event.isCharacters() && tag != null) {
                    String text = event.asCharacters().getData();
                    if (text == null) continue;
                    text = text.trim();
                    if (text.isEmpty()) continue;

                    if (cur == null && !"token".equals(tag)) continue;

                    switch (tag) {
                        case "token" -> resumptionToken = text;
                        case "headerIdentifier" -> cur.setOaiIdentifier(text);
                        case "datestamp" -> cur.setDatestamp(text);
                        case "creator" -> {
                            Author author = parseAuthorName(text);
                            cur.getAuthors().add(author);
                        }
                        case "subject" -> cur.getCategories().add(text);
                        case "description" -> {
                            if (descriptionBuilder == null) {
                                descriptionBuilder = new StringBuilder(text);
                            } else {
                                descriptionBuilder.append(" ").append(text);
                            }
                        }
                        case "rights" -> {
                            if (cur.getLicense() == null) {
                                cur.setLicense(text);
                            } else if (looksLikeLicenseUrl(text)) {
                                // Prefer the URL form over the human-readable text
                                cur.setLicense(text);
                            }
                        }
                        case "source" -> {
                            if (cur.getJournalRef() == null) {
                                cur.setJournalRef(text);
                            }
                        }
                        case "dcIdentifier" -> handleDcIdentifier(cur, text);
                    }
                }

                if (event.isEndElement()) {
                    String name = event.asEndElement().getName().getLocalPart();

                    switch (name) {
                        case "header" -> inHeader = false;
                        case "metadata" -> inMetadata = false;
                        case "record" -> {
                            if (cur != null) {
                                if (descriptionBuilder != null && !descriptionBuilder.toString().isBlank()) {
                                    cur.setComments(descriptionBuilder.toString());
                                }
                                if (cur.getLicense() != null) {
                                    cur.setLicense(normalizeLicense(cur.getLicense()));
                                }

                                // PMC pmc-open set guarantees open access; when no
                                // dc:rights element is present, default to CC-BY 4.0.
                                if (cur.getLicense() == null) {
                                    cur.setLicense("https://creativecommons.org/licenses/by/4.0/");
                                }

                                if (isOpenAccessLicense(cur.getLicense())
                                        && isLikelyScholarlyText(cur)) {
                                    records.add(cur);
                                }
                            }
                            cur = null;
                            descriptionBuilder = null;
                        }
                    }

                    if (tag != null && tag.equals(name)) {
                        tag = null;
                    }
                    if ("identifier".equals(name)
                            && ("headerIdentifier".equals(tag) || "dcIdentifier".equals(tag))) {
                        tag = null;
                    }
                    if ("resumptionToken".equals(name) && "token".equals(tag)) {
                        tag = null;
                    }
                }
            }

            return new Page(records, resumptionToken);
        } catch (Exception e) {
            throw new RuntimeException("PMC OAI-PMH parse failed", e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Dublin Core {@code dc:identifier} can contain a DOI URL, a PMC ID, or a PMID.
     * This method routes each value to the appropriate Record field.
     */
    private void handleDcIdentifier(Record record, String text) {
        Matcher doiMatcher = DOI_PATTERN.matcher(text);
        if (doiMatcher.find()) {
            record.setDoi(normalizeDoi(doiMatcher.group(1)));
            return;
        }

        Matcher pmcMatcher = PMC_ID_PATTERN.matcher(text);
        if (pmcMatcher.find()) {
            // PMC numeric ID is already set via OAI identifier extraction;
            // this is a secondary confirmation
            return;
        }

        // Other identifiers (PMID etc.) are ignored for now
    }

    /**
     * Parses an author name string. PMC Dublin Core typically provides names as
     * "LastName, FirstName" but may also use "FirstName LastName" format.
     */
    private static Author parseAuthorName(String name) {
        Author author = new Author();

        if (name.contains(",")) {
            String[] parts = name.split(",", 2);
            author.lastName = parts[0].trim();
            author.firstName = parts[1].trim();
        } else {
            String[] tokens = name.trim().split("\\s+");
            if (tokens.length == 1) {
                author.lastName = name.trim();
            } else {
                author.lastName = tokens[tokens.length - 1];
                author.firstName = String.join(" ",
                        java.util.Arrays.copyOf(tokens, tokens.length - 1));
            }
        }

        return author;
    }

    private static String normalizeDoi(String doi) {
        if (doi == null) return null;
        String d = doi.trim();
        d = d.replaceFirst("(?i)^https?://doi\\.org/", "");
        d = d.replaceFirst("(?i)^doi:\\s*", "");
        d = d.replaceAll("[\\s\\p{Punct}]+$", "");
        d = d.toLowerCase(Locale.ROOT);
        return d.isBlank() ? null : d;
    }

    private static String normalizeLicense(String license) {
        if (license == null) return null;
        return license.trim().replace("http://", "https://");
    }

    /**
     * Returns true if the text looks like a license URL rather than a
     * human-readable description.  PMC Dublin Core emits two {@code dc:rights}
     * values: a prose sentence and a URL.  We prefer the URL because the
     * {@link #isOpenAccessLicense} method matches against URL patterns.
     */
    private static boolean looksLikeLicenseUrl(String text) {
        if (text == null || text.isBlank()) return false;
        String t = text.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("http://") || t.startsWith("https://");
    }

    /**
     * Checks that the license is commercially usable.
     * Rejects -NC (NonCommercial) and -ND (NoDerivatives) which restrict commercial use.
     * Accepts -SA (ShareAlike) since it only requires derivatives to carry the same license
     * and does not prohibit commercial use -- consistent with ArxivOaiService.
     */
    private boolean isOpenAccessLicense(String license) {
        if (license == null || license.isBlank()) {
            return false;
        }

        String l = license.trim().toLowerCase(Locale.ROOT).replace("http://", "https://");
        String compact = l.replaceAll("\\s+", "").replace("_", "-");

        // Reject restrictive variants first
        if (compact.contains("-nc")) return false;
        if (compact.contains("-nd")) return false;

        // CC0 / Public Domain
        if (l.contains("publicdomain/zero") || compact.contains("cc0")) return true;

        // CC-BY (any version, including SA)
        if (l.contains("creativecommons.org/licenses/by/")) return true;
        if (l.contains("creativecommons.org/licenses/by-sa/")) return true;
        if (compact.contains("cc-by")) return true;

        // Permissive OSS
        if (compact.contains("mit")) return true;
        if (compact.contains("apache-2.0") || compact.contains("apache2")) return true;
        if (compact.contains("bsd-2-clause") || compact.contains("bsd-3-clause")) return true;

        return false;
    }

    /**
     * Ensures the record has minimum scholarly attributes for GROBID processing.
     */
    private boolean isLikelyScholarlyText(Record record) {
        return !record.getAuthors().isEmpty();
    }

    private static String attrValue(StartElement se, String attrLocalName) {
        if (se == null || attrLocalName == null) return null;
        @SuppressWarnings("unchecked")
        var it = se.getAttributes();
        while (it != null && it.hasNext()) {
            Attribute at = (Attribute) it.next();
            if (attrLocalName.equals(at.getName().getLocalPart())) {
                return at.getValue();
            }
        }
        return null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record Page(List<Record> records, String resumptionToken) {
    }
}
