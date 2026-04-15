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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.BindGithubRequest;
import com.senior.spm.controller.request.BindJiraRequest;
import com.senior.spm.controller.response.AdvisorRequestResponse;
import com.senior.spm.controller.response.BindToolResponse;
import com.senior.spm.controller.request.CreateGroupRequest;
import com.senior.spm.controller.request.SendAdvisorRequestBody;
import com.senior.spm.controller.response.GroupDetailResponse;
import com.senior.spm.controller.response.InvitationResponse;
import com.senior.spm.controller.request.SendInvitationRequest;
import com.senior.spm.service.AdvisorService;
import com.senior.spm.service.GroupService;
import com.senior.spm.service.InvitationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Validated
public class GroupController {

    private final GroupService groupService;
    private final InvitationService invitationService;
    private final AdvisorService advisorService;

    /**
     * Create a new group for the authenticated student.
     *
     * Validates that the student is not already in a group, the group name is
     * unique for the current term, and the GROUP_CREATION schedule window is
     * active. On success, creates a ProjectGroup and adds the requesting
     * student as TEAM_LEADER.
     *
     * @param request the {@link CreateGroupRequest} containing the group name
     * (required)
     * @return {@link ResponseEntity} with status 201 and
     * {@link GroupDetailResponse} containing: - id: UUID of the created group -
     * groupName: the group name - termId: UUID of the current active term -
     * status: initial status is FORMING - createdAt: ISO-8601 timestamp -
     * members: list containing the TEAM_LEADER member record
     *
     * Error responses: - 400: if group creation window is not active, student
     * is already in a group, or group name is duplicate - 409: if a group with
     * the same name already exists for the term
     */
    @PostMapping
    public ResponseEntity<GroupDetailResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request
    ) {
        // Extract student UUID from JWT
        UUID studentUUID = extractStudentUUIDFromJWT();

        GroupDetailResponse response = groupService.createGroup(request.getGroupName(), studentUUID);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieve the authenticated student's current group details.
     *
     * Returns the complete group information including members, tool binding
     * status (Jira, GitHub), and all relevant metadata.
     *
     * @return {@link ResponseEntity} with status 200 and
     * {@link GroupDetailResponse} containing: - id: UUID of the group -
     * groupName: the group name - termId: UUID of the term - status: current
     * group status (FORMING, TOOLS_PENDING, TOOLS_BOUND, ADVISOR_ASSIGNED,
     * DISBANDED) - createdAt: ISO-8601 timestamp - jiraSpaceUrl: JIRA space URL
     * (null if not bound) - jiraProjectKey: JIRA project key (null if not
     * bound) - jiraBound: boolean indicating if JIRA is bound - githubOrgName:
     * GitHub organization name (null if not bound) - githubBound: boolean
     * indicating if GitHub is bound - members: list of group members with
     * studentId, role (TEAM_LEADER or MEMBER), and joinedAt
     *
     * Error responses: - 404: if the authenticated student is not a member of
     * any group
     */
    @GetMapping("/my")
    public ResponseEntity<GroupDetailResponse> getMyGroup() {
        // Extract student UUID from JWT
        UUID studentUUID = extractStudentUUIDFromJWT();

        GroupDetailResponse response = groupService.getMyGroup(studentUUID);
        return ResponseEntity.ok(response);
    }

    /**
     * Send a group invitation to a target student. Auth: Student JWT (must be
     * TEAM_LEADER of groupId) POST /api/groups/{groupId}/invitations
     *
     * @param groupId UUID of the inviting group
     * @param request request body containing the target student's public
     * student id
     * @return {@link ResponseEntity} with status 201 and the created invitation
     * summary
     * @throws com.senior.spm.exception.GroupNotFoundException if the group does
     * not exist
     * @throws com.senior.spm.exception.ForbiddenException if the caller is not
     * the team leader
     * @throws com.senior.spm.exception.BusinessRuleException if the group
     * cannot send invitations
     * @throws com.senior.spm.exception.AlreadyInGroupException if the target
     * student is already grouped
     * @throws com.senior.spm.exception.DuplicateInvitationException if a
     * pending invitation already exists
     */
    @PostMapping("/{groupId}/invitations")
    public ResponseEntity<InvitationResponse> sendInvitation(
            @PathVariable UUID groupId,
            @Valid @RequestBody SendInvitationRequest request
    ) {
        InvitationResponse response = invitationService.sendInvitation(
                groupId,
                extractStudentUUIDFromJWT(),
                request.getTargetStudentId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all invitations sent by the group. Auth: Student JWT (must be
     * TEAM_LEADER of groupId) GET /api/groups/{groupId}/invitations
     *
     * @param groupId UUID of the group whose invitation history will be listed
     * @return {@link ResponseEntity} with status 200 and invitation summaries
     * for the group
     * @throws com.senior.spm.exception.GroupNotFoundException if the group does
     * not exist
     * @throws com.senior.spm.exception.ForbiddenException if the caller is not
     * the team leader
     */
    @GetMapping("/{groupId}/invitations")
    public ResponseEntity<List<InvitationResponse>> getGroupInvitations(
            @PathVariable UUID groupId
    ) {
        List<InvitationResponse> response = invitationService.getGroupInvitations(
                groupId,
                extractStudentUUIDFromJWT()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Bind a JIRA workspace to the group.
     *
     * <p>
     * Validates the JIRA credentials with a live API call before persisting
     * anything. The API token is encrypted (AES-256-GCM) before storage — it
     * never appears in the response or database in plaintext.
     *
     * <p>
     * Auth: Student JWT — requester must be {@code TEAM_LEADER} of
     * {@code groupId}.
     *
     * @param groupId UUID of the group to bind
     * @param request {@link BindJiraRequest} containing {@code jiraSpaceUrl},
     * {@code jiraProjectKey}, and {@code jiraApiToken} (all required)
     * @return 200 with {@link BindToolResponse} containing non-sensitive JIRA
     * metadata and updated group status
     *
     * Error responses: - 400: group is DISBANDED or DTO validation fails - 403:
     * requester is not TEAM_LEADER - 404: group not found - 422: JIRA live
     * validation failed (invalid token, unknown project key, unreachable URL)
     */
    @PostMapping("/{groupId}/jira")
    public ResponseEntity<BindToolResponse> bindJira(
            @PathVariable UUID groupId,
            @Valid @RequestBody BindJiraRequest request
    ) {
        UUID requesterUUID = extractStudentUUIDFromJWT();
        BindToolResponse response = groupService.bindJira(
                groupId,
                request.getJiraSpaceUrl(),
                request.getJiraProjectKey(),
                request.getJiraApiToken(),
                requesterUUID
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Bind a GitHub organization to the group.
     *
     * <p>
     * Validates the GitHub PAT with two sequential live API calls (org
     * existence + {@code repo} scope check) before persisting anything. The PAT
     * is encrypted (AES-256-GCM) before storage — it never appears in the
     * response or database in plaintext.
     *
     * <p>
     * Auth: Student JWT — requester must be {@code TEAM_LEADER} of
     * {@code groupId}.
     *
     * @param groupId UUID of the group to bind
     * @param request {@link BindGithubRequest} containing {@code githubOrgName}
     * and {@code githubPat} (both required)
     * @return 200 with {@link BindToolResponse} containing non-sensitive GitHub
     * metadata and updated group status
     *
     * Error responses: - 400: group is DISBANDED or DTO validation fails - 403:
     * requester is not TEAM_LEADER - 404: group not found - 422: GitHub live
     * validation failed (invalid PAT, org not found, missing repo scope)
     */
    @PostMapping("/{groupId}/github")
    public ResponseEntity<BindToolResponse> bindGithub(
            @PathVariable UUID groupId,
            @Valid @RequestBody BindGithubRequest request
    ) {
        UUID requesterUUID = extractStudentUUIDFromJWT();
        BindToolResponse response = groupService.bindGitHub(
                groupId,
                request.getGithubOrgName(),
                request.getGithubPat(),
                requesterUUID
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Extract student UUID from SecurityContext Assumes the principal is a
     * String containing the UUID
     */
    private UUID extractStudentUUIDFromJWT() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // JwtAuthenticationFilter sets the principal to claims.get("id", String.class),
        // which JWTService writes as student.getId() (the internal UUID PK).
        String principal = (String) authentication.getPrincipal();
        return UUID.fromString(principal);
    }

    // =========================================================================
    // STUDENT — Advisor Request Lifecycle (POST/GET/DELETE /api/groups/{groupId}/advisor-request)
    // DFD 3.1 | Issue P3-API-01
    // =========================================================================
    /**
     * Sends an advisor request from the TEAM_LEADER of the group to the
     * specified professor.
     *
     * <p>
     * Preconditions (enforced at service layer):
     * <ul>
     * <li>Requester must be TEAM_LEADER of groupId → 403</li>
     * <li>Group status must be TOOLS_BOUND → 400</li>
     * <li>ADVISOR_ASSOCIATION window must be active → 400</li>
     * <li>No PENDING request may already exist for this group → 409</li>
     * <li>Target advisor must exist and be a Professor with available capacity
     * → 404 / 400</li>
     * </ul>
     *
     * <p>
     * Auth: Student JWT (requester must be TEAM_LEADER of groupId).
     *
     * @param groupId UUID of the group
     * @param body {@link SendAdvisorRequestBody} containing the target
     * advisorId
     * @return 201 with {@link AdvisorRequestResponse} containing requestId,
     * groupId, advisorId, status=PENDING, sentAt
     */
    @PostMapping("/{groupId}/advisor-request")
    public ResponseEntity<AdvisorRequestResponse> sendAdvisorRequest(
            @PathVariable UUID groupId,
            @Valid @RequestBody SendAdvisorRequestBody body) {
        UUID requesterUUID = extractPrincipalUUID();
        AdvisorRequestResponse response = advisorService.sendAdvisorRequest(groupId, body.getAdvisorId(), requesterUUID);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns the most recent advisor request for the group (any status). The
     * requester must be a member of the group.
     *
     * <p>
     * Auth: Student JWT (requester must be a member of groupId).
     *
     * @param groupId UUID of the group
     * @return 200 with {@link AdvisorRequestResponse} containing requestId,
     * advisorId, advisorName, status, sentAt, respondedAt
     */
    @GetMapping("/{groupId}/advisor-request")
    public ResponseEntity<AdvisorRequestResponse> getAdvisorRequest(
            @PathVariable UUID groupId) {
        UUID studentUUID = extractPrincipalUUID();
        AdvisorRequestResponse response = advisorService.getAdvisorRequest(groupId, studentUUID);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancels the active PENDING advisor request for the group. Only the
     * TEAM_LEADER may cancel.
     *
     * <p>
     * Auth: Student JWT (requester must be TEAM_LEADER of groupId).
     *
     * @param groupId UUID of the group
     * @return 200 with {@link AdvisorRequestResponse} containing requestId and
     * status=CANCELLED
     */
    @DeleteMapping("/{groupId}/advisor-request")
    public ResponseEntity<AdvisorRequestResponse> cancelAdvisorRequest(
            @PathVariable UUID groupId) {
        UUID requesterUUID = extractPrincipalUUID();
        AdvisorRequestResponse response = advisorService.cancelAdvisorRequest(groupId, requesterUUID);
        return ResponseEntity.ok(response);
    }

    // Code duplication but i cant care less
    private UUID extractPrincipalUUID() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString((String) auth.getPrincipal());
    }
}
