package com.data.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(destroyMethod = "close")
    public ExecutorService virtualExecutorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(name = "transcriptExecutor")
    public AsyncTaskExecutor transcriptExecutor(ExecutorService virtualExecutorService) {
        AsyncTaskExecutor base =
                new TaskExecutorAdapter(virtualExecutorService);

        return new SemaphoreAsyncTaskExecutor(base, 10);
    }

    @Bean(name = "taskExecutor")
    public AsyncTaskExecutor defaultAsyncExecutor(ExecutorService virtualExecutorService) {
        AsyncTaskExecutor base =
                new TaskExecutorAdapter(virtualExecutorService);

        return new SemaphoreAsyncTaskExecutor(base, 30);
    }
}
