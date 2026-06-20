package com.mcesnik.planner_backend.DTO.ai;

import java.math.BigDecimal;

public record GeneratedActivity(
    String name,
    String description,
    String location,
    String startTime,
    String endTime,
    String category,
    BigDecimal cost
) {
}
