package com.mcesnik.planner_backend.mapper;

import com.mcesnik.planner_backend.model.TemplateActivity;
import com.mcesnik.planner_backend.responses.TemplateActivityResponse;
import org.springframework.stereotype.Component;

@Component
public class TemplateActivityMapper {

    public TemplateActivityResponse toResponse(TemplateActivity activity) {
        return TemplateActivityResponse.builder()
                .name(activity.getName())
                .description(activity.getDescription())
                .location(activity.getLocation())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .category(activity.getCategory())
                .cost(activity.getCost())
                .build();
    }
}
