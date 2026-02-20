package com.data.oai.zenodo;

import com.data.oai.generic.common.dto.Author;
import com.data.oai.generic.common.dto.Record;
import com.data.config.ZenodoOaiProps;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.xml.namespace.QName;
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

@Service
@RequiredArgsConstructor
public class ZenodoOaiService {

    private final ZenodoOaiProps props;
    private final ZenodoClient zenodoClient;

    private final XMLInputFactory xml = newXmlFactory();

    public List<Record> getZenodoPapersMetadata(String from, String until) {
        List<Record> collected = new ArrayList<>();
        String token = null;

        do {
            byte[] body = zenodoClient.listRecords(props.baseUrl(), from, until, token, props.metadataPrefix());
            Page page = parseDatacite(body);

            collected.addAll(page.records);
            token = page.resumptionToken;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                continue;
            }
        } while (token != null && !token.isBlank());

        return collected;
    }

    public AbstractMap.SimpleEntry<ZenodoRecord, byte[]> getPdf(String zenodoRecordId) {
        ZenodoRecord rec = zenodoClient.getRecord(zenodoRecordId);
        if (rec == null) throw new RuntimeException("No record found for recordId %s".formatted(zenodoRecordId));
        if (!ZenodoRecordFilePicker.acceptForGrobid(rec)) {
            throw new RuntimeException("Record not acceptable for grobid %s".formatted(zenodoRecordId));
        }
        ZenodoRecord.FileEntry pdf = ZenodoRecordFilePicker.pickPdfUrl(rec);
        if (pdf == null || pdf.getLinks() == null || pdf.getLinks().getSelf() == null) {
            throw new RuntimeException("No pdf links found for grobid processing %s".formatted(zenodoRecordId));
        }
        return new AbstractMap.SimpleEntry<>(rec, zenodoClient.downloadFile(pdf.getLinks().getSelf()));
    }

    private static XMLInputFactory newXmlFactory() {
        XMLInputFactory f = XMLInputFactory.newFactory();
        try {
            // DataCite tends to behave better with namespaces enabled.
            f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        } catch (IllegalArgumentException ignored) {
        }
        return f;
    }

    /**
     * Parses Zenodo OAI-PMH with metadataPrefix=oai_datacite and maps into your existing ArxivRecord.
     *
     * Mapping:
     * - sourceIdentifier: OAI header identifier (oai:zenodo.org:<recid>)
     * - datestamp: OAI header datestamp
     * - sourceId: Zenodo recid (suffix of sourceIdentifier)
     * - doi: DataCite identifier with identifierType=DOI (normalized)
     * - license: DataCite rightsURI (preferred) or rights text (normalized)
     * - categories: DataCite subjects
     * - authors: DataCite creators (family/given if present; else creatorName split)
     */
    private Page parseDatacite(byte[] xmlBytes) {
        try {
            XMLEventReader r = xml.createXMLEventReader(new ByteArrayInputStream(xmlBytes));
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

            while (r.hasNext()) {
                XMLEvent ev = r.nextEvent();

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
                                identifierType = attrValue(se, "identifierType");
                                tag = "dataciteIdentifier";
                            }
                        }
                        case "datestamp" -> {
                            if (inHeader) tag = "datestamp";
                        }

                        case "creator" -> {
                            if (inMetadata && cur != null) {
                                inCreator = true;
                                creatorName = null;
                                givenName = null;
                                familyName = null;
                            }
                        }
                        case "creatorName" -> {
                            if (inCreator) tag = "creatorName";
                        }
                        case "givenName" -> {
                            if (inCreator) tag = "givenName";
                        }
                        case "familyName" -> {
                            if (inCreator) tag = "familyName";
                        }
                        case "subject" -> {
                            if (inMetadata) tag = "subject";
                        }
                        case "rights" -> {
                            if (inMetadata && cur != null) {
                                // Prefer rightsURI if present
                                String rightsUri = attrValue(se, "rightsURI");
                                if (rightsUri != null && !rightsUri.isBlank() && cur.getLicense() == null) {
                                    cur.setLicense(rightsUri);
                                }
                                tag = "rightsText";
                            }
                        }

                        case "description" -> {
                            if (inMetadata) {
                                tag = "description";
                            }
                        }
                        case "title" -> {
                            if (inMetadata) tag = "title";
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
                        case "token" -> resumptionToken = text;
                        case "headerIdentifier" -> {
                            cur.setOaiIdentifier(text);
                        }
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
                                cur.setDoi(normalizeDoi(text));
                            }
                        }

                        case "description" -> {
                                if (abstractDescription == null) abstractDescription = new StringBuilder(text);
                                else abstractDescription.append(text);
                        }
                    }
                }

                if (ev.isEndElement()) {
                    String name = ev.asEndElement().getName().getLocalPart();

                    switch (name) {
                        case "header" -> inHeader = false;
                        case "metadata" -> inMetadata = false;

                        case "identifier" -> {
                            // end of datacite identifier
                            identifierType = null;
                            if ("dataciteIdentifier".equals(tag)) tag = null;
                            if ("headerIdentifier".equals(tag)) tag = null;
                        }

                        case "creator" -> {
                            if (inCreator) {
                                inCreator = false;
                                if (cur != null) {
                                    Author a = new Author();

                                    if (familyName != null || givenName != null) {
                                        a.lastName = familyName;
                                        a.firstName = givenName;
                                    } else if (creatorName != null) {
                                        String[] parts = creatorName.split(",", 2);
                                        if (parts.length == 2) {
                                            a.lastName = parts[0].trim();
                                            a.firstName = parts[1].trim();
                                        } else {
                                            String[] tokens = creatorName.trim().split("\\s+");
                                            if (tokens.length == 1) {
                                                a.lastName = creatorName;
                                            } else {
                                                a.lastName = tokens[tokens.length - 1];
                                                a.firstName = String.join(" ", java.util.Arrays.copyOf(tokens, tokens.length - 1));
                                            }
                                        }
                                    }

                                    cur.getAuthors().add(a);
                                }
                                // clear tag if it was inside creator fields
                                if ("creatorName".equals(tag) || "givenName".equals(tag) || "familyName".equals(tag)) {
                                    tag = null;
                                }
                            }
                        }

                        case "rights" -> {
                            if ("rightsText".equals(tag)) tag = null;
                        }

                        case "description" -> {
                            if ("description".equals(tag)) tag = null;
                        }

                        case "datestamp" -> {
                            if ("datestamp".equals(tag)) tag = null;
                        }

                        case "resumptionToken" -> {
                            if ("token".equals(tag)) tag = null;
                        }

                        case "record" -> {
                            if (cur != null) {
                                if (cur.getComments() == null) {
                                    if (abstractDescription != null && !abstractDescription.toString().isBlank()) cur.setComments(abstractDescription.toString());
                                }

                                if (cur.getLicense() != null) cur.setLicense(normalizeLicense(cur.getLicense()));

                                if (isCommerciallySafeForResale(cur.getLicense()) && isLikelyScholarlyText(cur)) {
                                    records.add(cur);
                                }
                            }
                            cur = null;

                            // clear record-level temps
                            abstractDescription = null;
                        }
                    }

                    // generic tag reset for simple tags that match end element names
                    if (tag != null && tag.equals(name)) {
                        tag = null;
                    }
                }
            }

            return new Page(records, resumptionToken);
        } catch (Exception e) {
            throw new RuntimeException("Zenodo OAI parse failed", e);
        }
    }

    private static String normalizeDoi(String doi) {
        if (doi == null) return null;
        String d = doi.trim();
        return d.isEmpty() ? null : d;
    }

    private static String normalizeLicense(String license) {
        if (license == null) return null;
        return license.trim().replace("http://", "https://");
    }

    private boolean isLikelyScholarlyText(Record r) {
        if (r.getAuthors().isEmpty()) return false;
        if (r.getCategories().isEmpty()) return false;
        return true;
    }

    private boolean isCommerciallySafeForResale(String license) {
        if (license == null || license.isBlank()) {
            return false;
        }

        String l = license.trim()
                .toLowerCase(Locale.ROOT)
                .replace("http://", "https://");

        String compact = l.replaceAll("\\s+", "")
                .replace("_", "-");

        // ---------------------------------------------------
        // EXPLICITLY REJECT
        // ---------------------------------------------------
        if (compact.contains("-nc")) return false;
        if (compact.contains("-nd")) return false;
        if (compact.contains("-sa")) return false;
        if (compact.contains("gpl")) return false;
        if (compact.contains("agpl")) return false;
        if (compact.contains("lgpl")) return false;

        // ---------------------------------------------------
        // SAFE CREATIVE COMMONS
        // ---------------------------------------------------
        if (l.contains("creativecommons.org/publicdomain/zero/1.0"))
            return true;
        if (compact.equals("cc0") || compact.contains("cc0-1.0"))
            return true;
        if (l.contains("creativecommons.org/licenses/by/4.0"))
            return true;

        if (compact.contains("cc-by-4.0"))
            return true;

        // CC BY 3.0
        if (l.contains("creativecommons.org/licenses/by/3.0"))
            return true;

        if (compact.contains("cc-by-3.0"))
            return true;

        // ---------------------------------------------------
        // PERMISSIVE OSS LICENSES
        // ---------------------------------------------------

        if (compact.contains("mit"))
            return true;

        if (compact.contains("apache-2.0") || compact.contains("apache2"))
            return true;

        if (compact.contains("bsd-2-clause") || compact.contains("bsd-3-clause"))
            return true;

        if (compact.contains("isc"))
            return true;

        return false;
    }

    private static String attrValue(StartElement se, String attrLocalName) {
        if (se == null || attrLocalName == null) return null;

        // Try without namespace
        Attribute a = se.getAttributeByName(new QName(attrLocalName));
        if (a != null) return a.getValue();

        // Some parsers treat attributes with namespaces differently; try any attr that matches local part
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

    private record Page(List<Record> records, String resumptionToken) {
    }
}
