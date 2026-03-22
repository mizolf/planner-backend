package com.mcesnik.planner_backend.mapper;

import com.mcesnik.planner_backend.model.UserTrip;
import com.mcesnik.planner_backend.responses.TripMemberResponse;
import org.springframework.stereotype.Component;

@Component
public class UserTripMapper {

    public TripMemberResponse toResponse(UserTrip userTrip) {
        return TripMemberResponse.builder()
                .userId(userTrip.getUser().getId())
                .username(userTrip.getUser().getUsername())
                .email(userTrip.getUser().getEmail())
                .role(userTrip.getRole())
                .joinedAt(userTrip.getJoinedAt())
                .build();
    }
}
