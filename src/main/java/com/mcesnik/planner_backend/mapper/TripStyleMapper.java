package com.mcesnik.planner_backend.mapper;

import com.mcesnik.planner_backend.model.TripStyle;
import com.mcesnik.planner_backend.responses.TripStyleDetailResponse;
import com.mcesnik.planner_backend.responses.TripStyleResponse;
import com.mcesnik.planner_backend.responses.TripTemplateSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TripStyleMapper {

    public TripStyleResponse toSummary(TripStyle style) {
        return TripStyleResponse.builder()
                .id(style.getId())
                .slug(style.getSlug())
                .name(style.getName())
                .description(style.getDescription())
                .imageUrl(style.getImageUrl())
                .templateCount(style.getTemplates() == null ? 0 : style.getTemplates().size())
                .build();
    }

    public TripStyleDetailResponse toDetail(TripStyle style, List<TripTemplateSummaryResponse> templates) {
        return TripStyleDetailResponse.builder()
                .id(style.getId())
                .slug(style.getSlug())
                .name(style.getName())
                .description(style.getDescription())
                .imageUrl(style.getImageUrl())
                .templates(templates)
                .build();
    }
}
