package com.data.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 uses tools.jackson.databind.ObjectMapper (Jackson 3.x) in its
 * auto-configuration, but several components in this codebase still depend on
 * the legacy com.fasterxml.jackson.databind.ObjectMapper (Jackson 2.x). This
 * config registers a single legacy ObjectMapper bean so those components can
 * be wired by Spring without each instantiating its own.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper legacyObjectMapper() {
        return new ObjectMapper();
    }
}
