package com.mcesnik.planner_backend.exception;

public class MemberConflictException extends RuntimeException {

    public enum Code {
        OWNER_CANNOT_LEAVE
    }

    private final Code code;

    public MemberConflictException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
