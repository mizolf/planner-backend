package com.mcesnik.planner_backend.responses;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TripDetailResponse extends TripResponse {
    private List<TripDayResponse> days;
    private List<TripMemberResponse> members;
}

