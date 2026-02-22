package com.data.oai;

import com.data.embedding.dto.EmbeddingDto;
import com.data.jpa.dao.EmbedTranscriptChunkEntity;
import com.data.jpa.dao.ReferenceMentionEntity;
import com.data.jpa.repository.EmbedTranscriptChunkRepository;
import com.data.oai.generic.EmbeddingMapper;
import com.data.oai.generic.common.author.ArxivAuthorEntity;
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
import java.util.ArrayList;
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
    public void persistState(Tracker tracker, Record r, EmbeddingDto embeddingInfo, String pdfUrl) {
        persistTracker(tracker);

        persistRecord(r, tracker.getDataSource(), embeddingInfo, pdfUrl);
    }

    private void persistRecord(Record recordFromApi, DataSource dataSource, EmbeddingDto embeddingInfo, String pdfUrl) {
        RecordEntity dbRecord = createDatabaseRecord(recordFromApi, dataSource, pdfUrl);
        addPaperDocument(recordFromApi, dbRecord, embeddingInfo);
        addCategories(recordFromApi, dbRecord);
        addAuthors(recordFromApi, dbRecord);
        recordRepository.save(dbRecord);
    }

    private static void addCategories(Record recordFromApi, RecordEntity dbRecord) {
        dbRecord.getCategories().addAll(recordFromApi.getCategories());
    }

    private static RecordEntity createDatabaseRecord(Record recordFromApi, DataSource dataSource, String pdfUrl) {
        return RecordEntity.builder()
                .arxivId(recordFromApi.getArxivId())
                .oaiIdentifier(recordFromApi.getOaiIdentifier())
                .datestamp(recordFromApi.getDatestamp() == null ? null :
                        Instant.parse(recordFromApi.getDatestamp())
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate())
                .comments(recordFromApi.getComments())
                .journalRef(recordFromApi.getJournalRef())
                .doi(recordFromApi.getDoi())
                .license(recordFromApi.getLicense())
                .categories(new ArrayList<>())
                .authors(new ArrayList<>())
                .dataSource(dataSource)
                .pdfUrl(pdfUrl)
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

    private static void addPaperDocument(Record apiRecord, RecordEntity dbRecord, EmbeddingDto embeddingInfo) {
        if (apiRecord.getDocument() == null) return;
        PaperDocumentEntity doc = PaperDocumentEntity.builder()
                .title(apiRecord.getDocument().title())
                .abstractText(apiRecord.getDocument().abstractText())
                .teiXmlRaw(apiRecord.getDocument().teiXml())
                .rawContent(apiRecord.getDocument().rawContent())
                .keywords(apiRecord.getDocument().keywords())
                .affiliations(apiRecord.getDocument().affiliation())
                .classCodes(apiRecord.getDocument().classCodes())
                .docType(apiRecord.getDocument().docType())
                .sections(new ArrayList<>())
                .references(new ArrayList<>())
                .embeddings(new ArrayList<>())
                .build();
        addSections(apiRecord, doc);
        addReferences(apiRecord, doc);
        addEmbeddings(embeddingInfo, dbRecord);
        dbRecord.setDocument(doc);
    }

    private static void addSections(Record r, PaperDocumentEntity doc) {
        List<Section> sections = r.getDocument().sections();
        for (int i = 0; i < sections.size(); i++) {
            var s = sections.get(i);
            doc.addSection(
                    SectionEntity.builder()
                            .title(s.title())
                            .level(s.level())
                            .text(s.text())
                            .pos(i)
                            .build()
            );
        }
    }

    private static void addReferences(Record r, PaperDocumentEntity doc) {
        List<Reference> refs = r.getDocument().references();
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

    private static void addEmbeddings(EmbeddingDto embeddingInfo, RecordEntity dbRecord) {
        List<EmbedTranscriptChunkEntity> chunks = EmbeddingMapper.toEntity(dbRecord.getDocument(), embeddingInfo);
        chunks.forEach(chunk -> dbRecord.getDocument().addEmbedding(chunk));
    }

    public void persistTracker(Tracker tracker) {
        trackerRepository.save(tracker);
    }

    public List<String> findArxivIdsProcessedInPeriod(LocalDate dateStart, LocalDate dateEnd, DataSource dataSource) {
        return recordRepository.findArxivIdsProcessedInPeriodAndByDataSource(
                dateStart,
                dateEnd,
                dataSource
        );
    }
}
