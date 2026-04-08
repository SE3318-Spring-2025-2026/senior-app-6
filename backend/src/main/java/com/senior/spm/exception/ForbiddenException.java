package com.senior.spm.exception;

/**
 * Thrown when the authenticated user does not have the required role or
 * ownership to perform an action (e.g., a non-TEAM_LEADER attempting to bind
 * tool integrations).
 *
 * Maps to HTTP 403 Forbidden via GlobalExceptionHandler.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
