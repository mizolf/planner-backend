package com.mcesnik.planner_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geocoding")
public record GeocodingProperties(String photonUrl, double maxRadiusKm) {

}
