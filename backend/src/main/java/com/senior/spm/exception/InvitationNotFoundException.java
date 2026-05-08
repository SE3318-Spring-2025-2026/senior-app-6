package com.senior.spm.exception;

/**
 * Thrown when an invitation id does not resolve to an existing invitation.
 */
public class InvitationNotFoundException extends NotFoundException {

    /**
     * Create an invitation not found exception with a user-facing message.
     *
     * @param message explanation of the missing invitation
     */
    public InvitationNotFoundException(String message) {
        super(message);
    }
}
