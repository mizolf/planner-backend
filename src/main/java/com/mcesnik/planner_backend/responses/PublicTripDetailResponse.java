package com.mcesnik.planner_backend.responses;

import com.mcesnik.planner_backend.model.Enums.Interest;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicTripDetailResponse {
    private Long id;
    private String name;
    private String description;
    private String destination;
    private Double latitude;
    private Double longitude;
    private LocalDate startDate;
    private LocalDate endDate;
    private long durationDays;
    private Set<Interest> interests;
    private String ownerDisplayName;
    private int memberCount;
    private List<PublicTripDayResponse> days;
}
