package com.mcesnik.planner_backend.DTO.seed;

import java.util.List;

public record TemplateDaySeedData(
        Integer dayNumber,
        String title,
        String notes,
        List<TemplateActivitySeedData> activities
) {
}
