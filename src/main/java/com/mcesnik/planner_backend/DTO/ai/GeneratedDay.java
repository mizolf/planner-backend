package com.mcesnik.planner_backend.DTO.ai;

import java.util.List;

public record GeneratedDay (
        Integer dayNumber,
        String title,
        List<GeneratedActivity> activities
) {
}
