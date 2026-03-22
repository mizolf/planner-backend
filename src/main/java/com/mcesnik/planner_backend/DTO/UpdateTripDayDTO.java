package com.mcesnik.planner_backend.DTO;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateTripDayDTO {
    private Integer dayNumber;
    private LocalDate date;
    private String notes;
}