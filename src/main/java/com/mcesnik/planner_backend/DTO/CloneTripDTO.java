package com.mcesnik.planner_backend.DTO;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CloneTripDTO {

    @NotNull(message = "Start date is required")
    @FutureOrPresent(message = "Start date must be today or later")
    private LocalDate startDate;

    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;
}
