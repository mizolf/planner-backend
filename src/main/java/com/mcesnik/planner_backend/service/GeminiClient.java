package com.mcesnik.planner_backend.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.mcesnik.planner_backend.DTO.ai.GeneratedItinerary;
import com.mcesnik.planner_backend.config.GeminiProperties;
import com.mcesnik.planner_backend.exception.AIGenerationException;
import com.mcesnik.planner_backend.model.Enums.Interest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GeminiClient {

    private final RestClient geminiRestClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    private final JsonNode responseSchema;


    public GeminiClient(RestClient geminiRestClient,
                        GeminiProperties properties,
                        ObjectMapper objectMapper) {
        this.geminiRestClient = geminiRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        try{
            this.responseSchema = objectMapper.readTree(RESPONSE_SCHEMA_JSON);
        } catch (JacksonException e) {
            throw new IllegalStateException("Invalid Gemini response schema JSON", e);
        }
    }

    public GeneratedItinerary generate(String destination, int numberOfDays, BigDecimal budget, Set<Interest> interests){
        Map<String, Object> requestBody =
                buildRequestBody(buildUserPrompt(destination, numberOfDays, budget, interests));

        JsonNode response;
        try{
            response = geminiRestClient.post()
                    .uri("/models/{model}:generateContent", properties.model())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AIGenerationException("Gemini request failed", e);
        }

        return parseItinerary(response);
    }

    private static final String SYSTEM_PROMPT = """
          You are an expert travel itinerary planner. Given a destination, a number of days,
          a budget, and a list of traveler interests, produce a realistic day-by-day plan.

          Rules:
          - Produce exactly the requested number of days, numbered 1 to N.
          - Each day has 3 to 5 activities.
          - Times use 24-hour "HH:mm" format (e.g. "09:00", "14:30"). Activities within a day
            must not overlap and must follow chronological order.
          - "category" must be one of: ATTRACTION, TRANSPORT, ACCOMMODATION, RESTAURANT, OTHER.
            Pick the best fit (a museum is ATTRACTION, a meal is RESTAURANT).
          - "location" must be a real, geocodable place name in or near the destination
            (a place name, never coordinates).
          - "cost" is the estimated cost per activity in EUR. The sum of all costs across the
            whole trip must not exceed the given budget.
          - Tailor activities to the traveler's interests.
          - Write all titles and descriptions in English.
          """;

    private static final String RESPONSE_SCHEMA_JSON = """
          {
            "type": "OBJECT",
            "properties": {
              "days": {
                "type": "ARRAY",
                "items": {
                  "type": "OBJECT",
                  "properties": {
                    "dayNumber": { "type": "INTEGER" },
                    "title": { "type": "STRING" },
                    "activities": {
                      "type": "ARRAY",
                      "items": {
                        "type": "OBJECT",
                        "properties": {
                          "name": { "type": "STRING" },
                          "description": { "type": "STRING" },
                          "location": { "type": "STRING" },
                          "startTime": { "type": "STRING" },
                          "endTime": { "type": "STRING" },
                          "category": {
                            "type": "STRING",
                            "enum": ["ATTRACTION", "TRANSPORT", "ACCOMMODATION", "RESTAURANT", "OTHER"]
                          },
                          "cost": { "type": "NUMBER" }
                        },
                        "required": ["name", "location", "startTime", "endTime", "category", "cost"]
                      }
                    }
                  },
                  "required": ["dayNumber", "title", "activities"]
                }
              }
            },
            "required": ["days"]
          }
          """;

    private String buildUserPrompt(String destination, int numberOfDays, BigDecimal budget, Set<Interest> interests){
        String interestList = (interests == null || interests.isEmpty())
                ? "general sightseeing"
                : interests.stream().map(Enum::name).collect(Collectors.joining(", "));
        return """
              Plan a %d-day trip to %s.
              Total budget: %s EUR.
              Traveler interests: %s.
              """.formatted(numberOfDays, destination, budget, interestList);
    }

    private Map<String, Object> buildRequestBody(String userPrompt) {
        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
                "contents", List.of(Map.of("parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema,
                        "temperature", 0.7
                )
        );
    }

    private GeneratedItinerary parseItinerary(JsonNode response) {
        if (response == null) {
            throw new AIGenerationException("Gemini returned an empty response");
        }

        JsonNode textNode = response
                .path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text");

        if (textNode.isMissingNode() || !textNode.isTextual()) {
            throw new AIGenerationException("Gemini response had no text part");
        }

        try {
            return objectMapper.readValue(textNode.asText(), GeneratedItinerary.class);
        } catch (JacksonException e) {
            throw new AIGenerationException("Could not parse Gemini itinerary JSON", e);
        }
    }
}
