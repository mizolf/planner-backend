package com.mcesnik.planner_backend.responses;

import com.mcesnik.planner_backend.model.Enums.Interest;
import com.mcesnik.planner_backend.model.Enums.TripStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripResponse {
    private Long id;
    private String name;
    private String description;
    private String destination;                                                                                                                                                                                                                                                                                         private LocalDate startDate;
    private LocalDate endDate;
    private TripStatus status;
    private BigDecimal budget;
    private Set<Interest> interests;
    private Instant createdAt;
    private Instant updatedAt;
}