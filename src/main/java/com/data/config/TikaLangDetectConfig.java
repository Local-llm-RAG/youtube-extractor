package com.data.config;

import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TikaLangDetectConfig {

    @Bean
    public LanguageDetector tikaLanguageDetector() {
        return new OptimaizeLangDetector().loadModels();
    }
}