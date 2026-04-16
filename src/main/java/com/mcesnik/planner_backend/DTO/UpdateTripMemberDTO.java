package com.mcesnik.planner_backend.DTO;

import com.mcesnik.planner_backend.model.Enums.TripRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTripMemberDTO {

    @NotNull(message = "Role is required")
    private TripRole role;
}
