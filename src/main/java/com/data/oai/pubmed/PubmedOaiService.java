package com.data.oai.pubmed;

import com.data.config.properties.PubmedOaiProps;
import com.data.oai.pubmed.oa.OaLink;
import com.data.shared.DataSource;
import com.data.oai.pubmed.oa.OaRecord;
import com.data.oai.pubmed.oa.OaResponse;
import com.data.oai.shared.AbstractOaiService;
import com.data.oai.shared.util.LicenseFilter;
import com.data.oai.shared.util.XmlFactories;
import com.data.oai.shared.dto.OaiPage;
import com.data.oai.shared.util.AuthorNameParser;
import com.data.oai.shared.util.DoiNormalizer;
import com.data.oai.shared.dto.PdfContent;
import com.data.oai.shared.dto.Record;
import com.data.shared.exception.OaiParseException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PMC OAI-PMH harvester using Dublin Core metadata prefix.
 * Only records from the {@code pmc-open} set are harvested.
 */
@Slf4j
@Service
public class PubmedOaiService extends AbstractOaiService {

    private final PubmedOaiProps props;
    private final PubmedClient pubmedClient;

    private final XMLInputFactory xml = XmlFactories.newFactory(true);
    private static final JAXBContext OA_JAXB_CTX = initOaJaxbContext();

