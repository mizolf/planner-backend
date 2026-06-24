package com.mcesnik.planner_backend.DTO;

import com.mcesnik.planner_backend.model.Enums.TripVisibility;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTripVisibilityDTO {

    @NotNull(message = "Visibility is required")
    private TripVisibility visibility;
}
