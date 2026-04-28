package com.mcesnik.planner_backend.responses;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripStyleDetailResponse {
    private Long id;
    private String slug;
    private String name;
    private String description;
    private String imageUrl;
    private List<TripTemplateSummaryResponse> templates;
}
