package com.mcesnik.planner_backend.DTO;

import com.mcesnik.planner_backend.model.Enums.TripRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddTripMemberDTO {
    private String email;
    private TripRole role;
}
