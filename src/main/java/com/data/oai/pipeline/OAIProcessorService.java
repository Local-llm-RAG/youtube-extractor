package com.data.oai.pipeline;

import com.data.config.properties.OaiProcessingProperties;
import com.data.oai.persistence.entity.Tracker;
import com.data.shared.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAIProcessorService implements Job {

    private final GenericFacade genericFacade;
    private final OaiProcessingProperties processingProps;

    @Override
    public void execute(JobExecution execution) {
        processingProps.sources()
                .forEach(this::processSource);
    }

    private void processSource(DataSource dataSource) {
        log.info("Starting OAI processing for {} ({} days back)", dataSource, processingProps.daysBack());

        List<LocalDate> failedDates = new ArrayList<>();

        IntStream.range(0, processingProps.daysBack())
                .mapToObj(i -> LocalDate.now().minusDays(i))
                .forEach(date -> processDateSafely(date, dataSource, failedDates));

        if (!failedDates.isEmpty()) {
            log.warn("[{}] {} date(s) failed due to timeouts or errors and will be retried on the next run: {}",
                    dataSource, failedDates.size(), failedDates);
        }
    }

    /**
     * Processes a single date for the given source. If the OAI server times out or
     * any unexpected error occurs, the failure is logged and the date is added to
     * {@code failedDates} so the pipeline can continue with remaining dates.
     * Failed dates will be retried automatically on the next scheduled run since
     * they remain unprocessed in the tracker.
     */
    private void processDateSafely(LocalDate date, DataSource dataSource, List<LocalDate> failedDates) {
        try {
            Tracker tracker = genericFacade.getTracker(date, dataSource);
            if (tracker != null) {
                genericFacade.processCollectedArxivRecord(tracker);
            }
        } catch (Exception e) {
            failedDates.add(date);
            log.error("[{}] Failed to process date {}. Skipping to next date. Error: {}",
                    dataSource, date, e.getMessage());
        }
    }

    @Override
    public @NotNull String getName() {
        return "OAIProcessorService Job";
    }
}
