package com.mcesnik.planner_backend.util;

import com.mcesnik.planner_backend.model.Enums.TripStatus;

import java.time.LocalDate;

/**
 * Derives a trip's {@link TripStatus} from its dates instead of storing it.
 *
 * <p>Dates (including "today") are passed in explicitly so the logic is pure
 * and unit-testable without mocking the clock.
 */
public final class TripStatusCalculator {

    private TripStatusCalculator() {
    }

    public static TripStatus calculate(LocalDate start, LocalDate end, LocalDate today) {
        if (today.isBefore(start)) return TripStatus.UPCOMING;
        if (today.isAfter(end)) return TripStatus.COMPLETED;
        return TripStatus.IN_PROGRESS;
    }
}
