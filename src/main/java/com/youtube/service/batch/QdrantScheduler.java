package com.youtube.service.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class QdrantScheduler {
    private final JobOperator jobOperator;

    private final List<Job> jobs;

    @Scheduled(cron = "0 5 0 * * ?")
    public void performBatchJob() {
//        jobs.stream()
//                .filter(job -> job instanceof QdrantProcessorService)
//                .map(job -> new AbstractMap.SimpleEntry<>(job, buildGenericJobParameters(job)))
//                .forEach(jobWithParams -> {
//                    try {
//                        jobOperator.start(jobWithParams.getKey(), jobWithParams.getValue());
//                    } catch (JobExecutionException e) {
//                        log.error("Could not perform job {}", jobWithParams.getKey().getName(), e);
//                        throw new RuntimeException(e);
//                    }
//                });
    }

    @Scheduled(cron = "0 0 5 * * ?")
    public void performOAIExtraction() {
//        jobs.stream()
//                .filter(job -> job instanceof OAIProcessorService)
//                .map(job -> new AbstractMap.SimpleEntry<>(job, buildGenericJobParameters(job)))
//                .forEach(jobWithParams -> {
//                    try {
//                        jobOperator.start(jobWithParams.getKey(), jobWithParams.getValue());
//                    } catch (JobExecutionException e) {
//                        log.error("Could not perform job {}", jobWithParams.getKey().getName(), e);
//                        throw new RuntimeException(e);
//                    }
//                });
    }

    private JobParameters buildGenericJobParameters(Job job) {
        return new JobParametersBuilder()
                .addString("runTime", String.valueOf(System.currentTimeMillis()))
                .addString("JobName", job.getName())
                .toJobParameters();
    }
}
