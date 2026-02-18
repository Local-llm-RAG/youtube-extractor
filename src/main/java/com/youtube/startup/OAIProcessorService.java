package com.youtube.startup;

import com.youtube.arxiv.oai.ArxivGenericFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final ArxivGenericFacade arxivGenericFacade;

    @Override
    public void execute(JobExecution execution) {
        IntStream
                .iterate(0, i -> i + 1) // iterate eternally
                .mapToObj(i -> arxivGenericFacade.getArxivTracker(LocalDate.now().minusDays(i)))
                .filter(Objects::nonNull) // filter fully processed
                .forEach(arxivGenericFacade::processCollectedArxivRecord);

    }

    @Override
    public String getName() {
        return "OAIProcessorService Job";
    }
}
