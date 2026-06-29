package com.mcesnik.planner_backend.DTO.seed;

import com.mcesnik.planner_backend.model.Enums.ActivityCategory;

import java.math.BigDecimal;
import java.time.LocalTime;

public record TemplateActivitySeedData(
        String name,
        String description,
        String location,
        Double latitude,
        Double longitude,
        LocalTime startTime,
        LocalTime endTime,
        ActivityCategory category,
        BigDecimal cost
) {
}
