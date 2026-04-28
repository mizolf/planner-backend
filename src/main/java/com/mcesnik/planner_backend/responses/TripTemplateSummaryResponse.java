package com.mcesnik.planner_backend.responses;

import com.mcesnik.planner_backend.model.Enums.Interest;
import com.mcesnik.planner_backend.model.Enums.Season;
import lombok.*;

import java.math.BigDecimal;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripTemplateSummaryResponse {
    private Long id;
    private String slug;
    private String name;
    private String destination;
    private Integer durationDays;
    private Season recommendedSeason;
    private String imageUrl;
    private BigDecimal estimatedBudget;
    private Set<Interest> interests;
}
