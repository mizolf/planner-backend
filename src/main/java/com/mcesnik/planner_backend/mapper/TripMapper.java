package com.mcesnik.planner_backend.mapper;

import com.mcesnik.planner_backend.DTO.CreateTripDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripDTO;
import com.mcesnik.planner_backend.model.Enums.TripStatus;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.responses.TripDetailResponse;
import com.mcesnik.planner_backend.responses.TripDayResponse;
import com.mcesnik.planner_backend.responses.TripMemberResponse;
import com.mcesnik.planner_backend.responses.TripResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TripMapper {

    public Trip toEntity(CreateTripDTO dto) {
        return Trip.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .destination(dto.getDestination())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .budget(dto.getBudget())
                .interests(dto.getInterests())
                .status(TripStatus.PLANNING)
                .build();
    }

    public void updateEntity(Trip trip, UpdateTripDTO dto) {
        trip.setName(dto.getName());
        trip.setDescription(dto.getDescription());
        trip.setDestination(dto.getDestination());
        trip.setStartDate(dto.getStartDate());
        trip.setEndDate(dto.getEndDate());
        trip.setStatus(dto.getStatus());
        trip.setBudget(dto.getBudget());
        trip.setInterests(dto.getInterests());
    }

    public TripResponse toResponse(Trip trip) {
        return TripResponse.builder()
                .id(trip.getId())
                .name(trip.getName())
                .description(trip.getDescription())
                .destination(trip.getDestination())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .status(trip.getStatus())
                .budget(trip.getBudget())
                .interests(trip.getInterests())
                .createdAt(trip.getCreatedAt())
                .updatedAt(trip.getUpdatedAt())
                .build();
    }

    public TripDetailResponse toDetailResponse(Trip trip, List<TripDayResponse> days, List<TripMemberResponse> members) {
        TripDetailResponse response = new TripDetailResponse();
        response.setId(trip.getId());
        response.setName(trip.getName());
        response.setDescription(trip.getDescription());
        response.setDestination(trip.getDestination());
        response.setStartDate(trip.getStartDate());
        response.setEndDate(trip.getEndDate());
        response.setStatus(trip.getStatus());
        response.setBudget(trip.getBudget());
        response.setInterests(trip.getInterests());
        response.setCreatedAt(trip.getCreatedAt());
        response.setUpdatedAt(trip.getUpdatedAt());
        response.setDays(days);
        response.setMembers(members);
        return response;
    }
}
