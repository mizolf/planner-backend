package com.mcesnik.planner_backend.mapper;

import com.mcesnik.planner_backend.DTO.CreateActivityDTO;
import com.mcesnik.planner_backend.DTO.UpdateActivityDTO;
import com.mcesnik.planner_backend.model.Activity;
import com.mcesnik.planner_backend.responses.ActivityResponse;
import org.springframework.stereotype.Component;

@Component
public class ActivityMapper {

    public Activity toEntity(CreateActivityDTO dto) {
        return Activity.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .location(dto.getLocation())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .category(dto.getCategory())
                .cost(dto.getCost())
                .build();
    }

    public void updateEntity(Activity activity, UpdateActivityDTO dto) {
        if (dto.getName() != null) activity.setName(dto.getName());
        if (dto.getDescription() != null) activity.setDescription(dto.getDescription());
        if (dto.getLocation() != null) activity.setLocation(dto.getLocation());
        if (dto.getStartTime() != null) activity.setStartTime(dto.getStartTime());
        if (dto.getEndTime() != null) activity.setEndTime(dto.getEndTime());
        if (dto.getCategory() != null) activity.setCategory(dto.getCategory());
        if (dto.getCost() != null) activity.setCost(dto.getCost());
    }

    public ActivityResponse toResponse(Activity activity) {
        return ActivityResponse.builder()
                .id(activity.getId())
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
