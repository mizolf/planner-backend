package com.mcesnik.planner_backend.responses;

import lombok.*;

import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateActivityResponse {
    private String name;
    private String description;
    private String location;
    private LocalTime startTime;
    private LocalTime endTime;
}
