package com.mcesnik.planner_backend.DTO;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateTripDayDTO {

    @NotNull(message = "Day number is required")
    @Positive(message = "Day number must be positive")
    private Integer dayNumber;

    @NotNull(message = "Date is required")
    private LocalDate date;

    @Size(max = 255, message = "Notes must be at most 255 characters")
    private String notes;
}
