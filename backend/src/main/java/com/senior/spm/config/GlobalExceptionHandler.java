package com.senior.spm.config;

import java.nio.file.AccessDeniedException;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.exception.AdvisorAtCapacityException;
import com.senior.spm.exception.RepositoryException;
import com.senior.spm.exception.AdvisorNotFoundException;
import com.senior.spm.exception.AlreadyInGroupException;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.exception.DuplicateGroupNameException;
import com.senior.spm.exception.DuplicateInvitationException;
import com.senior.spm.exception.DuplicateRequestException;
import com.senior.spm.exception.ExternalToolValidationException;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.GroupNotFoundException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.exception.InvitationNotFoundException;
import com.senior.spm.exception.InvitationNotPendingException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.exception.NotInGroupException;
import com.senior.spm.exception.RequestNotFoundException;
import com.senior.spm.exception.RequestNotPendingException;
import com.senior.spm.exception.ScheduleWindowClosedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorMessage> handleValidationExceptions(MethodArgumentNotValidException ex) {
        var message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorMessage> handleAccessDenied(AccessDeniedException ex) {
        var authority = (SimpleGrantedAuthority) SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .iterator().next();
        var message = "Access denied: " + authority.getAuthority()
                + " does not have permission to access this resource.";
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorMessage(message));
    }

    @ExceptionHandler(RepositoryException.class)
    public ResponseEntity<ErrorMessage> handleRepositoryException(RepositoryException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorMessage("An unexpected error occurred: " + ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorMessage> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorMessage("Invalid request body: " + ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorMessage> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorMessage("An unexpected error occurred: " + ex.getMessage()));
    }

    @ExceptionHandler(ScheduleWindowClosedException.class)
    public ResponseEntity<ErrorMessage> handleScheduleWindowClosed(ScheduleWindowClosedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(ex.getMessage()));
    }

    @ExceptionHandler(AlreadyInGroupException.class)
    public ResponseEntity<ErrorMessage> handleAlreadyInGroup(AlreadyInGroupException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateGroupNameException.class)
    public ResponseEntity<ErrorMessage> handleDuplicateGroupName(DuplicateGroupNameException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorMessage(ex.getMessage()));
    }

    /**
     * Map duplicate pending invitations to HTTP 409 Conflict.
     *
     * @param ex duplicate invitation domain exception
     * @return conflict response with a user-facing error message
     */
    @ExceptionHandler(DuplicateInvitationException.class)
    public ResponseEntity<ErrorMessage> handleDuplicateInvitation(DuplicateInvitationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorMessage(ex.getMessage()));
    }

    @ExceptionHandler(NotInGroupException.class)
    public ResponseEntity<ErrorMessage> handleNotInGroup(NotInGroupException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(ex.getMessage()));
    }

    // Maps JiraValidationException and GitHubValidationException (both extend
    // ExternalToolValidationException) to HTTP 422 Unprocessable Entity.
    // The exception message is the exact user-facing string defined in the API spec
    // (e.g. "JIRA validation failed: API token is invalid or expired").
    @ExceptionHandler(ExternalToolValidationException.class)
    public ResponseEntity<ErrorMessage> handleExternalToolValidation(ExternalToolValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorMessage(ex.getMessage()));
    }

    // Thrown when the authenticated user does not hold the required role or ownership
    // (e.g., a non-TEAM_LEADER attempting to bind tool integrations or send invitations).
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorMessage> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorMessage(ex.getMessage()));
    }

    // Thrown when a request violates a domain business rule (e.g., binding tools on a
    // DISBANDED group, or responding to a locked roster). Distinct from validation
    // errors (400 from @Valid) — these are semantic, not structural, rejections.
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorMessage> handleBusinessRule(BusinessRuleException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(ex.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorMessage> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(ex.getMessage()));
    }

    // Thrown when a group with the given UUID does not exist.
    @ExceptionHandler(GroupNotFoundException.class)
    public ResponseEntity<ErrorMessage> handleGroupNotFound(GroupNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(ex.getMessage()));
    }

    /**
     * Map missing invitations to HTTP 404 Not Found.
     *
     * @param ex invitation not found domain exception
     * @return not found response with a user-facing error message
     */
    @ExceptionHandler(InvitationNotFoundException.class)
    public ResponseEntity<ErrorMessage> handleInvitationNotFound(InvitationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(ex.getMessage()));
    }

    // Thrown when an advisor has reached their maximum group capacity.
    @ExceptionHandler(AdvisorAtCapacityException.class)
    public ResponseEntity<ErrorMessage> handleAdvisorAtCapacity(AdvisorAtCapacityException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(ex.getMessage()));
    }

    // Thrown when an advisor request with the given ID does not exist.
    @ExceptionHandler(RequestNotFoundException.class)
    public ResponseEntity<ErrorMessage> handleRequestNotFound(RequestNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(ex.getMessage()));
    }

    // Thrown when attempting to cancel/respond to an advisor request that is no longer PENDING.
    @ExceptionHandler(RequestNotPendingException.class)
    public ResponseEntity<ErrorMessage> handleRequestNotPending(RequestNotPendingException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(ex.getMessage()));
    }

    // Thrown when an advisor (StaffUser with role=Professor) cannot be found by ID,
    // or the resolved user is not a Professor.
    @ExceptionHandler(AdvisorNotFoundException.class)
    public ResponseEntity<ErrorMessage> handleAdvisorNotFound(AdvisorNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(ex.getMessage()));
    }

    // Thrown when a group tries to send a second advisor request while a PENDING one already
    // exists. Maps to 409 Conflict per the P3 API spec (endpoints_p3.md).
    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<ErrorMessage> handleDuplicateRequest(DuplicateRequestException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorMessage(ex.getMessage()));
    }
    /**
     * Map non-pending invitation lifecycle actions to HTTP 400 Bad Request.
     *
     * @param ex invitation state domain exception
     * @return bad request response with a user-facing error message
     */
    @ExceptionHandler(InvitationNotPendingException.class)
    public ResponseEntity<ErrorMessage> handleInvitationNotPending(InvitationNotPendingException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(ex.getMessage()));
    }

    // Generic resource-not-found mapped to HTTP 404. Use this when a more specific
    // *NotFoundException isn't warranted (e.g., looking up a Deliverable by id).
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorMessage> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(ex.getMessage()));
    }

    // Thrown by stub endpoints (Issue #45) that are not yet implemented.
    // Prevents UnsupportedOperationException from falling through to the 500 catch-all.
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorMessage> handleUnsupportedOperation(UnsupportedOperationException ex) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ErrorMessage("This endpoint is not yet implemented"));
    }
}
