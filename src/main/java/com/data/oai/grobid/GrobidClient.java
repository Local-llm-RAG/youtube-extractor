package com.data.oai.grobid;

import com.data.config.properties.GrobidProperties;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GrobidClient {

    private final RestClient rest;
    private final GrobidProperties props;

    public GrobidClient(@Qualifier("grobidRestClient") RestClient rest, GrobidProperties props) {
        this.rest = rest;
        this.props = props;
    }

    /**
     * Sends a PDF to the GROBID service for full-text extraction and returns the
     * TEI-XML response. Retries are handled by Resilience4j (@Retry).
     */
    @Retry(name = "grobid")
    public String processPdfToXmlString(String sourceId, byte[] pdfBytes) {
        MultipartBodyBuilder mb = buildGrobidRequest(pdfBytes, sourceId + ".pdf");

        return rest.post()
                .uri(props.baseUrl() + props.fulltextEndpoint())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(mb.build())
                .retrieve()
                .body(String.class);
    }

    private MultipartBodyBuilder buildGrobidRequest(byte[] pdfBytes, String filename) {
        GrobidProperties.Options opts = props.options();
        MultipartBodyBuilder mb = new MultipartBodyBuilder();
        mb.part("input", pdfBytes)
                .filename(filename)
                .contentType(MediaType.APPLICATION_PDF);

        mb.part("consolidateHeader", boolToFlag(opts.consolidateHeader()));
        mb.part("consolidateCitations", boolToFlag(opts.consolidateCitations()));
        mb.part("segmentSentences", boolToFlag(opts.segmentSentences()));
        mb.part("includeRawCitations", boolToFlag(opts.includeRawCitations()));
        mb.part("includeRawAffiliations", boolToFlag(opts.includeRawAffiliations()));
        return mb;
    }

    private static String boolToFlag(boolean value) {
        return value ? "1" : "0";
    }
}
