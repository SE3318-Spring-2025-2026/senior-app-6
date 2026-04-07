package com.senior.spm.exception;

/**
 * Exception thrown when a forbidden operation is attempted.
 * Primary use: preventing removal of TEAM_LEADER role from groups.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
