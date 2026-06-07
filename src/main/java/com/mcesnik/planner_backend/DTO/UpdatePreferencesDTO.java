package com.mcesnik.planner_backend.DTO;

import com.mcesnik.planner_backend.model.Enums.Interest;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UpdatePreferencesDTO {
    // No bean-validation constraints: unknown enum values are already rejected
    // by Jackson at deserialization time. A null/empty set clears all preferences.
    private Set<Interest> interests;
}
