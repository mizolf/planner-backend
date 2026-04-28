package com.mcesnik.planner_backend.DTO.seed;

import com.mcesnik.planner_backend.model.Enums.Interest;
import com.mcesnik.planner_backend.model.Enums.Season;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record TripTemplateSeedData(
        String slug,
        String name,
        String description,
        String destination,
        Integer durationDays,
        Season recommendedSeason,
        String imageUrl,
        BigDecimal estimatedBudget,
        Set<Interest> interests,
        Integer displayOrder,
        List<TemplateDaySeedData> days
) {
}
