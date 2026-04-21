package com.senior.spm.exception;

/**
 * Thrown when a StaffUser with role=Professor cannot be found by the given ID,
 * or the resolved user is not a Professor. Maps to HTTP 404 via GlobalExceptionHandler.
 */
public class AdvisorNotFoundException extends RuntimeException {

    public AdvisorNotFoundException(String message) {
        super(message);
    }
}
