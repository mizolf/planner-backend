package com.mcesnik.planner_backend.responses;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripStyleResponse {
    private Long id;
    private String slug;
    private String name;
    private String description;
    private String imageUrl;
    private Integer templateCount;
}
