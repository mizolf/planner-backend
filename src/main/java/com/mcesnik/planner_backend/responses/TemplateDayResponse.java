package com.mcesnik.planner_backend.responses;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateDayResponse {
    private Integer dayNumber;
    private String title;
    private String notes;
    private List<TemplateActivityResponse> activities;
}
