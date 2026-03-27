package com.data.oai.persistence;

import com.data.oai.persistence.entity.Tracker;
import com.data.oai.persistence.repository.TrackerRepository;
import com.data.oai.pipeline.DataSource;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TrackerService {
    private final TrackerRepository trackerRepository;

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
    public void persistTracker(Tracker tracker) {
        trackerRepository.save(tracker);
    }
}
