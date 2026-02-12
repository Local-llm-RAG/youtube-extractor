package com.youtube.service.arxiv;

import com.youtube.jpa.dao.ArxivTracker;
import com.youtube.jpa.repository.ArxivTrackerRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArxivInternalService {
    private final ArxivTrackerRepository arxivTrackerRepository;

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
    public void saveTracker(ArxivTracker arxivTracker) {
        arxivTrackerRepository.save(arxivTracker);
    }
}
