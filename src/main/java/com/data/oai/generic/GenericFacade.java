package com.data.oai.generic;

import com.data.config.properties.EmbeddingProperties;
import com.data.embedding.RagSystemRestApiService;
import com.data.external.rest.pythonclient.dto.EmbedTranscriptRequest;
import com.data.external.rest.pythonclient.dto.EmbeddingTask;
import com.data.oai.DataSource;
import com.data.oai.PaperInternalService;
import com.data.oai.generic.common.tracker.Tracker;
import com.data.oai.generic.common.dto.PaperDocument;
import com.data.oai.generic.common.dto.Record;
import com.data.oai.generic.common.dto.Section;
import com.data.grobid.GrobidService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenericFacade {

    private final OaiSourceRegistry sourceRegistry;
    private final GrobidService grobidService;
    private final PaperInternalService paperInternalService;
    private final RagSystemRestApiService ragService;
    private final EmbeddingProperties embeddingProperties;
    private final LanguageDetector languageDetector;

    @Resource(name = "grobidExecutor")
    private ExecutorService grobidPool;

    private final AtomicLong totalBytesStored = new AtomicLong();

    public Tracker getTracker(LocalDate startDate, DataSource dataSource) {
        return paperInternalService.getTracker(startDate, dataSource);
    }

    public void processCollectedArxivRecord(Tracker tracker) {
        processCollectedArxivRecord(tracker, null);
    }

    public void processCollectedArxivRecord(Tracker tracker, Set<String> onlyArxivIds) {
        OaiSourceHandler handler = sourceRegistry.get(tracker.getDataSource());

        Set<String> processedPaperIds = new java.util.HashSet<>(
                paperInternalService.findArxivIdsProcessedInPeriod(
                        tracker.getDateStart(),
                        tracker.getDateEnd(),
                        tracker.getDataSource()
                )
        );

        List<Record> allRecords = handler.fetchMetadata(
                tracker.getDateStart(),
                tracker.getDateEnd());

        tracker.setAllPapersForPeriod(allRecords.size());

        List<Record> unprocessed = allRecords.stream()
                .peek(r -> r.setSourceId(Record.extractIdFromOai(r.getOaiIdentifier())))
                .filter(r -> nonNull(r.getSourceId()))
                .filter(r -> !processedPaperIds.contains(r.getSourceId()))
                .filter(r -> isNull(onlyArxivIds) || onlyArxivIds.contains(r.getSourceId()))
                .toList();
        tracker.setProcessedPapersForPeriod(allRecords.size() - unprocessed.size());
        AtomicInteger processed = new AtomicInteger(tracker.getProcessedPapersForPeriod());

        List<CompletableFuture<Void>> futures = unprocessed.stream()
                .map(r -> CompletableFuture.runAsync(() -> processOne(handler, tracker, r, processed), grobidPool))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Processed {} records with GROBID", unprocessed.size());
    }

    private void processOne(OaiSourceHandler handler, Tracker tracker, Record apiRecord, AtomicInteger processed) {
        String sourceId = apiRecord.getSourceId();

        try {
            AbstractMap.SimpleEntry<String, byte[]> urlWithContent = handler.fetchPdfAndEnrich(apiRecord);
            if (urlWithContent == null) {
                return; // no PDF available — already logged by handler
            }
            byte[] pdfContent = urlWithContent.getValue();
            if (pdfContent == null || pdfContent.length == 0) {
                throw new RuntimeException("Empty or no pdf content found for %s".formatted(sourceId));
            }

            PaperDocument grobidDoc = grobidService.processGrobidDocument(sourceId, apiRecord.getOaiIdentifier(), pdfContent);
            apiRecord.setLanguage(detectLang(grobidDoc.title() + " " + grobidDoc.abstractText(), sourceId));
            grobidDoc.sections().forEach(
                    section ->
                            section.setEmbeddings(ragService.getEmbeddingsForText(buildEmbedTranscriptRequest(section.getText())))
            );
//            EmbeddingDto embeddingInfo = EmbeddingDto.builder().build();
            paperInternalService.persistState(
                    tracker.getDataSource(),
                    apiRecord,
                    grobidDoc,
                    urlWithContent.getKey());
            totalBytesStored.addAndGet(estimateDocumentBytes(grobidDoc));
        } catch (Exception e) {
            log.warn("Failed to process arXivId={} with GROBID", sourceId, e);
        } finally {
            int newVal = processed.incrementAndGet();

            tracker.setProcessedPapersForPeriod(newVal);

            if (newVal % 10 == 0) {
                paperInternalService.persistTracker(tracker);
                log.info("Cumulative DB content stored: {} ({} documents)", humanReadableSize(totalBytesStored.get()), newVal);
            }
        }
    }

    private static long estimateDocumentBytes(PaperDocument doc) {
        long size = 0;
        if (doc.teiXml() != null)       size += doc.teiXml().getBytes(StandardCharsets.UTF_8).length;
        if (doc.rawContent() != null)    size += doc.rawContent().getBytes(StandardCharsets.UTF_8).length;
        if (doc.title() != null)         size += doc.title().getBytes(StandardCharsets.UTF_8).length;
        if (doc.abstractText() != null)  size += doc.abstractText().getBytes(StandardCharsets.UTF_8).length;
        for (Section s : doc.sections())
            if (s.getText() != null)     size += s.getText().getBytes(StandardCharsets.UTF_8).length;
        return size;
    }

    private static String humanReadableSize(long bytes) {
        if (bytes < 1024)                    return bytes + " B";
        if (bytes < 1024 * 1024)             return "%.1f KB".formatted(bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024)     return "%.1f MB".formatted(bytes / (1024.0 * 1024));
        return "%.2f GB".formatted(bytes / (1024.0 * 1024 * 1024));
    }

    private String detectLang(String text, String sourceId) {
        try {
            LanguageResult result = languageDetector.detect(text);
            if (result.isUnknown()) {
                log.warn("Unknown language for sourceId={}", sourceId);
                return "en";
            }
            return result.getLanguage();
        } catch (Exception e) {
            log.error("Language detection failed for sourceId={}", sourceId, e);
            return "en";
        }
    }

    private EmbedTranscriptRequest buildEmbedTranscriptRequest(String transcriptText) {
        return EmbedTranscriptRequest.builder()
                .text(transcriptText)
                .task(EmbeddingTask.RETRIEVAL_PASSAGE.getValue())
                .chunkTokens(embeddingProperties.chunkSize())
                .chunkOverlap(embeddingProperties.overlap())
                .normalize(embeddingProperties.normalize())
                .build();
    }
}