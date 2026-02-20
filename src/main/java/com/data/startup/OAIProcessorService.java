package com.data.startup;

import com.data.oai.DataSource;
import com.data.oai.generic.GenericFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAIProcessorService implements Job {

    private final GenericFacade genericFacade;

    @Override
    public void execute(JobExecution execution) {
        List.of(DataSource.ZENODO)
                .forEach(dataSource -> {
                    IntStream
                            .iterate(0, i -> i + 1) // iterate eternally
                            .mapToObj(i -> genericFacade.getTracker(LocalDate.now().minusDays(i), dataSource))
                            .filter(Objects::nonNull) // filter fully processed
                            .forEach(genericFacade::processCollectedArxivRecord);
                });

    }

    @Override
    public String getName() {
        return "OAIProcessorService Job";
    }
}
