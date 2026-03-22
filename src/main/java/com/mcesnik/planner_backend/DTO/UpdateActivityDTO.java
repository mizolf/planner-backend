package com.mcesnik.planner_backend.DTO;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
public class UpdateActivityDTO {
    private String name;
    private String description;
    private String location;
    private LocalTime startTime;
    private LocalTime endTime;
}