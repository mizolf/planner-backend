package com.mcesnik.planner_backend.model.Enums;

public enum TripStatus {
    UPCOMING,     // startDate is in the future
    IN_PROGRESS,  // today is between startDate and endDate (inclusive)
    COMPLETED     // endDate has passed
}
