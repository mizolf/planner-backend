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
                .build();
    }

    public void updateEntity(Activity activity, UpdateActivityDTO dto) {
        activity.setName(dto.getName());
        activity.setDescription(dto.getDescription());
        activity.setLocation(dto.getLocation());
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());
    }

    public ActivityResponse toResponse(Activity activity) {
        return ActivityResponse.builder()
                .id(activity.getId())
                .name(activity.getName())
                .description(activity.getDescription())
                .location(activity.getLocation())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .build();
    }
}
