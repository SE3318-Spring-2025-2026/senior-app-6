package com.senior.spm.exception;

/**
 * Thrown when a group attempts to create a pending invitation that already exists.
 */
public class DuplicateInvitationException extends RuntimeException {

    /**
     * Create a duplicate invitation exception with a user-facing message.
     *
     * @param message explanation of the duplicate invitation conflict
     */
    public DuplicateInvitationException(String message) {
        super(message);
    }
}
