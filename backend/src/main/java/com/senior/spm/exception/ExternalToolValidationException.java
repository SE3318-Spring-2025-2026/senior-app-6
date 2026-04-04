package com.senior.spm.exception;

/**
 * Base exception for external tool validation failures (JIRA, GitHub).
 * Subclasses carry the exact user-facing 422 message defined in the API spec.
 */
public class ExternalToolValidationException extends RuntimeException {

    public ExternalToolValidationException(String message) {
        super(message);
    }
}
