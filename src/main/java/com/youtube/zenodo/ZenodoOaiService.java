package com.youtube.zenodo;

import com.youtube.arxiv.oai.dto.ArxivAuthor;
import com.youtube.arxiv.oai.dto.ArxivRecord;
import com.youtube.config.ZenodoOaiProps;
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

    public List<ArxivRecord> getZenodoPapersMetadata(String from, String until) {
        List<ArxivRecord> collected = new ArrayList<>();
        String token = null;

        do {
            byte[] body = zenodoClient.listRecords(props.baseUrl(), from, until, token, props.metadataPrefix());
            Page page = parseDatacite(body);

            collected.addAll(page.records);
            token = page.resumptionToken;
            try {
                Thread.sleep(800);
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
     * - oaiIdentifier: OAI header identifier (oai:zenodo.org:<recid>)
     * - datestamp: OAI header datestamp
     * - arxivId: Zenodo recid (suffix of oaiIdentifier)
     * - doi: DataCite identifier with identifierType=DOI (normalized)
     * - license: DataCite rightsURI (preferred) or rights text (normalized)
     * - categories: DataCite subjects
     * - authors: DataCite creators (family/given if present; else creatorName split)
     * - comments: prefers descriptionType="Abstract", else first description
     */
    private Page parseDatacite(byte[] xmlBytes) {
        try {
            XMLEventReader r = xml.createXMLEventReader(new ByteArrayInputStream(xmlBytes));
            List<ArxivRecord> records = new ArrayList<>();

            ArxivRecord cur = null;

            boolean inHeader = false;
            boolean inMetadata = false;
            boolean inCreator = false;

            String tag = null;
            String resumptionToken = null;

            // creator temp
            String creatorName = null;
            String givenName = null;
            String familyName = null;

            // identifier temp
            String identifierType = null;

            // description temp (prefer Abstract)
            String descriptionType = null;
            String firstDescription = null;
            StringBuilder abstractDescription = null;

            while (r.hasNext()) {
                XMLEvent ev = r.nextEvent();

                if (ev.isStartElement()) {
                    StartElement se = ev.asStartElement();
                    String name = se.getName().getLocalPart();

                    switch (name) {
                        case "record" -> {
                            cur = new ArxivRecord();
                            // reset record-level temps
                            firstDescription = null;
                            abstractDescription = null;
                        }
                        case "header" -> inHeader = true;
                        case "metadata" -> inMetadata = true;

                        case "resumptionToken" -> tag = "token";

                        // OAI header fields
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

                        // DataCite: creators
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

                        // DataCite: subjects
                        case "subject" -> {
                            if (inMetadata) tag = "subject";
                        }

                        // DataCite: rights (license)
                        case "rights" -> {
                            if (inMetadata && cur != null) {
                                // Prefer rightsURI if present
                                String rightsUri = attrValue(se, "rightsURI");
                                if (rightsUri != null && !rightsUri.isBlank() && cur.getLicense() == null) {
                                    cur.setLicense(rightsUri);
                                }
                                tag = "rightsText"; // capture text too as fallback
                            }
                        }

                        // DataCite: descriptions (prefer Abstract)
                        case "description" -> {
                            if (inMetadata) {
                                descriptionType = attrValue(se, "descriptionType"); // may be null
                                tag = "description";
                            }
                        }

                        // Optional: titles exist, but ArxivRecord doesn't have a title field.
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

                    // token is outside record sometimes; allow it
                    if (cur == null && !"token".equals(tag)) continue;

                    switch (tag) {
                        case "token" -> resumptionToken = text;

                        // header
                        case "headerIdentifier" -> {
                            cur.setOaiIdentifier(text);
                            // set recid immediately
                            if (cur.getArxivId() == null) {
                                cur.setArxivId(extractZenodoRecId(text));
                            }
                        }
                        case "datestamp" -> cur.setDatestamp(text);

                        // creators
                        case "creatorName" -> creatorName = text;
                        case "givenName" -> givenName = text;
                        case "familyName" -> familyName = text;

                        // subjects -> categories
                        case "subject" -> cur.getCategories().add(text);

                        // rights
                        case "rightsText" -> {
                            if (cur.getLicense() == null) cur.setLicense(text);
                        }

                        // identifiers
                        case "dataciteIdentifier" -> {
                            if ("DOI".equalsIgnoreCase(identifierType)) {
                                cur.setDoi(normalizeDoi(text));
                            }
                        }

                        // descriptions -> comments (prefer Abstract)
                        case "description" -> {
                            if (firstDescription == null) firstDescription = text;
                            if ("Abstract".equalsIgnoreCase(descriptionType)) {
                                if (abstractDescription == null) abstractDescription = new StringBuilder(text);
                                else abstractDescription.append(text);
                            }
                        }

                        case "title" -> {
                            // no-op for now (ArxivRecord doesn't have a title field)
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
                                    ArxivAuthor a = new ArxivAuthor();

                                    if (familyName != null || givenName != null) {
                                        a.lastName = familyName;
                                        a.firstName = givenName;
                                    } else if (creatorName != null) {
                                        // common formats: "Last, First" OR "First Last"
                                        String[] parts = creatorName.split(",", 2);
                                        if (parts.length == 2) {
                                            a.lastName = parts[0].trim();
                                            a.firstName = parts[1].trim();
                                        } else {
                                            // fallback: last token as last name, rest as first name
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
                            descriptionType = null;
                        }

                        case "datestamp" -> {
                            if ("datestamp".equals(tag)) tag = null;
                        }

                        case "resumptionToken" -> {
                            if ("token".equals(tag)) tag = null;
                        }

                        case "record" -> {
                            if (cur != null) {
                                // finalize comments from descriptions
                                if (cur.getComments() == null) {
                                    String best = (abstractDescription != null) ? abstractDescription.toString() : firstDescription;
                                    if (best != null && !best.isBlank()) cur.setComments(best);
                                }

                                // normalize license if present
                                if (cur.getLicense() != null) cur.setLicense(normalizeLicense(cur.getLicense()));

                                // ensure recid set
                                if (cur.getArxivId() == null && cur.getOaiIdentifier() != null) {
                                    cur.setArxivId(extractZenodoRecId(cur.getOaiIdentifier()));
                                }

                                // apply license filter (same semantics as your arXiv service)
                                if (isCommerciallyUsableLicense(cur.getLicense()) && isLikelyScholarlyText(cur)) {
                                    records.add(cur);
                                }
                            }
                            cur = null;

                            // clear record-level temps
                            firstDescription = null;
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

    private static String extractZenodoRecId(String oaiIdentifier) {
        // Example: oai:zenodo.org:8435696
        if (oaiIdentifier == null) return null;
        int idx = oaiIdentifier.lastIndexOf(':');
        return (idx >= 0) ? oaiIdentifier.substring(idx + 1) : oaiIdentifier;
    }

    private static String normalizeDoi(String doi) {
        if (doi == null) return null;
        String d = doi.trim();

        // common variants
        d = d.replaceFirst("(?i)^https?://doi\\.org/", "");
        d = d.replaceFirst("(?i)^doi:", "");
        d = d.trim();

        return d.isEmpty() ? null : d;
    }

    private static String normalizeLicense(String license) {
        if (license == null) return null;
        // normalize whitespace + http->https
        String l = license.trim().replace("http://", "https://");
        // drop trailing punctuation spaces etc
        return l;
    }

    private boolean isLikelyScholarlyText(ArxivRecord r) {

        // require at least one creator/author
        if (r.getAuthors().isEmpty()) return false;

        // optional: require at least one subject keyword
        if (r.getCategories().isEmpty()) return false;

        return true;
    }

    private boolean isCommerciallyUsableLicense(String license) {
        if (license == null || license.isBlank()) return false;

        String l = license.trim().toLowerCase(Locale.ROOT)
                .replace("http://", "https://");

        // Accept common CC URLs
        if (l.contains("creativecommons.org/licenses/by/4.0")) return true;
        if (l.contains("creativecommons.org/licenses/by-sa/4.0")) return true;

        // Only include ND if you explicitly allow ND for your use-case
        if (l.contains("creativecommons.org/licenses/by-nd/4.0")) return true;

        if (l.contains("creativecommons.org/publicdomain/zero/1.0")) return true;

        // Accept common short forms / SPDX-ish forms
        String compact = l.replaceAll("\\s+", "")
                .replace("_", "-");

        return compact.contains("cc-by-4.0")
                || compact.contains("cc-by-sa-4.0")
                || compact.contains("cc-by-nd-4.0")
                || compact.contains("cc0-1.0")
                || compact.contains("cc0");
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

    private record Page(List<ArxivRecord> records, String resumptionToken) {
    }
}
