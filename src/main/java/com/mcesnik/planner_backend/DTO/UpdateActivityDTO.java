package com.mcesnik.planner_backend.DTO;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
public class UpdateActivityDTO {

    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;

    @Size(max = 255, message = "Location must be at most 255 characters")
    private String location;

    private LocalTime startTime;

    private LocalTime endTime;
}
