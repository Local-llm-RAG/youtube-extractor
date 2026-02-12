package com.youtube.service.grobid;

import com.youtube.config.GrobidProperties;
import com.youtube.external.rest.arxiv.dto.ArxivPaperDocument;
import com.youtube.external.rest.grobid.GrobidClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class GrobidService {

    private final GrobidProperties props;
    private final GrobidClient grobidClient;

    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    public ArxivPaperDocument processGrobidDocument(String arxivId, String oaiIdentifier, byte[] pdfBytes) {
        String xmlString = grobidClient.processPdfToXmlString(arxivId, pdfBytes, props.baseUrl(), props.fulltextEndpoint());
        log.info("Collected document for paper with id {}", oaiIdentifier);
        return GrobidTeiMapperJsoup.toArxivPaperDocument(arxivId, oaiIdentifier, xmlString);
    }

    public String teiToPlainText(String teiXml) {
        if (teiXml == null || teiXml.isBlank()) return null;
        String noTags = TAGS.matcher(teiXml).replaceAll(" ");
        noTags = noTags.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
        return noTags.replaceAll("\\s+", " ").trim();
    }
}
