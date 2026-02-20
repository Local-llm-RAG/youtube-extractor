package com.data.oai;

import com.data.oai.generic.common.author.ArxivAuthorEntity;
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
    public void persistState(Tracker tracker, Record r) {
        persistTracker(tracker);

        persistRecord(r, tracker.getDataSource());
    }

    private void persistRecord(Record r, DataSource dataSource) {
        RecordEntity record = RecordEntity.builder()
                .arxivId(r.getArxivId())
                .oaiIdentifier(r.getOaiIdentifier())
                .datestamp(r.getDatestamp() == null ? null : Instant.parse(r.getDatestamp())
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate())
                .comments(r.getComments())
                .journalRef(r.getJournalRef())
                .doi(r.getDoi())
                .license(r.getLicense())
                .categories(new ArrayList<>())
                .authors(new ArrayList<>())
                .dataSource(dataSource)
                .build();
        record.getCategories().addAll(r.getCategories());
        IntStream.range(0, r.getAuthors().size())
                .forEach(i -> {
                    var a = r.getAuthors().get(i);

                    record.addAuthor(
                            ArxivAuthorEntity.builder()
                                    .firstName(a.getFirstName())
                                    .lastName(a.getLastName())
                                    .pos(i)
                                    .build()
                    );
                });

        if (r.getDocument() != null) {
            var doc = PaperDocumentEntity.builder()
                    .title(r.getDocument().title())
                    .abstractText(r.getDocument().abstractText())
                    .teiXmlRaw(r.getDocument().teiXml())
                    .rawContent(r.getDocument().rawContent())
                    .keywords(r.getDocument().keywords())
                    .affiliations(r.getDocument().affiliation())
                    .classCodes(r.getDocument().classCodes())
                    .references(r.getDocument().references())
                    .docType(r.getDocument().docType())
                    .sections(new ArrayList<>())
                    .build();

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
            record.setDocument(doc);
        }
        recordRepository.save(record);
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
