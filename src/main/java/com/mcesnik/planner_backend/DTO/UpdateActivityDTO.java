package com.mcesnik.planner_backend.DTO;

import com.mcesnik.planner_backend.model.Enums.ActivityCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
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

    private ActivityCategory category;

    @DecimalMin(value = "0.00", message = "Cost must be >= 0")
    @Digits(integer = 6, fraction = 2, message = "Cost format invalid")
    private BigDecimal cost;
}
