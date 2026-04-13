package com.data.oai.persistence;

import com.data.oai.persistence.entity.Tracker;
import com.data.oai.persistence.repository.TrackerRepository;
import com.data.shared.DataSource;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackerService {
    private final TrackerRepository trackerRepository;

    public Tracker getTracker(LocalDate startDate, DataSource dataSource) {
        Optional<Tracker> trackerForDate = trackerRepository.findByDateStartAndDataSource(startDate, dataSource);
        if (trackerForDate.isPresent()) {
            Tracker tracker = trackerForDate.get();
            // Skip only when fully processed (all > 0 and all == processed)
            if (tracker.getAllPapersForPeriod() > 0
                    && tracker.getAllPapersForPeriod().equals(tracker.getProcessedPapersForPeriod())) {
                log.debug("[{}] Skipping fully processed date {} ({}/{} records)",
                        dataSource, startDate, tracker.getProcessedPapersForPeriod(), tracker.getAllPapersForPeriod());
                return null;
            }
            return tracker;
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
    public void persistTracker(Tracker tracker) {
        trackerRepository.save(tracker);
    }

    /**
     * Atomically increments the processed count at the DB level.
     * Thread-safe — can be called from multiple GROBID pool threads concurrently.
     */
    @Transactional
    public void incrementProcessed(Long trackerId) {
        trackerRepository.incrementProcessed(trackerId);
    }
}
