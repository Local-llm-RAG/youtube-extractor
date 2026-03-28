package com.data.oai.pipeline;

import com.data.config.properties.OaiProcessingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;
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
                .forEach(dataSource -> {
                    log.info("Starting OAI processing for {} ({} days back)", dataSource, processingProps.daysBack());
                    IntStream.range(0, processingProps.daysBack())
                            .mapToObj(i -> genericFacade.getTracker(LocalDate.now().minusDays(i), dataSource))
                            .filter(Objects::nonNull)
                            .forEach(genericFacade::processCollectedArxivRecord);
                });
    }

    @Override
    public @NotNull String getName() {
        return "OAIProcessorService Job";
    }
}
