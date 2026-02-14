package com.youtube.arxiv.oai;

import com.youtube.arxiv.oai.author.ArxivAuthorEntity;
import com.youtube.arxiv.oai.author.ArxivAuthorRepository;
import com.youtube.arxiv.oai.paper.ArxivPaperDocumentEntity;
import com.youtube.arxiv.oai.paper.ArxivPaperDocumentRepository;
import com.youtube.arxiv.oai.record.ArxivRecordEntity;
import com.youtube.arxiv.oai.record.ArxivRecordRepository;
import com.youtube.arxiv.oai.section.ArxivSectionEntity;
import com.youtube.arxiv.oai.tracker.ArxivTracker;
import com.youtube.arxiv.oai.tracker.ArxivTrackerRepository;
import com.youtube.arxiv.oai.dto.ArxivRecord;
import com.youtube.arxiv.oai.dto.Section;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArxivInternalService {
    private final ArxivTrackerRepository arxivTrackerRepository;
    private final ArxivRecordRepository arxivRecordRepository;
    private final ArxivAuthorRepository arxivAuthorRepository;
    private final ArxivPaperDocumentRepository arxivPaperDocumentRepository;

    public ArxivTracker getArchiveTracker() {
        return arxivTrackerRepository.findTopByOrderByDateEndDesc()
                .map(arxivTracker -> {
                    if (arxivTracker.getProcessedPapersForPeriod().equals(arxivTracker.getAllPapersForPeriod())) { // finished cycle
                        long startEndDateDiff = ChronoUnit.DAYS.between(arxivTracker.getDateStart(), arxivTracker.getDateEnd());
                        return ArxivTracker.builder()
                                .allPapersForPeriod(0)
                                .processedPapersForPeriod(0)
                                .dateStart(arxivTracker.getDateStart().plusDays(startEndDateDiff))
                                .dateEnd(arxivTracker.getDateEnd().plusDays(startEndDateDiff))
                                .build();
                    }
                    return arxivTracker;
                })
                .orElse(
                        ArxivTracker.builder()
                                .allPapersForPeriod(0)
                                .processedPapersForPeriod(0)
                                .dateStart(LocalDate.now().minusDays(1))
                                .dateEnd(LocalDate.now())
                                .build()
                );
    }

    @Transactional
    public void persistArxivState(ArxivTracker tracker, ArxivRecord r) {
        arxivTrackerRepository.save(tracker);

        var record = ArxivRecordEntity.builder()
                .arxivId(r.getArxivId())
                .oaiIdentifier(r.getOaiIdentifier())
                .datestamp(r.getDatestamp() == null ? null : LocalDate.parse(r.getDatestamp()))
                .title(r.getTitle())
                .abstractText(r.getAbstractText())
                .comments(r.getComments())
                .journalRef(r.getJournalRef())
                .doi(r.getDoi())
                .license(r.getLicense())
                .categories(new ArrayList<>())
                .authors(new ArrayList<>())
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
            var doc = ArxivPaperDocumentEntity.builder()
                    .title(r.getDocument().title())
                    .abstractText(r.getDocument().abstractText())
                    .teiXmlRaw(r.getDocument().teiXmlRaw())
                    .sections(new ArrayList<>())
                    .build();

            List<Section> sections = r.getDocument().sections();

            for (int i = 0; i < sections.size(); i++) {
                var s = sections.get(i);

                doc.addSection(
                        ArxivSectionEntity.builder()
                                .title(s.title())
                                .level(s.level())
                                .text(s.text())
                                .pos(i)
                                .build()
                );
            }
            record.setDocument(doc);
        }
        arxivRecordRepository.save(record);
    }

    public List<String> findArxivIdsProcessedInPeriod(LocalDate dateStart, LocalDate dateEnd) {
        return arxivRecordRepository.findArxivIdsProcessedInPeriod(
                dateStart,
                dateEnd
        );
    }
}
