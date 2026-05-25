package com.mcesnik.planner_backend.responses;

import com.mcesnik.planner_backend.model.Enums.InviteStatus;
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
public class TripInviteResponse {
    private Long id;
    private String email;
    private TripRole role;
    private InviteStatus status;
    private String inviterName;
    private Instant createdAt;
    private Instant expiresAt;
}
