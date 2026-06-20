package com.mcesnik.planner_backend.service;

import tools.jackson.databind.JsonNode;
import com.mcesnik.planner_backend.config.GeocodingProperties;
import com.mcesnik.planner_backend.service.ai.Coordinates;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Service
public class GeocodingService {
    private final RestClient photonRestClient;
    private final GeocodingProperties properties;

    public GeocodingService(RestClient photonRestClient, GeocodingProperties properties) {
        this.photonRestClient = photonRestClient;
        this.properties = properties;
    }

    public Optional<Coordinates> geocode(String query, Double biasLat, Double biasLon) {
        try {
            JsonNode response = photonRestClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.queryParam("q", query).queryParam("limit", 1);
                        if (biasLat != null && biasLon != null) {
                            uriBuilder.queryParam("lat", biasLat).queryParam("lon", biasLon);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode coordinates = response
                    .path("features").path(0)
                    .path("geometry").path("coordinates");

            if (!coordinates.isArray() || coordinates.size() < 2) {
                return Optional.empty();
            }

            double lon = coordinates.path(0).asDouble();
            double lat = coordinates.path(1).asDouble();
            return Optional.of(new Coordinates(lat, lon));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<Coordinates> geocodeActivity(String location, String destination, Coordinates center) {
        String query = location + ", " + destination;
        Double biasLat = center != null ? center.latitude() : null;
        Double biasLon = center != null ? center.longitude() : null;

        Optional<Coordinates> result = geocode(query, biasLat, biasLon);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        if (center != null && distanceKm(result.get(), center) > properties.maxRadiusKm()) {
            return Optional.empty();
        }
        return result;
    }

    private double distanceKm(Coordinates a, Coordinates b) {
        final double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * earthRadiusKm * Math.asin(Math.sqrt(h));
    }
}
