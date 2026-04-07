package com.senior.spm.exception;

/**
 * Exception thrown when a requested project group cannot be found.
 * Thrown during coordinator operations and group lookups when group ID doesn't exist.
 */
public class GroupNotFoundException extends RuntimeException {
    public GroupNotFoundException(String message) {
        super(message);
    }
}
