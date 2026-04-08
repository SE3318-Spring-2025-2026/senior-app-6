package com.senior.spm.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.senior.spm.exception.AlreadyInGroupException;
import com.senior.spm.exception.DuplicateGroupNameException;
import com.senior.spm.exception.GitHubValidationException;
import com.senior.spm.exception.JiraValidationException;
import com.senior.spm.exception.NotInGroupException;
import com.senior.spm.exception.ScheduleWindowClosedException;

/**
 * Verifies that GlobalExceptionHandler maps both JiraValidationException and
 * GitHubValidationException to HTTP 422 Unprocessable Entity with the exact
 * user-facing message from the exception.
 *
 * This is the integration point between the validation services and the HTTP
 * response layer. If this mapping is broken, validation failures silently
 * become 500 Internal Server Error responses.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ── JIRA exceptions → 422 ─────────────────────────────────────────────

    @Test
    void jiraValidationException_mapsTo422() {
        var ex = new JiraValidationException("JIRA validation failed: API token is invalid or expired");
        var response = handler.handleExternalToolValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void jiraValidationException_preservesMessageInBody() {
        var message = "JIRA validation failed: Project key 'SPM' not found";
        var ex = new JiraValidationException(message);
        var response = handler.handleExternalToolValidation(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo(message);
    }

    // ── GitHub exceptions → 422 ───────────────────────────────────────────

    @Test
    void gitHubValidationException_mapsTo422() {
        var ex = new GitHubValidationException("GitHub validation failed: PAT is invalid or expired");
        var response = handler.handleExternalToolValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void gitHubValidationException_preservesMessageInBody() {
        var message = "GitHub validation failed: PAT lacks required 'repo' scope";
        var ex = new GitHubValidationException(message);
        var response = handler.handleExternalToolValidation(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo(message);
    }

    // ── P2 exceptions (added in PR #83) ──────────────────────────────────

    @Test
    void scheduleWindowClosedException_mapsTo400() {
        var ex = new ScheduleWindowClosedException("Group creation window is not currently active");
        var response = handler.handleScheduleWindowClosed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo(ex.getMessage());
    }

    @Test
    void alreadyInGroupException_mapsTo400() {
        var ex = new AlreadyInGroupException("You are already a member of a group");
        var response = handler.handleAlreadyInGroup(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo(ex.getMessage());
    }

    @Test
    void duplicateGroupNameException_mapsTo409() {
        var ex = new DuplicateGroupNameException("A group named 'TeamAlpha' already exists for this term");
        var response = handler.handleDuplicateGroupName(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo(ex.getMessage());
    }

    @Test
    void notInGroupException_mapsTo404() {
        var ex = new NotInGroupException("You are not a member of any group");
        var response = handler.handleNotInGroup(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo(ex.getMessage());
    }

    // ── Never returns a different status ─────────────────────────────────

    @Test
    void handler_neverReturns500_forValidationExceptions() {
        var jiraEx = new JiraValidationException("any jira error");
        var githubEx = new GitHubValidationException("any github error");

        assertThat(handler.handleExternalToolValidation(jiraEx).getStatusCode())
                .isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(handler.handleExternalToolValidation(githubEx).getStatusCode())
                .isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
