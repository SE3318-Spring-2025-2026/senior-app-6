package com.senior.spm.exception;

/**
 * Thrown when a group with the given ID does not exist in the database.
 *
 * Maps to HTTP 404 Not Found via GlobalExceptionHandler.
 */
public class GroupNotFoundException extends NotFoundException {

    public GroupNotFoundException(String message) {
        super(message);
    }
}
