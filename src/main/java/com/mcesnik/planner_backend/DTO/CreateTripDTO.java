package com.mcesnik.planner_backend.DTO;

import com.mcesnik.planner_backend.model.Enums.Interest;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
public class CreateTripDTO {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;

    @NotBlank(message = "Destination is required")
    @Size(max = 255, message = "Destination must be at most 255 characters")
    private String destination;

    @DecimalMin(value = "-90", message = "Latitude out of range")
    @DecimalMax(value = "90", message = "Latitude out of range")
    private Double latitude;

    @DecimalMin(value = "-180", message = "Longitude out of range")
    @DecimalMax(value = "180", message = "Longitude out of range")
    private Double longitude;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @DecimalMin(value = "0", message = "Budget must be non-negative")
    private BigDecimal budget;

    private Set<Interest> interests;
}
