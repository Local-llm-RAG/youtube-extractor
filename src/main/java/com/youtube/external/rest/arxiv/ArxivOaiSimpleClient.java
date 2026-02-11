package com.youtube.external.rest.arxiv;

import com.youtube.config.ArxivOaiProps;
import com.youtube.external.rest.arxiv.dto.ArxivAuthor;
import com.youtube.external.rest.arxiv.dto.ArxivRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ArxivOaiSimpleClient {
    private final ArxivOaiProps props;
    private final RestClient rest;

    // IMPORTANT: make namespace handling predictable for arXiv default namespaces
    private final XMLInputFactory xml = newXmlFactory();

    private static XMLInputFactory newXmlFactory() {
        XMLInputFactory f = XMLInputFactory.newFactory();
        try {
            f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        } catch (IllegalArgumentException ignored) {
            // Some StAX impls may not support toggling; safe to ignore.
        }
        return f;
    }

    public List<ArxivRecord> listRecords(String from, String until) {
        List<ArxivRecord> out = new ArrayList<>();
        String token = null;

        do {
            URI uri = buildUri(from, until, token);

            byte[] body = rest.get()
                    .uri(uri)
                    .retrieve()
                    .body(byte[].class);

            Page page = parse(body);
            out.addAll(page.records);
            token = page.resumptionToken;

        } while (token != null && !token.isBlank());

        return out;
    }

    private URI buildUri(String from, String until, String token) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromUriString(props.baseUrl())
                .queryParam("verb", "ListRecords");

        if (token == null) {
            b.queryParam("metadataPrefix", "arXiv")
                    .queryParam("from", from)
                    .queryParam("until", until);
        } else {
            b.queryParam("resumptionToken", token);
        }

        return b.build(true).toUri();
    }

    private Page parse(byte[] xmlBytes) {
        try {
            XMLEventReader r = xml.createXMLEventReader(new ByteArrayInputStream(xmlBytes));
            List<ArxivRecord> records = new ArrayList<>();

            ArxivRecord cur = null;

            boolean inHeader = false;
            boolean inMetadata = false;

            String tag = null;
            String resumptionToken = null;

            while (r.hasNext()) {
                XMLEvent ev = r.nextEvent();

                if (ev.isStartElement()) {
                    String name = ev.asStartElement().getName().getLocalPart();

                    switch (name) {
                        case "record" -> cur = new ArxivRecord();
                        case "header" -> inHeader = true;
                        case "metadata" -> inMetadata = true;
                        case "resumptionToken" -> tag = "token";
                        case "identifier", "datestamp" -> {
                            if (inHeader) tag = name;
                        }
                        case "title", "abstract", "categories", "comments", "journal-ref", "doi", "license", "keyname",
                             "forenames" -> {
                            if (inMetadata) tag = name;
                        }
                        case "author" -> {
                            if (inMetadata && cur != null) cur.getAuthors().add(new ArxivAuthor());
                        }
                        default -> {
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
                        case "title" -> cur.setTitle(text);
                        case "abstract" -> {
                            if (cur.getAbstractText() == null) {
                                cur.setAbstractText(text);
                            } else {
                                cur.setAbstractText(cur.getAbstractText() + " " + text);
                            }
                        }
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

    public byte[] getPdf(String arxivId) {
        URI pdfUri = URI.create("https://arxiv.org/pdf/" + arxivId + ".pdf");
        return rest.get().uri(pdfUri).retrieve().body(byte[].class);
    }

    public byte[] getEText(String arxivId) {
        URI srcUri = URI.create("https://arxiv.org/e-print/" + arxivId);
        return rest.get().uri(srcUri).retrieve().body(byte[].class);
    }

    private record Page(List<ArxivRecord> records, String resumptionToken) {
    }
}
