package com.mcesnik.planner_backend.event;

import com.mcesnik.planner_backend.DTO.UpdateActivityDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripDayDTO;
import com.mcesnik.planner_backend.model.Activity;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.model.TripDay;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Component
public class ChangeDetector {

    public List<FieldChange> detectTripChanges(Trip trip, UpdateTripDTO dto) {
        List<FieldChange> changes = new ArrayList<>();
        compare(changes, "name", trip.getName(), dto.getName());
        compare(changes, "description", trip.getDescription(), dto.getDescription());
        compare(changes, "destination", trip.getDestination(), dto.getDestination());
        compare(changes, "startDate", trip.getStartDate(), dto.getStartDate());
        compare(changes, "endDate", trip.getEndDate(), dto.getEndDate());
        compareBigDecimal(changes, "budget", trip.getBudget(), dto.getBudget());
        compareCollection(changes, "interests", trip.getInterests(), dto.getInterests());
        return changes;
    }

    public List<FieldChange> detectDayChanges(TripDay day, UpdateTripDayDTO dto) {
        List<FieldChange> changes = new ArrayList<>();
        compare(changes, "dayNumber", day.getDayNumber(), dto.getDayNumber());
        compare(changes, "date", day.getDate(), dto.getDate());
        compare(changes, "notes", day.getNotes(), dto.getNotes());
        return changes;
    }

    public List<FieldChange> detectActivityChanges(Activity activity, UpdateActivityDTO dto) {
        List<FieldChange> changes = new ArrayList<>();
        compare(changes, "name", activity.getName(), dto.getName());
        compare(changes, "description", activity.getDescription(), dto.getDescription());
        compare(changes, "location", activity.getLocation(), dto.getLocation());
        compare(changes, "startTime", activity.getStartTime(), dto.getStartTime());
        compare(changes, "endTime", activity.getEndTime(), dto.getEndTime());
        return changes;
    }

    private void compare(List<FieldChange> changes, String field, Object oldVal, Object newVal) {
        if (newVal == null) return;
        if (Objects.equals(oldVal, newVal)) return;
        changes.add(new FieldChange(field, toStr(oldVal), newVal.toString()));
    }

    private void compareBigDecimal(List<FieldChange> changes, String field, BigDecimal oldVal, BigDecimal newVal) {
        if (newVal == null) return;
        if (oldVal != null && oldVal.compareTo(newVal) == 0) return;
        changes.add(new FieldChange(field, toStr(oldVal), newVal.toString()));
    }

    private void compareCollection(List<FieldChange> changes, String field, Collection<?> oldVal, Collection<?> newVal) {
        if (newVal == null) return;
        List<String> oldSorted = sortedStrings(oldVal);
        List<String> newSorted = sortedStrings(newVal);
        if (oldSorted.equals(newSorted)) return;
        changes.add(new FieldChange(field, oldSorted.toString(), newSorted.toString()));
    }

    private List<String> sortedStrings(Collection<?> c) {
        if (c == null) return List.of();
        return c.stream().map(Object::toString).sorted().toList();
    }

    private String toStr(Object val) {
        return val == null ? null : val.toString();
    }
}
