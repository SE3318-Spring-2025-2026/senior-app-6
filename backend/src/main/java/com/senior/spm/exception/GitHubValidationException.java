package com.senior.spm.exception;

/**
 * Thrown by GitHubValidationService when a live GitHub API call fails.
 * Maps to HTTP 422 Unprocessable Entity.
 */
public class GitHubValidationException extends ExternalToolValidationException {

    public GitHubValidationException(String message) {
        super(message);
    }
}
