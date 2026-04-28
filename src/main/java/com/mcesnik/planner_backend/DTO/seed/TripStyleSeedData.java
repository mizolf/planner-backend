package com.mcesnik.planner_backend.DTO.seed;

import java.util.List;

public record TripStyleSeedData(
        String slug,
        String name,
        String description,
        String imageUrl,
        Integer displayOrder,
        List<TripTemplateSeedData> templates
) {
}
