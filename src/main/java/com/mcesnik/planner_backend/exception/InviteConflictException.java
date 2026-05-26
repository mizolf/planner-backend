package com.mcesnik.planner_backend.exception;

public class InviteConflictException extends RuntimeException {

    public enum Code {
        SELF_INVITE,
        ALREADY_MEMBER,
        INVITE_NOT_PENDING,
        INVITE_EXPIRED
    }

    private final Code code;

    public InviteConflictException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
