package com.mcesnik.planner_backend.exception;

public class TripConflictException extends RuntimeException {

    public enum Code {
        OVERLAPPING_DATES
    }

    private final Code code;

    public TripConflictException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
