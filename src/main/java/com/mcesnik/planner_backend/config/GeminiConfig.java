package com.mcesnik.planner_backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiConfig {

    @Bean
    RestClient geminiRestClient(GeminiProperties properties){
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("x-goog-api-key", properties.apiKey())
                .build();
    }
}
