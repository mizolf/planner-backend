package com.mcesnik.planner_backend.responses;

import com.mcesnik.planner_backend.model.Enums.ActivityCategory;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityResponse {
    private Long id;
    private String name;
    private String description;
    private String location;
    private Double latitude;
    private Double longitude;
    private LocalTime startTime;
    private LocalTime endTime;
    private ActivityCategory category;
    private BigDecimal cost;
}
