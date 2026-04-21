package com.senior.spm.exception;

/**
 * Thrown when a request violates a domain business rule (e.g., attempting to
 * bind tools on a DISBANDED group, or locking a roster that is already locked).
 *
 * Maps to HTTP 400 Bad Request via GlobalExceptionHandler.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
