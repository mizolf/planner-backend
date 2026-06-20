package com.mcesnik.planner_backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GeocodingProperties.class)
public class GeocodingConfig {

    @Bean
    RestClient photonRestClient(GeocodingProperties properties){
        return RestClient.builder()
                .baseUrl(properties.photonUrl())
                .build();
    }
}
