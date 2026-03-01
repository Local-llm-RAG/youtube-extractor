package com.data.oai;

import com.data.embedding.dto.EmbeddingDto;
import com.data.jpa.dao.EmbedTranscriptChunkEntity;
import com.data.jpa.dao.ReferenceMentionEntity;
import com.data.jpa.repository.EmbedTranscriptChunkRepository;
import com.data.oai.generic.common.author.ArxivAuthorEntity;
import com.data.oai.generic.common.dto.PaperDocument;
import com.data.oai.generic.common.dto.Reference;
import com.data.oai.generic.common.paper.PaperDocumentEntity;
import com.data.oai.generic.common.record.RecordEntity;
import com.data.oai.generic.common.record.RecordRepository;
import com.data.oai.generic.common.section.SectionEntity;
import com.data.oai.generic.common.tracker.Tracker;
import com.data.oai.generic.common.tracker.TrackerRepository;
import com.data.oai.generic.common.dto.Record;
import com.data.oai.generic.common.dto.Section;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaperInternalService {
    private final TrackerRepository trackerRepository;
    private final RecordRepository recordRepository;
    private final EmbedTranscriptChunkRepository embedTranscriptChunkRepository;

    public Tracker getTracker(LocalDate startDate, DataSource dataSource) {
        Optional<Tracker> trackerForDate = trackerRepository.findByDateStartAndDataSource(startDate, dataSource);
        if (trackerForDate.isPresent()) {
            // Not finished processing
            if (trackerForDate.get().getProcessedPapersForPeriod() != 0 && !trackerForDate.get().getAllPapersForPeriod().equals(trackerForDate.get().getProcessedPapersForPeriod())) {
                return trackerForDate.get();
            } else return null; // skip the ones that are fully processed
        } else {
            return Tracker.builder()
                    .allPapersForPeriod(0)
                    .processedPapersForPeriod(0)
                    .dateStart(startDate)
                    .dateEnd(startDate.plusDays(1))
                    .dataSource(dataSource)
                    .build();

        }
    }

    @Transactional
    public void persistState(DataSource dataSource, Record r, PaperDocument doc, String pdfUrl) {
        persistRecord(r, dataSource, doc, pdfUrl);
    }

    private void persistRecord(
            Record recordFromApi,
            DataSource dataSource,
            PaperDocument grobidDoc,
            String pdfUrl) {
        RecordEntity dbRecord = createDatabaseRecord(recordFromApi, dataSource, pdfUrl);
        addPaperDocument(grobidDoc, dbRecord);
        addCategories(recordFromApi, dbRecord);
        addAuthors(recordFromApi, dbRecord);
        recordRepository.save(dbRecord);
    }

    private static void addCategories(Record recordFromApi, RecordEntity dbRecord) {
        dbRecord.getCategories().addAll(new HashSet<>(recordFromApi.getCategories()));
    }

    private static RecordEntity createDatabaseRecord(Record recordFromApi, DataSource dataSource, String pdfUrl) {
        LocalDate date = parseToLocalDate(recordFromApi.getDatestamp());
        return RecordEntity.builder()
                .sourceId(recordFromApi.getSourceId())
                .oaiIdentifier(recordFromApi.getOaiIdentifier())
                .datestamp(date)
                .comments(recordFromApi.getComments())
                .journalRef(recordFromApi.getJournalRef())
                .doi(recordFromApi.getDoi())
                .license(recordFromApi.getLicense())
                .categories(new ArrayList<>())
                .authors(new ArrayList<>())
                .dataSource(dataSource)
                .pdfUrl(pdfUrl)
                .language(recordFromApi.getLanguage())
                .build();
    }

    private static void addAuthors(Record recordFromApi, RecordEntity dbRecord) {
        IntStream.range(0, recordFromApi.getAuthors().size())
                .forEach(i -> {
                    var a = recordFromApi.getAuthors().get(i);
                    dbRecord.addAuthor(
                            ArxivAuthorEntity.builder()
                                    .firstName(a.getFirstName())
                                    .lastName(a.getLastName())
                                    .pos(i)
                                    .build()
                    );
                });
    }

    private static void addPaperDocument(PaperDocument grobidDoc, RecordEntity dbRecord) {
        if (grobidDoc == null) return;
        PaperDocumentEntity doc = PaperDocumentEntity.builder()
                .title(grobidDoc.title())
                .abstractText(grobidDoc.abstractText())
                .teiXmlRaw(grobidDoc.teiXml())
                .rawContent(grobidDoc.rawContent())
                .keywords(grobidDoc.keywords())
                .affiliations(grobidDoc.affiliation())
                .classCodes(grobidDoc.classCodes())
                .docType(grobidDoc.docType())
                .sections(new ArrayList<>())
                .references(new ArrayList<>())
                .build();
        addSections(grobidDoc, doc);
        addReferences(grobidDoc, doc);
        dbRecord.setDocument(doc);
    }

    private static void addSections(PaperDocument grobidDoc, PaperDocumentEntity doc) {
        List<Section> sections = grobidDoc.sections();
        if (sections == null || sections.isEmpty()) return;

        IntStream.range(0, sections.size())
                .mapToObj(i -> toSectionEntity(sections.get(i), i))
                .forEach(doc::addSection);
    }

    private static void addReferences(PaperDocument grobidDoc, PaperDocumentEntity doc) {
        List<Reference> refs = grobidDoc.references();
        for (int index = 0; index < refs.size(); index++) {
            Reference ref = refs.get(index);

            String title = ref.analyticTitle() != null && !ref.analyticTitle().isBlank()
                    ? ref.analyticTitle()
                    : ref.monogrTitle();

            List<String> idnosFlat = ref.idnos() == null
                    ? List.of()
                    : ref.idnos().entrySet().stream()
                    .map(e -> e.getKey() + ":" + e.getValue())
                    .toList();

            doc.addReference(
                    ReferenceMentionEntity.builder()
                            .refIndex(index)
                            .title(title)
                            .doi(ref.doi())
                            .year(ref.year())
                            .venue(ref.venue())
                            .authors(ref.authors())
                            .urls(ref.urls())
                            .idnos(idnosFlat)
                            .build()
            );
        }
    }

    private static SectionEntity toSectionEntity(Section s, int pos) {
        return SectionEntity.builder()
                .title(s.getTitle())
                .level(s.getLevel())
                .text(s.getText())
                .pos(pos)
                .embeddings(s.getEmbeddings().stream()
                        .map(PaperInternalService::toChunkEntity)
                        .toList())
                .build();
    }

    private static EmbedTranscriptChunkEntity toChunkEntity(EmbeddingDto dto) {
        return EmbedTranscriptChunkEntity.builder()
                .task(dto.getEmbeddingTask())
                .chunkTokens(dto.getChunkTokens())
                .chunkOverlap(dto.getChunkOverlap())
                .embeddingModel(dto.getEmbeddingModel())
                .dim(dto.getDim())
                .chunkIndex(dto.getChunkIndex())
                .chunkText(dto.getChunkText())
                .spanStart(dto.getSpanStart())
                .spanEnd(dto.getSpanEnd())
                .embedding(dto.getEmbedding())
                .build();
    }

    public static LocalDate parseToLocalDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(raw)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();
        } catch (DateTimeParseException ignored) {
            return LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    @Transactional
    public void persistTracker(Tracker tracker) {
        trackerRepository.save(tracker);
    }

    public List<String> findArxivIdsProcessedInPeriod(LocalDate dateStart, LocalDate dateEnd, DataSource dataSource) {
        return recordRepository.findSourceIdsProcessedInPeriodAndByDataSource(
                dateStart,
                dateEnd,
                dataSource
        );
    }
}
