package com.senior.spm.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.senior.spm.exception.AlreadyInGroupException;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.exception.DuplicateGroupNameException;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.GitHubValidationException;
import com.senior.spm.exception.GroupNotFoundException;
import com.senior.spm.exception.JiraValidationException;
import com.senior.spm.exception.NotInGroupException;
import com.senior.spm.exception.ScheduleWindowClosedException;

/**
 * Verifies that GlobalExceptionHandler maps exceptions to correct HTTP status codes.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ── Tool validation → 422 ─────────────────────────────────────────────

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

    @Test
    void handler_neverReturns500_forValidationExceptions() {
        var jiraEx = new JiraValidationException("any jira error");
        var githubEx = new GitHubValidationException("any github error");

        assertThat(handler.handleExternalToolValidation(jiraEx).getStatusCode())
                .isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(handler.handleExternalToolValidation(githubEx).getStatusCode())
                .isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── P2 exceptions ─────────────────────────────────────────────────────

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

    @Test
    void forbiddenException_mapsTo403() {
        var ex = new ForbiddenException("Only the Team Leader can bind tool integrations");
        var response = handler.handleForbidden(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo(ex.getMessage());
    }

    @Test
    void businessRuleException_mapsTo400() {
        var ex = new BusinessRuleException("This group has been disbanded");
        var response = handler.handleBusinessRule(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo(ex.getMessage());
    }

    @Test
    void businessRuleException_preservesMessage() {
        var message = "This group has been disbanded";
        var response = handler.handleBusinessRule(new BusinessRuleException(message));

        assertThat(response.getBody().getMessage()).isEqualTo(message);
    }

    @Test
    void groupNotFoundException_mapsTo404() {
        var ex = new GroupNotFoundException("Group not found");
        var response = handler.handleGroupNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo(ex.getMessage());
    }

    @Test
    void unsupportedOperationException_mapsTo501() {
        // Stub endpoints (Issue #45) throw UnsupportedOperationException.
        // Handler must return 501 Not Implemented — NOT 500 Internal Server Error.
        var ex = new UnsupportedOperationException("Not implemented yet");
        var response = handler.handleUnsupportedOperation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
    }
}
