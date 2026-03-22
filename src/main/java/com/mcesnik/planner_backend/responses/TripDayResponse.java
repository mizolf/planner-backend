package com.mcesnik.planner_backend.responses;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripDayResponse {
    private Long id;
    private Integer dayNumber;
    private LocalDate date;
    private String notes;
    private List<ActivityResponse> activities;
}

