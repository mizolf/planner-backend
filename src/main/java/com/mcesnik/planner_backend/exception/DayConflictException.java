package com.mcesnik.planner_backend.exception;

public class DayConflictException extends RuntimeException {

    public enum Code {
        DUPLICATE_DATE
    }

    private final Code code;

    public DayConflictException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
