package com.mcesnik.planner_backend.mapper;

import com.mcesnik.planner_backend.model.TripTemplate;
import com.mcesnik.planner_backend.responses.TemplateDayResponse;
import com.mcesnik.planner_backend.responses.TripTemplateDetailResponse;
import com.mcesnik.planner_backend.responses.TripTemplateSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TripTemplateMapper {

    public TripTemplateSummaryResponse toSummary(TripTemplate template) {
        return TripTemplateSummaryResponse.builder()
                .id(template.getId())
                .slug(template.getSlug())
                .name(template.getName())
                .destination(template.getDestination())
                .durationDays(template.getDurationDays())
                .recommendedSeason(template.getRecommendedSeason())
                .imageUrl(template.getImageUrl())
                .estimatedBudget(template.getEstimatedBudget())
                .interests(template.getInterests())
                .build();
    }

    public TripTemplateDetailResponse toDetail(TripTemplate template, List<TemplateDayResponse> days) {
        return TripTemplateDetailResponse.builder()
                .id(template.getId())
                .slug(template.getSlug())
                .name(template.getName())
                .description(template.getDescription())
                .destination(template.getDestination())
                .durationDays(template.getDurationDays())
                .recommendedSeason(template.getRecommendedSeason())
                .imageUrl(template.getImageUrl())
                .estimatedBudget(template.getEstimatedBudget())
                .interests(template.getInterests())
                .days(days)
                .build();
    }
}
