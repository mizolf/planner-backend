package com.mcesnik.planner_backend.responses;

import com.mcesnik.planner_backend.model.Enums.Interest;
import lombok.*;

import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicTripSummaryResponse {
    private Long id;
    private String name;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private long durationDays;
    private Set<Interest> interests;
    private String ownerDisplayName;
    private int memberCount;
}