    private static final Pattern DOI_PATTERN =
            Pattern.compile("(?:https?://doi\\.org/|doi:\\s*)(10\\..+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PMC_ID_PATTERN = Pattern.compile("PMC(\\d+)");

    private static final int TAR_BLOCK_SIZE = 512;
    private static final int TAR_NAME_OFFSET = 0;
    private static final int TAR_NAME_LENGTH = 100;
    private static final int TAR_SIZE_OFFSET = 124;
    private static final int TAR_SIZE_LENGTH = 12;

    public PubmedOaiService(PubmedOaiProps props, PubmedClient pubmedClient) {
        this.props = props;
        this.pubmedClient = pubmedClient;
    }

    private static JAXBContext initOaJaxbContext() {
        try {
            return JAXBContext.newInstance(OaResponse.class);
        } catch (JAXBException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // ── AbstractOaiService hooks ─────────────────────────────────────

    @Override public DataSource supports() { return DataSource.PUBMED; }
    @Override protected String sourceName() { return "PMC"; }
    @Override protected long paginationDelayMs() { return props.paginationDelayMs(); }

    @Override
    protected byte[] callListRecords(String from, String until, String token) {
        return pubmedClient.listRecords(
                props.baseUrl(), from, until, token,
                props.metadataPrefix(), props.set());
    }

    @Override
    protected OaiPage parseResponse(byte[] xmlBytes) {
        return parseDublinCore(xmlBytes);
    }

    // ── PDF resolution (OA Web Service + PDF/tgz fallback) ───────────

    @Override
    public PdfContent getPdf(String pmcNumericId) {
        String pmcId = "PMC" + pmcNumericId;
        OaLinks links = resolveOaLinks(pmcId);

        if (links.pdfUrl != null) {
            try {
                byte[] pdfBytes = pubmedClient.downloadPdf(links.pdfUrl);
                if (isPdf(pdfBytes)) {
                    return new PdfContent(links.pdfUrl, pdfBytes);
                }
                log.warn("Direct PDF link for {} did not return valid PDF", pmcId);
            } catch (Exception e) {
                log.warn("Failed to download PDF for {}: {}", pmcId, e.getMessage());
            }
        }

        if (links.tgzUrl != null) {
            try {
                byte[] tgzBytes = pubmedClient.downloadPdf(links.tgzUrl);
                byte[] pdfBytes = extractPdfFromTgz(tgzBytes);
                if (pdfBytes != null) {
                    return new PdfContent(links.tgzUrl, pdfBytes);
                }
                log.warn("No PDF found inside tgz archive for {}", pmcId);
            } catch (Exception e) {
                log.warn("Failed to download/extract tgz for {}: {}", pmcId, e.getMessage());
            }
        }

        log.info("Skipping {} — not available for bulk download (likely not in OA subset)", pmcId);
        return null;
    }

    private OaLinks resolveOaLinks(String pmcId) {
        try {
            byte[] oaResponse = pubmedClient.fetchOaLinks(pmcId);
            Unmarshaller unmarshaller = OA_JAXB_CTX.createUnmarshaller();
            OaResponse response = (OaResponse) unmarshaller.unmarshal(new ByteArrayInputStream(oaResponse));

            if (response.getRecords() == null || response.getRecords().getRecords() == null) {
                return new OaLinks(null, null);
            }

            OaRecord record = response.getRecords().getRecords().stream().findFirst().orElse(null);
            if (record == null) {
                return new OaLinks(null, null);
            }
            if (record.isRetracted()) {
                log.info("Skipping retracted article {}", pmcId);
                return new OaLinks(null, null);
            }

            String pdfUrl = null;
            String tgzUrl = null;
            if (record.getLinks() != null) {
                for (OaLink link : record.getLinks()) {
                    if ("pdf".equalsIgnoreCase(link.getFormat())) pdfUrl = link.getHref();
                    else if ("tgz".equalsIgnoreCase(link.getFormat())) tgzUrl = link.getHref();
                }
            }
            return new OaLinks(pdfUrl, tgzUrl);
        } catch (Exception e) {
            log.warn("OA Web Service call failed for {}", pmcId, e);
            return new OaLinks(null, null);
        }
    }

    private byte[] extractPdfFromTgz(byte[] tgzBytes) {
        try (var gzipIn = new java.util.zip.GZIPInputStream(new ByteArrayInputStream(tgzBytes))) {
            byte[] tarBytes = gzipIn.readAllBytes();
            int offset = 0;
            while (offset + TAR_BLOCK_SIZE <= tarBytes.length) {
                String name = new String(tarBytes, offset + TAR_NAME_OFFSET, TAR_NAME_LENGTH, java.nio.charset.StandardCharsets.US_ASCII).trim();
                if (name.isEmpty()) break;

                String sizeStr = new String(tarBytes, offset + TAR_SIZE_OFFSET, TAR_SIZE_LENGTH, java.nio.charset.StandardCharsets.US_ASCII).trim();
                if (sizeStr.isEmpty()) break;
                long size = Long.parseLong(sizeStr, 8);

                int dataOffset = offset + TAR_BLOCK_SIZE;
                if (name.toLowerCase(Locale.ROOT).endsWith(".pdf") && size > 0) {
                    byte[] pdfBytes = new byte[(int) size];
                    System.arraycopy(tarBytes, dataOffset, pdfBytes, 0, (int) size);
                    return pdfBytes;
                }

                offset = dataOffset + (int) ((size + TAR_BLOCK_SIZE - 1) / TAR_BLOCK_SIZE * TAR_BLOCK_SIZE);
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

    // ── Source-specific XML parsing (Dublin Core) ────────────────────

    private OaiPage parseDublinCore(byte[] xmlBytes) {
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
                        case "datestamp" -> { if (inHeader) tag = "datestamp"; }
                        case "creator" -> { if (inMetadata) tag = "creator"; }
                        case "subject" -> { if (inMetadata) tag = "subject"; }
                        case "title" -> { if (inMetadata) tag = "title"; }
                        case "description" -> { if (inMetadata) tag = "description"; }
                        case "rights" -> { if (inMetadata) tag = "rights"; }
                        case "source" -> { if (inMetadata) tag = "source"; }
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
                        case "headerIdentifier" -> cur.setExternalIdentifier(text);
                        case "datestamp" -> cur.setDatestamp(text);
                        case "title" -> cur.setTitle(text);
                        case "creator" -> cur.getAuthors().add(AuthorNameParser.parse(text));
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
                            } else if (LicenseFilter.looksLikeLicenseUrl(text)) {
                                cur.setLicense(text);
                            }
                        }
                        case "source" -> { if (cur.getJournalRef() == null) cur.setJournalRef(text); }
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
                                    cur.setAbstractText(descriptionBuilder.toString());
                                    cur.setComments(descriptionBuilder.toString());
                                }
                                if (cur.getLicense() != null) {
                                    cur.setLicense(LicenseFilter.normalizeLicense(cur.getLicense()));
                                }
                                if (cur.getLicense() == null) {
                                    cur.setLicense(LicenseFilter.DEFAULT_OPEN_ACCESS_LICENSE);
                                }
                                if (LicenseFilter.isPermissiveLicense(cur.getLicense(), true, false)
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

            return new OaiPage(records, resumptionToken);
        } catch (Exception e) {
            throw new OaiParseException("PMC OAI-PMH parse failed", e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void handleDcIdentifier(Record record, String text) {
        Matcher doiMatcher = DOI_PATTERN.matcher(text);
        if (doiMatcher.find()) {
            record.setDoi(DoiNormalizer.normalize(doiMatcher.group(1)));
            return;
        }

        Matcher pmcMatcher = PMC_ID_PATTERN.matcher(text);
        if (pmcMatcher.find()) {
            return;
        }
    }

    private boolean isLikelyScholarlyText(Record record) {
        return !record.getAuthors().isEmpty();
    }
}
