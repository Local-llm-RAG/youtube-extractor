package com.data.oai.arxiv;

import com.data.config.properties.ArxivOaiProps;
import com.data.oai.pipeline.DataSource;
import com.data.oai.shared.AbstractOaiService;
import com.data.oai.shared.util.LicenseFilter;
import com.data.oai.shared.util.XmlFactories;
import com.data.oai.shared.dto.Author;
import com.data.oai.shared.dto.OaiPage;
import com.data.oai.shared.dto.PdfContent;
import com.data.oai.shared.dto.Record;
import com.data.shared.exception.OaiParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ArxivOaiService extends AbstractOaiService {

    private final ArxivOaiProps props;
    private final ArxivClient arxivClient;
    private final XMLInputFactory xml = XmlFactories.newFactory(false);

    public ArxivOaiService(ArxivOaiProps props, ArxivClient arxivClient) {
        this.props = props;
        this.arxivClient = arxivClient;
    }

    @Override
    public DataSource supports() {
        return DataSource.ARXIV;
    }

    @Override
    protected String sourceName() {
        return "ArXiv";
    }

    @Override
    protected long paginationDelayMs() {
        return props.paginationDelayMs();
    }

    @Override
    protected byte[] callListRecords(String from, String until, String token) {
        return arxivClient.listRecords(props.baseUrl(), from, until, token, props.metadataPrefix());
    }

    @Override
    protected OaiPage parseResponse(byte[] xmlBytes) {
        return parse(xmlBytes);
    }

    @Override
    public PdfContent getPdf(String sourceId) {
        String pdfUrl = props.pdfBaseUrl() + sourceId + ".pdf";
        try {
            return new PdfContent(pdfUrl, arxivClient.downloadFile(pdfUrl));
        } catch (Exception e) {
            log.warn("PDF not available for ArXiv {}: {}", sourceId, e.getMessage());
            return null;
        }
    }

    // ── Source-specific XML parsing ──────────────────────────────────

    private OaiPage parse(byte[] xmlBytes) {
        XMLEventReader reader = null;
        try {
            reader = xml.createXMLEventReader(new ByteArrayInputStream(xmlBytes));
            List<Record> records = new ArrayList<>();

            Record cur = null;
            boolean inHeader = false;
            boolean inMetadata = false;
            String tag = null;
            String resumptionToken = null;

            while (reader.hasNext()) {
                XMLEvent ev = reader.nextEvent();

                if (ev.isStartElement()) {
                    String name = ev.asStartElement().getName().getLocalPart();

                    switch (name) {
                        case "record" -> cur = new Record();
                        case "header" -> inHeader = true;
                        case "metadata" -> inMetadata = true;
                        case "resumptionToken" -> tag = "token";
                        case "identifier", "datestamp" -> {
                            if (inHeader) tag = name;
                        }
                        case "id", "title", "abstract", "categories", "comments",
                             "journal-ref", "doi", "license", "keyname", "forenames" -> {
                            if (inMetadata) tag = name;
                        }
                        case "author" -> {
                            if (inMetadata && cur != null) cur.getAuthors().add(new Author());
                        }
                    }
                }

                if (ev.isCharacters() && tag != null) {
                    String text = ev.asCharacters().getData();
                    if (text == null) continue;
                    text = text.trim();
                    if (text.isEmpty()) continue;

                    if (cur == null && !"token".equals(tag)) continue;

                    switch (tag) {
                        case "identifier" -> cur.setOaiIdentifier(text);
                        case "datestamp" -> cur.setDatestamp(text);
                        case "categories" -> cur.getCategories().addAll(List.of(text.split("\\s+")));
                        case "comments" -> cur.setComments(text);
                        case "journal-ref" -> cur.setJournalRef(text);
                        case "doi" -> cur.setDoi(text);
                        case "license" -> cur.setLicense(text);
                        case "keyname" -> {
                            if (!cur.getAuthors().isEmpty()) cur.lastAuthor().lastName = text;
                        }
                        case "forenames" -> {
                            if (!cur.getAuthors().isEmpty()) cur.lastAuthor().firstName = text;
                        }
                        case "token" -> resumptionToken = text;
                        case "id" -> cur.setSourceId(text);
                    }
                }

                if (ev.isEndElement()) {
                    String name = ev.asEndElement().getName().getLocalPart();

                    if ("header".equals(name)) inHeader = false;
                    if ("metadata".equals(name)) inMetadata = false;

                    if ("record".equals(name)) {
                        if (cur != null && LicenseFilter.isAcceptableByUrlWhitelist(cur.getLicense())) {
                            records.add(cur);
                        }
                        cur = null;
                    }

                    if ("resumptionToken".equals(name) && "token".equals(tag)) {
                        tag = null;
                    } else if (name.equals(tag)) {
                        tag = null;
                    }
                }
            }

            return new OaiPage(records, resumptionToken);
        } catch (Exception e) {
            throw new OaiParseException("ArXiv OAI parse failed", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
