package com.mcesnik.planner_backend.event;

public record FieldChange(String field, String oldValue, String newValue) {
}
