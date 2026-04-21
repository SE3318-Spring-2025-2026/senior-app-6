package com.senior.spm.exception;

/**
 * Thrown by JiraValidationService when a live JIRA API call fails.
 * Maps to HTTP 422 Unprocessable Entity.
 */
public class JiraValidationException extends ExternalToolValidationException {

    public JiraValidationException(String message) {
        super(message);
    }
}
