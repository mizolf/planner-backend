package com.mcesnik.planner_backend.DTO;

import com.mcesnik.planner_backend.model.Enums.Interest;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
public class CreateTripDTO {
    private Integer id;
    private String name;
    private String description;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal budget;
    private Set<Interest> interests;
}
