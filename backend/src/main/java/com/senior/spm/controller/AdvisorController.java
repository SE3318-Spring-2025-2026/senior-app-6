package com.senior.spm.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.AdvisorRespondRequest;
import com.senior.spm.controller.request.ScrumGradeRequest;
import com.senior.spm.controller.response.ActiveSprintResponse;
import com.senior.spm.controller.response.AdvisorCapacityResponse;
import com.senior.spm.controller.response.AdvisorGroupSprintSummaryResponse;
import com.senior.spm.controller.response.ScrumGradeResponse;
import com.senior.spm.controller.response.SprintTrackingResponse;
import com.senior.spm.entity.ScrumGrade;
import com.senior.spm.service.AdvisorService;
import com.senior.spm.service.ScrumGradingService;
import com.senior.spm.service.dto.AdvisorRequestDetail;
import com.senior.spm.service.dto.AdvisorRequestSummary;
import com.senior.spm.service.dto.AdvisorRespondResponse;
import com.senior.spm.util.SecurityUtils;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for all P3 advisor-related endpoints.
 *
 * <p>
 * Handles three groups of endpoints:
 * <ul>
 * <li><strong>Student-facing</strong> ({@code /api/advisors},
 * {@code /api/groups/{groupId}/advisor-request}) — authenticated student JWT
 * required; role enforced at service layer.</li>
 * <li><strong>Professor-facing</strong> ({@code /api/advisor/requests/**}) —
 *       {@code hasRole("PROFESSOR")} enforced by {@code SecurityConfig} for the
 * entire {@code /api/advisor/**} path.</li>
 * </ul>
 *
 * <p>
 * Principal extraction: both student and staff JWTs set the principal to the
 * internal UUID string (via {@code JwtAuthenticationFilter} →
 * {@code claims.get("id", String.class)}).
 */
@RestController
@RequestMapping("/api/advisor")
@RequiredArgsConstructor
@Validated
public class AdvisorController {

    private final AdvisorService advisorService;
    private final ScrumGradingService scrumGradingService;

    // =========================================================================
    // STUDENT — Browse Advisors (GET /api/advisors)
    // DFD 3.1 | Issue P3-API-01
    // =========================================================================
    /**
     * Returns all professors whose current group count is below their capacity
     * for the active term. termId is resolved server-side — never passed from
     * the client.
     *
     * <p>
     * Auth: Student JWT (any authenticated student).
     *
     * @return 200 with list of available advisors; empty list when none are
     * available
     */
    @GetMapping
    public ResponseEntity<List<AdvisorCapacityResponse>> getAvailableAdvisors() {
        List<AdvisorCapacityResponse> advisors = advisorService.getAvailableAdvisors();
        return ResponseEntity.ok(advisors);
    }

    // =========================================================================
    // PROFESSOR — Pending Request List (GET /api/advisor/requests)
    // DFD 3.2 | Issue P3-API-02
    // SecurityConfig: /api/advisor/** → hasRole("PROFESSOR")
    // =========================================================================
    /**
     * Returns all PENDING advisor requests addressed to the authenticated
     * professor. An empty list is valid — this endpoint never returns 404.
     *
     * <p>
     * Auth: Staff JWT with role=Professor (enforced by SecurityConfig for
     * /api/advisor/**).
     *
     * @return 200 with list of {@link AdvisorRequestSummary}; may be empty
     */
    @GetMapping("/requests")
    public ResponseEntity<List<AdvisorRequestSummary>> getPendingRequests(Authentication auth) {
        UUID professorId = SecurityUtils.extractPrincipalUUID(auth);
        List<AdvisorRequestSummary> summaries = advisorService.getPendingRequestsForAdvisor(professorId);
        return ResponseEntity.ok(summaries);
    }

    /**
     * Returns full detail for a single advisor request. The authenticated
     * professor must be the target advisor.
     *
     * <p>
     * Auth: Staff JWT with role=Professor (enforced by SecurityConfig for
     * /api/advisor/**).
     *
     * @param requestId UUID of the advisor request
     * @return 200 with {@link AdvisorRequestDetail} including group and member
     * list
     *
     * Error responses: - 403: authenticated professor is not the target advisor
     * - 404: request not found
     */
    @GetMapping("/requests/{requestId}")
    public ResponseEntity<AdvisorRequestDetail> getRequestDetail(
            @PathVariable UUID requestId,
            Authentication auth) {
        UUID professorId = SecurityUtils.extractPrincipalUUID(auth);
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
     * <p>
     * On {@code accept: true} — atomically:
     * <ul>
     * <li>Capacity re-check inside the transaction (prevents over-capacity
     * race).</li>
     * <li>Sets {@code request.status = ACCEPTED},
     * {@code group.status = ADVISOR_ASSIGNED},
     * {@code group.advisorId = professor}.</li>
     * <li>Bulk AUTO_REJECTs all other PENDING requests for the same group.</li>
     * </ul>
     *
     * <p>
     * On {@code accept: false} — only this request is set to REJECTED; group
     * stays TOOLS_BOUND.
     *
     * <p>
     * Auth: Staff JWT with role=Professor (enforced by SecurityConfig for
     * /api/advisor/**).
     *
     * @param requestId UUID of the advisor request
     * @param body {@link AdvisorRespondRequest} with {@code accept} boolean
     * @return 200 with {@link AdvisorRespondResponse}
     *
     * Error responses: - 400: request is no longer PENDING; or professor is at
     * capacity (on accept) - 403: authenticated professor is not the target
     * advisor - 404: request not found
     */
    @PatchMapping("/requests/{requestId}/respond")
    public ResponseEntity<AdvisorRespondResponse> respondToRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody AdvisorRespondRequest body,
            Authentication auth) {
        UUID professorId = SecurityUtils.extractPrincipalUUID(auth);
        AdvisorRespondResponse response = advisorService.respondToRequest(requestId, professorId, body.getAccept());
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // PROFESSOR — Sprint tracking endpoints (P5)
    // SecurityConfig: /api/advisor/** → hasRole("PROFESSOR") covers all below
    // =========================================================================

    // GET /api/advisor/sprints/active
    @GetMapping("/sprints/active")
    public ResponseEntity<ActiveSprintResponse> getAdvisorActiveSprint() {
        return ResponseEntity.ok(scrumGradingService.getActiveSprint());
    }

    // GET /api/advisor/sprints/{sprintId}/groups
    @GetMapping("/sprints/{sprintId}/groups")
    public ResponseEntity<List<AdvisorGroupSprintSummaryResponse>> getAdvisorGroupSummaries(
            @PathVariable UUID sprintId,
            Authentication auth) {
        UUID advisorId = SecurityUtils.extractPrincipalUUID(auth);
        return ResponseEntity.ok(scrumGradingService.getAdvisorGroupSummaries(advisorId, sprintId));
    }

    // GET /api/advisor/sprints/{sprintId}/groups/{groupId}/tracking
    @GetMapping("/sprints/{sprintId}/groups/{groupId}/tracking")
    public ResponseEntity<SprintTrackingResponse> getAdvisorGroupTracking(
            @PathVariable UUID sprintId,
            @PathVariable UUID groupId,
            Authentication auth) {
        UUID advisorId = SecurityUtils.extractPrincipalUUID(auth);
        return ResponseEntity.ok(scrumGradingService.getAdvisorGroupTracking(advisorId, groupId, sprintId));
    }

    // POST /api/advisor/sprints/{sprintId}/groups/{groupId}/grade
    // 201 on first submission, 200 on update
    @PostMapping("/sprints/{sprintId}/groups/{groupId}/grade")
    public ResponseEntity<ScrumGradeResponse> submitGrade(
            @PathVariable UUID sprintId,
            @PathVariable UUID groupId,
            @Valid @RequestBody ScrumGradeRequest request,
            Authentication auth) {
        UUID advisorId = SecurityUtils.extractPrincipalUUID(auth);
        ScrumGrade grade = scrumGradingService.submitGrade(advisorId, groupId, sprintId, request);
        ScrumGradeResponse response = scrumGradingService.toGradeResponse(grade);
        HttpStatus status = grade.getUpdatedAt() == null ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    // GET /api/advisor/sprints/{sprintId}/groups/{groupId}/grade
    @GetMapping("/sprints/{sprintId}/groups/{groupId}/grade")
    public ResponseEntity<ScrumGradeResponse> getGrade(
            @PathVariable UUID sprintId,
            @PathVariable UUID groupId,
            Authentication auth) {
        UUID advisorId = SecurityUtils.extractPrincipalUUID(auth);
        ScrumGrade grade = scrumGradingService.getGrade(advisorId, groupId, sprintId);
        return ResponseEntity.ok(scrumGradingService.toGradeResponse(grade));
    }

}
