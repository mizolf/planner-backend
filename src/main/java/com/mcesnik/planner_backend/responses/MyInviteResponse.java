package com.mcesnik.planner_backend.responses;

import com.mcesnik.planner_backend.model.Enums.TripRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MyInviteResponse {
    private Long id;
    private Long tripId;
    private String tripName;
    private String tripDestination;
    private String inviterName;
    private TripRole role;
    private Instant expiresAt;
}
