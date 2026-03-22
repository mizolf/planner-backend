package com.mcesnik.planner_backend.responses;

import com.mcesnik.planner_backend.model.Enums.TripRole;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripMemberResponse {
    private Long userId;
    private String username;
    private String email;
    private TripRole role;
    private LocalDateTime joinedAt;
}

