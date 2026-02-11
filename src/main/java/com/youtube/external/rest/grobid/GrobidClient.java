package com.youtube.external.rest.grobid;

import com.youtube.config.GrobidProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class GrobidClient {

  private final RestClient rest;
  private final GrobidProperties props;

  public String processFulltext(byte[] pdfBytes, String filename) {
    MultipartBodyBuilder mb = new MultipartBodyBuilder();
    mb.part("input", pdfBytes)
        .filename(filename)
        .contentType(MediaType.APPLICATION_PDF);

    mb.part("consolidateHeader", "1");
    mb.part("consolidateCitations", "1");
    mb.part("segmentSentences", "1");

    return rest.post()
        .uri(props.baseUrl() + props.fulltextEndpoint())
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(mb.build())
        .retrieve()
        .body(String.class);
  }

  // Optional: derive plain text from TEI (quick + crude).
  // For high quality, keep TEI as the product and optionally generate text offline.
  private static final Pattern TAGS = Pattern.compile("<[^>]+>");

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
