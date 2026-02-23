package com.data.oai.arxiv;

import com.data.config.properties.ArxivOaiProps;
import com.data.oai.generic.common.dto.Author;
import com.data.oai.generic.common.dto.Record;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ArxivOaiService {
    private final ArxivOaiProps props;
    private final ArxivClient arxivClient;

    private final XMLInputFactory xml = newXmlFactory();

    public List<Record> getArxivPapersMetadata(String from, String until) {
        List<Record> collectedRecords = new ArrayList<>();
        String token = null;

        do {
            byte[] body = arxivClient.listRecords(props.baseUrl(), from, until, token);

            Page page = parse(body);
            collectedRecords.addAll(page.records);
            token = page.resumptionToken;

        } while (token != null && !token.isBlank());

        return collectedRecords;
    }

    public AbstractMap.SimpleEntry<String, byte[]> getPdf(String arxivId) {
        String pdfUrl = "https://arxiv.org/pdf/" + arxivId + ".pdf";
        return new AbstractMap.SimpleEntry<>(pdfUrl, arxivClient.getPdf(pdfUrl));
    }

    public byte[] getEText(String arxivId) {
        return arxivClient.getEText(arxivId);
    }

    private static XMLInputFactory newXmlFactory() {
        XMLInputFactory f = XMLInputFactory.newFactory();
        try {
            // Make namespace handling predictable for arXiv default namespaces
            f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        } catch (IllegalArgumentException ignored) {
        }
        return f;
    }

    private Page parse(byte[] xmlBytes) {
        try {
            XMLEventReader r = xml.createXMLEventReader(new ByteArrayInputStream(xmlBytes));
            List<Record> records = new ArrayList<>();

            Record cur = null;

            boolean inHeader = false;
            boolean inMetadata = false;

            String tag = null;
            String resumptionToken = null;

            while (r.hasNext()) {
                XMLEvent ev = r.nextEvent();

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
                        case "id", "title", "abstract", "categories", "comments", "journal-ref", "doi", "license", "keyname",
                             "forenames" -> {
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

                    if (cur == null && !"token".equals(tag)) {
                        continue;
                    }

                    switch (tag) {
                        case "identifier" -> cur.setOaiIdentifier(text);
                        case "datestamp" -> cur.setDatestamp(text);
                        case "categories" -> cur.getCategories().addAll(List.of(text.split("\\s+")));
                        case "comments" -> cur.setComments(text);
                        case "journal-ref" -> cur.setJournalRef(text);
                        case "doi" -> cur.setDoi(text);
                        case "license" -> cur.setLicense(text);
                        case "keyname" -> {
                            if (!cur.getAuthors().isEmpty()) {
                                cur.lastAuthor().lastName = text;
                            }
                        }
                        case "forenames" -> {
                            if (!cur.getAuthors().isEmpty()) {
                                cur.lastAuthor().firstName = text;
                            }
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
                        if (cur != null && isCommerciallyUsableLicense(cur.getLicense())) {
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

            return new Page(records, resumptionToken);
        } catch (Exception e) {
            throw new RuntimeException("OAI parse failed", e);
        }
    }

    private boolean isCommerciallyUsableLicense(String license) {
        if (license == null || license.isBlank()) {
            return false;
        }

        String l = license.trim().toLowerCase(Locale.ROOT);

        // Normalize http/https
        l = l.replace("http://", "https://");

        // Explicit whitelist
        return l.equals("https://creativecommons.org/licenses/by/4.0/")
                || l.equals("https://creativecommons.org/licenses/by-sa/4.0/")
                || l.equals("https://creativecommons.org/licenses/by-nd/4.0/") // only if you decide ND is acceptable
                || l.equals("https://creativecommons.org/publicdomain/zero/1.0/");
    }

    private record Page(List<Record> records, String resumptionToken) {
    }
}
