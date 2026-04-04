package com.data.oai.zenodo;

import com.data.config.properties.ZenodoOaiProps;
import com.data.oai.pipeline.DataSource;
import com.data.oai.shared.AbstractOaiService;
import com.data.oai.shared.util.LicenseFilter;
import com.data.oai.shared.util.XmlFactories;
import com.data.oai.shared.dto.Author;
import com.data.oai.shared.dto.OaiPage;
import com.data.oai.shared.dto.PdfContent;
import com.data.oai.shared.dto.Record;
import com.data.oai.shared.util.AuthorNameParser;
import com.data.oai.shared.util.DoiNormalizer;
import com.data.shared.exception.OaiParseException;
import com.data.shared.exception.PdfDownloadException;
import com.data.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class ZenodoOaiService extends AbstractOaiService {

    private final ZenodoOaiProps props;
    private final ZenodoClient zenodoClient;
    private final XMLInputFactory xml = XmlFactories.newFactory(true);

    public ZenodoOaiService(ZenodoOaiProps props, ZenodoClient zenodoClient) {
        this.props = props;
        this.zenodoClient = zenodoClient;
    }

    @Override public DataSource supports() { return DataSource.ZENODO; }
    @Override protected String sourceName() { return "Zenodo"; }
    @Override protected long paginationDelayMs() { return props.paginationDelayMs(); }

    @Override
    protected byte[] callListRecords(String from, String until, String token) {
        return zenodoClient.listRecords(props.baseUrl(), from, until, token, props.metadataPrefix());
    }

    @Override
    protected OaiPage parseResponse(byte[] xmlBytes) {
        return parseDatacite(xmlBytes);
    }

    @Override
    public PdfContent getPdf(String sourceId) {
        ZenodoRecord rec = zenodoClient.getRecord(sourceId);
        if (rec == null) {
            throw new ResourceNotFoundException("No record found for recordId %s".formatted(sourceId));
        }
        if (!ZenodoRecordFilePicker.acceptForGrobid(rec)) {
            throw new PdfDownloadException("Record not acceptable for grobid %s".formatted(sourceId));
        }

        ZenodoRecord.FileEntry pdf = ZenodoRecordFilePicker.pickPdfUrl(rec);
        if (pdf == null || pdf.getLinks() == null || pdf.getLinks().getSelf() == null) {
            throw new PdfDownloadException("No pdf links found for grobid processing %s".formatted(sourceId));
        }

        String pdfUrl = pdf.getLinks().getSelf();
        return new PdfContent(pdfUrl, zenodoClient.downloadFile(pdfUrl));
    }

    // ── Source-specific XML parsing (DataCite) ───────────────────────

    private OaiPage parseDatacite(byte[] xmlBytes) {
        XMLEventReader reader = null;
        try {
            reader = xml.createXMLEventReader(new ByteArrayInputStream(xmlBytes));
            List<Record> records = new ArrayList<>();

            Record cur = null;
            boolean inHeader = false;
            boolean inMetadata = false;
            boolean inCreator = false;

            String tag = null;
            String resumptionToken = null;

            String creatorName = null;
            String givenName = null;
            String familyName = null;
            String identifierType = null;

            StringBuilder abstractDescription = null;

            while (reader.hasNext()) {
                XMLEvent ev = reader.nextEvent();

                if (ev.isStartElement()) {
                    StartElement se = ev.asStartElement();
                    String name = se.getName().getLocalPart();

                    switch (name) {
                        case "record" -> {
                            cur = new Record();
                            abstractDescription = null;
                        }
                        case "header" -> inHeader = true;
                        case "metadata" -> inMetadata = true;
                        case "resumptionToken" -> tag = "token";
                        case "identifier" -> {
                            if (inHeader) {
                                tag = "headerIdentifier";
                            } else if (inMetadata) {
                                identifierType = XmlFactories.attrValue(se, "identifierType");
                                tag = "dataciteIdentifier";
                            }
                        }
                        case "datestamp" -> { if (inHeader) tag = "datestamp"; }
                        case "creator" -> {
                            if (inMetadata && cur != null) {
                                inCreator = true;
                                creatorName = null;
                                givenName = null;
                                familyName = null;
                            }
                        }
                        case "creatorName" -> { if (inCreator) tag = "creatorName"; }
                        case "givenName" -> { if (inCreator) tag = "givenName"; }
                        case "familyName" -> { if (inCreator) tag = "familyName"; }
                        case "subject" -> { if (inMetadata) tag = "subject"; }
                        case "rights" -> {
                            if (inMetadata && cur != null) {
                                String rightsUri = XmlFactories.attrValue(se, "rightsURI");
                                if (rightsUri != null && !rightsUri.isBlank() && cur.getLicense() == null) {
                                    cur.setLicense(rightsUri);
                                }
                                tag = "rightsText";
                            }
                        }
                        case "description" -> { if (inMetadata) tag = "description"; }
                        case "title" -> { if (inMetadata) tag = "title"; }
                    }
                }

                if (ev.isCharacters() && tag != null) {
                    String text = ev.asCharacters().getData();
                    if (text == null) continue;
                    text = text.trim();
                    if (text.isEmpty()) continue;

                    if (cur == null && !"token".equals(tag)) continue;

                    switch (tag) {
                        case "token" -> resumptionToken = text;
                        case "headerIdentifier" -> cur.setOaiIdentifier(text);
                        case "datestamp" -> cur.setDatestamp(text);
                        case "creatorName" -> creatorName = text;
                        case "givenName" -> givenName = text;
                        case "familyName" -> familyName = text;
                        case "subject" -> cur.getCategories().add(text);
                        case "rightsText" -> {
                            if (cur.getLicense() == null) cur.setLicense(text);
                        }
                        case "dataciteIdentifier" -> {
                            if ("DOI".equalsIgnoreCase(identifierType)) {
                                cur.setDoi(DoiNormalizer.normalize(text));
                            }
                        }
                        case "title" -> cur.setTitle(text);
                        case "description" -> {
                            if (abstractDescription == null) {
                                abstractDescription = new StringBuilder(text);
                            } else {
                                abstractDescription.append(" ").append(text);
                            }
                        }
                    }
                }

                if (ev.isEndElement()) {
                    String name = ev.asEndElement().getName().getLocalPart();

                    switch (name) {
                        case "header" -> inHeader = false;
                        case "metadata" -> inMetadata = false;
                        case "identifier" -> {
                            identifierType = null;
                            if ("dataciteIdentifier".equals(tag)) tag = null;
                            if ("headerIdentifier".equals(tag)) tag = null;
                        }
                        case "creator" -> {
                            if (inCreator) {
                                inCreator = false;
                                if (cur != null) {
                                    Author a;
                                    if (familyName != null || givenName != null) {
                                        a = new Author();
                                        a.lastName = familyName;
                                        a.firstName = givenName;
                                    } else if (creatorName != null) {
                                        a = AuthorNameParser.parse(creatorName);
                                    } else {
                                        continue;
                                    }
                                    cur.getAuthors().add(a);
                                }
                                if ("creatorName".equals(tag) || "givenName".equals(tag) || "familyName".equals(tag)) {
                                    tag = null;
                                }
                            }
                        }
                        case "rights" -> { if ("rightsText".equals(tag)) tag = null; }
                        case "description" -> { if ("description".equals(tag)) tag = null; }
                        case "datestamp" -> { if ("datestamp".equals(tag)) tag = null; }
                        case "resumptionToken" -> { if ("token".equals(tag)) tag = null; }
                        case "record" -> {
                            if (cur != null) {
                                if (abstractDescription != null && !abstractDescription.toString().isBlank()) {
                                    cur.setAbstractText(abstractDescription.toString());
                                    if (cur.getComments() == null) {
                                        cur.setComments(abstractDescription.toString());
                                    }
                                }

                                if (cur.getLicense() != null) {
                                    cur.setLicense(LicenseFilter.normalizeLicense(cur.getLicense()));
                                }

                                if (LicenseFilter.isPermissiveLicense(cur.getLicense(), true, false)
                                        && isLikelyScholarlyText(cur)) {
                                    records.add(cur);
                                }
                            }
                            cur = null;
                            abstractDescription = null;
                        }
                    }

                    if (tag != null && tag.equals(name)) {
                        tag = null;
                    }
                }
            }

            return new OaiPage(records, resumptionToken);
        } catch (Exception e) {
            throw new OaiParseException("Zenodo OAI parse failed", e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
    }

    private boolean isLikelyScholarlyText(Record r) {
        return !r.getAuthors().isEmpty();
    }
}
