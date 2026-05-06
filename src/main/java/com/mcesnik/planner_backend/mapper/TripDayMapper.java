package com.mcesnik.planner_backend.mapper;

import com.mcesnik.planner_backend.DTO.CreateTripDayDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripDayDTO;
import com.mcesnik.planner_backend.model.TripDay;
import com.mcesnik.planner_backend.responses.ActivityResponse;
import com.mcesnik.planner_backend.responses.TripDayResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TripDayMapper {

    public TripDay toEntity(CreateTripDayDTO dto) {
        return TripDay.builder()
                .dayNumber(dto.getDayNumber())
                .date(dto.getDate())
                .title(dto.getTitle())
                .notes(dto.getNotes())
                .build();
    }

    public void updateEntity(TripDay day, UpdateTripDayDTO dto) {
        if (dto.getDayNumber() != null) day.setDayNumber(dto.getDayNumber());
        if (dto.getDate() != null) day.setDate(dto.getDate());
        if (dto.getTitle() != null) day.setTitle(dto.getTitle());
        if (dto.getNotes() != null) day.setNotes(dto.getNotes());
    }

    public TripDayResponse toResponse(TripDay day, List<ActivityResponse> activities) {
        return TripDayResponse.builder()
                .id(day.getId())
                .dayNumber(day.getDayNumber())
                .date(day.getDate())
                .title(day.getTitle())
                .notes(day.getNotes())
                .activities(activities)
                .build();
    }
}
