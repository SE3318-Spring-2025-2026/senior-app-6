package com.senior.spm.exception;

/**
 * Thrown when a group attempts to send a second advisor request while a PENDING
 * request already exists. Maps to HTTP 409 Conflict via GlobalExceptionHandler.
 */
public class DuplicateRequestException extends RuntimeException {

    public DuplicateRequestException(String message) {
        super(message);
    }
}
