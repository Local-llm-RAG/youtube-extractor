package com.data.config;

import com.data.config.properties.GrobidProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class GrobidExecutorConfig {

    @Bean(name = "grobidExecutor")
    public ExecutorService grobidExecutor(GrobidProperties props) {
        return new ThreadPoolExecutor(
                props.client().concurrency(),
                props.client().concurrency(),
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(props.client().queue()),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
