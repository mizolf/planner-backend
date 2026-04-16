package com.mcesnik.planner_backend.DTO;

import com.mcesnik.planner_backend.model.Enums.Interest;
import com.mcesnik.planner_backend.model.Enums.TripStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
public class UpdateTripDTO {

    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;

    @Size(max = 255, message = "Destination must be at most 255 characters")
    private String destination;

    private LocalDate startDate;

    private LocalDate endDate;

    private TripStatus status;

    @DecimalMin(value = "0", message = "Budget must be non-negative")
    private BigDecimal budget;

    private Set<Interest> interests;
}
