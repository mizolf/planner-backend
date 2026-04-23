package com.mcesnik.planner_backend.event;

import com.mcesnik.planner_backend.model.Enums.TripEventEntityType;
import com.mcesnik.planner_backend.model.Enums.TripEventType;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.model.User;

import java.util.List;

public record TripEventRecorded(
        Trip trip,
        User actor,
        TripEventType eventType,
        TripEventEntityType entityType,
        Long entityId,
        String entityName,
        List<FieldChange> changes
) {
}
