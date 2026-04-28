package com.mcesnik.planner_backend.mapper;

import com.mcesnik.planner_backend.model.TemplateDay;
import com.mcesnik.planner_backend.responses.TemplateActivityResponse;
import com.mcesnik.planner_backend.responses.TemplateDayResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TemplateDayMapper {

    public TemplateDayResponse toResponse(TemplateDay day, List<TemplateActivityResponse> activities) {
        return TemplateDayResponse.builder()
                .dayNumber(day.getDayNumber())
                .notes(day.getNotes())
                .activities(activities)
                .build();
    }
}
