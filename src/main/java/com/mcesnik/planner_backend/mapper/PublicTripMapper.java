package com.mcesnik.planner_backend.mapper;

import com.mcesnik.planner_backend.model.Activity;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.model.TripDay;
import com.mcesnik.planner_backend.responses.PublicTripActivityResponse;
import com.mcesnik.planner_backend.responses.PublicTripDayResponse;
import com.mcesnik.planner_backend.responses.PublicTripDetailResponse;
import com.mcesnik.planner_backend.responses.PublicTripSummaryResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class PublicTripMapper {

    public PublicTripSummaryResponse toSummary(Trip trip, String ownerDisplayName, int memberCount) {
        return PublicTripSummaryResponse.builder()
                .id(trip.getId())
                .name(trip.getName())
                .destination(trip.getDestination())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .durationDays(durationDays(trip.getStartDate(), trip.getEndDate()))
                .interests(trip.getInterests())
                .ownerDisplayName(ownerDisplayName)
                .memberCount(memberCount)
                .build();
    }

    public PublicTripDetailResponse toDetail(Trip trip, List<PublicTripDayResponse> days,
                                             String ownerDisplayName, int memberCount) {
        return PublicTripDetailResponse.builder()
                .id(trip.getId())
                .name(trip.getName())
                .description(trip.getDescription())
                .destination(trip.getDestination())
                .latitude(trip.getLatitude())
                .longitude(trip.getLongitude())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .durationDays(durationDays(trip.getStartDate(), trip.getEndDate()))
                .interests(trip.getInterests())
                .ownerDisplayName(ownerDisplayName)
                .memberCount(memberCount)
                .days(days)
                .build();
    }

    public PublicTripDayResponse toDayResponse(TripDay day, List<PublicTripActivityResponse> activities) {
        return PublicTripDayResponse.builder()
                .dayNumber(day.getDayNumber())
                .date(day.getDate())
                .title(day.getTitle())
                .notes(day.getNotes())
                .activities(activities)
                .build();
    }

    public PublicTripActivityResponse toActivityResponse(Activity activity) {
        return PublicTripActivityResponse.builder()
                .name(activity.getName())
                .description(activity.getDescription())
                .location(activity.getLocation())
                .latitude(activity.getLatitude())
                .longitude(activity.getLongitude())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .category(activity.getCategory())
                .build();
    }

    private long durationDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }
}
