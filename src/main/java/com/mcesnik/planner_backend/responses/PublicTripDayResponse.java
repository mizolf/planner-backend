package com.mcesnik.planner_backend.responses;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicTripDayResponse {
    private Integer dayNumber;
    private LocalDate date;
    private String title;
    private String notes;
    private List<PublicTripActivityResponse> activities;
}
