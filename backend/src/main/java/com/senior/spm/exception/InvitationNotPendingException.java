package com.senior.spm.exception;

/**
 * Thrown when an invitation lifecycle action requires a PENDING invitation.
 */
public class InvitationNotPendingException extends RuntimeException {

    /**
     * Create an invitation state exception with a user-facing message.
     *
     * @param message explanation of why the invitation cannot be changed
     */
    public InvitationNotPendingException(String message) {
        super(message);
    }
}
