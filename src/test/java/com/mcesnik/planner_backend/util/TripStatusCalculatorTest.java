package com.mcesnik.planner_backend.util;

import com.mcesnik.planner_backend.model.Enums.TripStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TripStatusCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 6, 10);
    private static final LocalDate END = LocalDate.of(2026, 6, 20);

    @Test
    void todayBeforeStart_isUpcoming() {
        LocalDate today = LocalDate.of(2026, 6, 1);
        assertEquals(TripStatus.UPCOMING, TripStatusCalculator.calculate(START, END, today));
    }

    @Test
    void todayAfterEnd_isCompleted() {
        LocalDate today = LocalDate.of(2026, 6, 21);
        assertEquals(TripStatus.COMPLETED, TripStatusCalculator.calculate(START, END, today));
    }

    @Test
    void todayBetweenDates_isInProgress() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        assertEquals(TripStatus.IN_PROGRESS, TripStatusCalculator.calculate(START, END, today));
    }

    @Test
    void todayEqualsStart_isInProgress() {
        assertEquals(TripStatus.IN_PROGRESS, TripStatusCalculator.calculate(START, END, START));
    }

    @Test
    void todayEqualsEnd_isInProgress() {
        assertEquals(TripStatus.IN_PROGRESS, TripStatusCalculator.calculate(START, END, END));
    }
}
