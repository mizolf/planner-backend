package com.mcesnik.planner_backend.mapper;

import com.mcesnik.planner_backend.model.TripInvite;
import com.mcesnik.planner_backend.responses.MyInviteResponse;
import com.mcesnik.planner_backend.responses.TripInviteResponse;
import org.springframework.stereotype.Component;

@Component
public class TripInviteMapper {

    public TripInviteResponse toOwnerResponse(TripInvite invite) {
        return TripInviteResponse.builder()
                .id(invite.getId())
                .email(invite.getEmail())
                .role(invite.getRole())
                .status(invite.getStatus())
                .inviterName(invite.getInviter() != null ? invite.getInviter().getFullName() : "Deleted user")
                .createdAt(invite.getCreatedAt())
                .expiresAt(invite.getExpiresAt())
                .build();
    }

    public MyInviteResponse toMyInviteResponse(TripInvite invite) {
        return MyInviteResponse.builder()
                .id(invite.getId())
                .tripId(invite.getTrip().getId())
                .tripName(invite.getTrip().getName())
                .tripDestination(invite.getTrip().getDestination())
                .inviterName(invite.getInviter() != null ? invite.getInviter().getFullName() : "Deleted user")
                .role(invite.getRole())
                .expiresAt(invite.getExpiresAt())
                .build();
    }
}
