package com.data.config;

import com.data.config.properties.OaiProcessingProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class OaiExecutorConfig {

    @Bean(name = "oaiExecutor", destroyMethod = "shutdown")
    public ExecutorService oaiExecutor(OaiProcessingProperties props) {
        return new ThreadPoolExecutor(
                props.concurrency(),
                props.concurrency(),
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(props.queue()),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
