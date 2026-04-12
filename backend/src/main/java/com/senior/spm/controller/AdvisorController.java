package com.senior.spm.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.dto.AdvisorCapacityResponse;
import com.senior.spm.controller.dto.AdvisorRequestResponse;
import com.senior.spm.controller.dto.AdvisorRespondRequest;
import com.senior.spm.controller.dto.SendAdvisorRequestBody;
import com.senior.spm.service.AdvisorService;
import com.senior.spm.service.dto.AdvisorRequestDetail;
import com.senior.spm.service.dto.AdvisorRequestSummary;
import com.senior.spm.service.dto.AdvisorRespondResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for all P3 advisor-related endpoints.
 *
 * <p>Handles three groups of endpoints:
 * <ul>
 *   <li><strong>Student-facing</strong> ({@code /api/advisors}, {@code /api/groups/{groupId}/advisor-request}) —
 *       authenticated student JWT required; role enforced at service layer.</li>
 *   <li><strong>Professor-facing</strong> ({@code /api/advisor/requests/**}) —
 *       {@code hasRole("PROFESSOR")} enforced by {@code SecurityConfig} for the entire
 *       {@code /api/advisor/**} path.</li>
 * </ul>
 *
 * <p>Principal extraction: both student and staff JWTs set the principal to the internal UUID
 * string (via {@code JwtAuthenticationFilter} → {@code claims.get("id", String.class)}).
 */
@RestController
@RequiredArgsConstructor
@Validated
public class AdvisorController {

    private final AdvisorService advisorService;

    // =========================================================================
    // STUDENT — Browse Advisors (GET /api/advisors)
    // DFD 3.1 | Issue P3-API-01
    // =========================================================================

    /**
     * Returns all professors whose current group count is below their capacity for the active term.
     * termId is resolved server-side — never passed from the client.
     *
     * <p>Auth: Student JWT (any authenticated student).
     *
     * @return 200 with list of available advisors; empty list when none are available
     *
     * TODO: Issue #59 — student service method not yet implemented; returns 501 until then
     */
    @GetMapping("/api/advisors")
    public ResponseEntity<List<AdvisorCapacityResponse>> getAvailableAdvisors() {
        List<AdvisorCapacityResponse> advisors = advisorService.getAvailableAdvisors();
        return ResponseEntity.ok(advisors);
    }

    // =========================================================================
    // STUDENT — Advisor Request Lifecycle (POST/GET/DELETE /api/groups/{groupId}/advisor-request)
    // DFD 3.1 | Issue P3-API-01
    // =========================================================================

    /**
     * Sends an advisor request from the TEAM_LEADER of the group to the specified professor.
     *
     * <p>Preconditions (enforced at service layer):
     * <ul>
     *   <li>Requester must be TEAM_LEADER of groupId → 403</li>
     *   <li>Group status must be TOOLS_BOUND → 400</li>
     *   <li>ADVISOR_ASSOCIATION window must be active → 400</li>
     *   <li>No PENDING request may already exist for this group → 409</li>
     *   <li>Target advisor must exist and be a Professor with available capacity → 404 / 400</li>
     * </ul>
     *
     * <p>Auth: Student JWT (requester must be TEAM_LEADER of groupId).
     *
     * @param groupId UUID of the group
     * @param body    {@link SendAdvisorRequestBody} containing the target advisorId
     * @return 201 with {@link AdvisorRequestResponse} containing requestId, groupId, advisorId, status=PENDING, sentAt
     *
     * TODO: Issue #59 — student service method not yet implemented; returns 501 until then
     */
    @PostMapping("/api/groups/{groupId}/advisor-request")
    public ResponseEntity<AdvisorRequestResponse> sendAdvisorRequest(
            @PathVariable UUID groupId,
            @Valid @RequestBody SendAdvisorRequestBody body) {
        UUID requesterUUID = extractPrincipalUUID();
        AdvisorRequestResponse response = advisorService.sendAdvisorRequest(groupId, body.getAdvisorId(), requesterUUID);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns the most recent advisor request for the group (any status).
     * The requester must be a member of the group.
     *
     * <p>Auth: Student JWT (requester must be a member of groupId).
     *
     * @param groupId UUID of the group
     * @return 200 with {@link AdvisorRequestResponse} containing requestId, advisorId, advisorName, status, sentAt, respondedAt
     *
     * TODO: Issue #59 — student service method not yet implemented; returns 501 until then
     */
    @GetMapping("/api/groups/{groupId}/advisor-request")
    public ResponseEntity<AdvisorRequestResponse> getAdvisorRequest(
            @PathVariable UUID groupId) {
        UUID studentUUID = extractPrincipalUUID();
        AdvisorRequestResponse response = advisorService.getAdvisorRequest(groupId, studentUUID);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancels the active PENDING advisor request for the group.
     * Only the TEAM_LEADER may cancel.
     *
     * <p>Auth: Student JWT (requester must be TEAM_LEADER of groupId).
     *
     * @param groupId UUID of the group
     * @return 200 with {@link AdvisorRequestResponse} containing requestId and status=CANCELLED
     *
     * TODO: Issue #59 — student service method not yet implemented; returns 501 until then
     */
    @DeleteMapping("/api/groups/{groupId}/advisor-request")
    public ResponseEntity<AdvisorRequestResponse> cancelAdvisorRequest(
            @PathVariable UUID groupId) {
        UUID requesterUUID = extractPrincipalUUID();
        AdvisorRequestResponse response = advisorService.cancelAdvisorRequest(groupId, requesterUUID);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // PROFESSOR — Pending Request List (GET /api/advisor/requests)
    // DFD 3.2 | Issue P3-API-02
    // SecurityConfig: /api/advisor/** → hasRole("PROFESSOR")
    // =========================================================================

    /**
     * Returns all PENDING advisor requests addressed to the authenticated professor.
     * An empty list is valid — this endpoint never returns 404.
     *
     * <p>Auth: Staff JWT with role=Professor (enforced by SecurityConfig for /api/advisor/**).
     *
     * @return 200 with list of {@link AdvisorRequestSummary}; may be empty
     */
    @GetMapping("/api/advisor/requests")
    public ResponseEntity<List<AdvisorRequestSummary>> getPendingRequests() {
        UUID professorId = extractPrincipalUUID();
        List<AdvisorRequestSummary> summaries = advisorService.getPendingRequestsForAdvisor(professorId);
        return ResponseEntity.ok(summaries);
    }

    /**
     * Returns full detail for a single advisor request.
     * The authenticated professor must be the target advisor.
     *
     * <p>Auth: Staff JWT with role=Professor (enforced by SecurityConfig for /api/advisor/**).
     *
     * @param requestId UUID of the advisor request
     * @return 200 with {@link AdvisorRequestDetail} including group and member list
     *
     *         Error responses:
     *         - 403: authenticated professor is not the target advisor
     *         - 404: request not found
     */
    @GetMapping("/api/advisor/requests/{requestId}")
    public ResponseEntity<AdvisorRequestDetail> getRequestDetail(
            @PathVariable UUID requestId) {
        UUID professorId = extractPrincipalUUID();
        AdvisorRequestDetail detail = advisorService.getRequestDetail(requestId, professorId);
        return ResponseEntity.ok(detail);
    }

    // =========================================================================
    // PROFESSOR — Respond to Request (PATCH /api/advisor/requests/{requestId}/respond)
    // DFD 3.3 | Issue P3-API-03
    // SecurityConfig: /api/advisor/** → hasRole("PROFESSOR")
    // =========================================================================

    /**
     * Processes the authenticated professor's accept or reject decision.
     *
     * <p>On {@code accept: true} — atomically:
     * <ul>
     *   <li>Capacity re-check inside the transaction (prevents over-capacity race).</li>
     *   <li>Sets {@code request.status = ACCEPTED}, {@code group.status = ADVISOR_ASSIGNED},
     *       {@code group.advisorId = professor}.</li>
     *   <li>Bulk AUTO_REJECTs all other PENDING requests for the same group.</li>
     * </ul>
     *
     * <p>On {@code accept: false} — only this request is set to REJECTED; group stays TOOLS_BOUND.
     *
     * <p>Auth: Staff JWT with role=Professor (enforced by SecurityConfig for /api/advisor/**).
     *
     * @param requestId UUID of the advisor request
     * @param body      {@link AdvisorRespondRequest} with {@code accept} boolean
     * @return 200 with {@link AdvisorRespondResponse}
     *
     *         Error responses:
     *         - 400: request is no longer PENDING; or professor is at capacity (on accept)
     *         - 403: authenticated professor is not the target advisor
     *         - 404: request not found
     */
    @PatchMapping("/api/advisor/requests/{requestId}/respond")
    public ResponseEntity<AdvisorRespondResponse> respondToRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody AdvisorRespondRequest body) {
        UUID professorId = extractPrincipalUUID();
        AdvisorRespondResponse response = advisorService.respondToRequest(requestId, professorId, body.getAccept());
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Extracts the internal UUID of the authenticated principal from the SecurityContext.
     * Works for both student and staff JWTs — JwtAuthenticationFilter sets the principal
     * to {@code claims.get("id", String.class)} which is the entity's primary key UUID.
     */
    private UUID extractPrincipalUUID() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString((String) auth.getPrincipal());
    }
}
