package com.mcesnik.planner_backend.DTO.seed;

import java.time.LocalTime;

public record TemplateActivitySeedData(
        String name,
        String description,
        String location,
        LocalTime startTime,
        LocalTime endTime
) {
}
