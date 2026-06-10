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
public class TemplateActivityResponse {
    private String name;
    private String description;
    private String location;
    private LocalTime startTime;
    private LocalTime endTime;
    private ActivityCategory category;
    private BigDecimal cost;
}
