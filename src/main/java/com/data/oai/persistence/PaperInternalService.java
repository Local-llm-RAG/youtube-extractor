package com.data.oai.persistence;

import com.data.embedding.dto.EmbeddingDto;
import com.data.oai.persistence.entity.EmbedTranscriptChunkEntity;
import com.data.oai.persistence.entity.PaperDocumentEntity;
import com.data.oai.persistence.entity.RecordAuthorEntity;
import com.data.oai.persistence.entity.RecordEntity;
import com.data.oai.persistence.entity.ReferenceMentionEntity;
import com.data.oai.persistence.entity.SectionEntity;
import com.data.oai.persistence.repository.EmbedTranscriptChunkRepository;
import com.data.oai.persistence.repository.RecordRepository;
import com.data.oai.pipeline.DataSource;
import com.data.oai.shared.util.DateParser;
import com.data.oai.shared.dto.PaperDocument;
import com.data.oai.shared.dto.Record;
import com.data.oai.shared.dto.Reference;
import com.data.oai.shared.dto.Section;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaperInternalService {
    private final RecordRepository recordRepository;
    private final EmbedTranscriptChunkRepository embedTranscriptChunkRepository;

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
        LocalDate date = DateParser.parseToLocalDate(recordFromApi.getDatestamp());
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
                            RecordAuthorEntity.builder()
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
        SectionEntity section = SectionEntity.builder()
                .title(s.getTitle())
                .level(s.getLevel())
                .text(s.getText())
                .pos(pos)
                .embeddings(new ArrayList<>())
                .build();

        if (s.getEmbeddings() != null) {
            s.getEmbeddings().stream()
                    .map(PaperInternalService::toChunkEntity)
                    .forEach(section::addEmbedding);
        }

        return section;
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

    public List<String> findArxivIdsProcessedInPeriod(LocalDate dateStart, LocalDate dateEnd, DataSource dataSource) {
        return recordRepository.findSourceIdsProcessedInPeriodAndByDataSource(
                dateStart,
                dateEnd,
                dataSource
        );
    }
}
