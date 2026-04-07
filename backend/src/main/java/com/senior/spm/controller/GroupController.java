package com.senior.spm.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.dto.CreateGroupRequest;
import com.senior.spm.controller.dto.GroupDetailResponse;
import com.senior.spm.service.GroupService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Validated
public class GroupController {

    private final GroupService groupService;

    /**
     * Create a new group for the authenticated student.
     * 
     * Validates that the student is not already in a group, the group name is unique for the current term,
     * and the GROUP_CREATION schedule window is active. On success, creates a ProjectGroup and adds
     * the requesting student as TEAM_LEADER.
     * 
     * @param request the {@link CreateGroupRequest} containing the group name (required)
     * @return {@link ResponseEntity} with status 201 and {@link GroupDetailResponse} containing:
     *         - id: UUID of the created group
     *         - groupName: the group name
     *         - termId: UUID of the current active term
     *         - status: initial status is FORMING
     *         - createdAt: ISO-8601 timestamp
     *         - members: list containing the TEAM_LEADER member record
     *         
     *         Error responses:
     *         - 400: if group creation window is not active, student is already in a group, or group name is duplicate
     *         - 409: if a group with the same name already exists for the term
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
     * Returns the complete group information including members, tool binding status (Jira, GitHub),
     * and all relevant metadata.
     * 
     * @return {@link ResponseEntity} with status 200 and {@link GroupDetailResponse} containing:
     *         - id: UUID of the group
     *         - groupName: the group name
     *         - termId: UUID of the term
     *         - status: current group status (FORMING, TOOLS_PENDING, TOOLS_BOUND, ADVISOR_ASSIGNED, DISBANDED)
     *         - createdAt: ISO-8601 timestamp
     *         - jiraSpaceUrl: JIRA space URL (null if not bound)
     *         - jiraProjectKey: JIRA project key (null if not bound)
     *         - jiraBound: boolean indicating if JIRA is bound
     *         - githubOrgName: GitHub organization name (null if not bound)
     *         - githubBound: boolean indicating if GitHub is bound
     *         - members: list of group members with studentId, role (TEAM_LEADER or MEMBER), and joinedAt
     *         
     *         Error responses:
     *         - 404: if the authenticated student is not a member of any group
     */
    @GetMapping("/my")
    public ResponseEntity<GroupDetailResponse> getMyGroup() {
        // Extract student UUID from JWT
        UUID studentUUID = extractStudentUUIDFromJWT();

        GroupDetailResponse response = groupService.getMyGroup(studentUUID);
        return ResponseEntity.ok(response);
    }

    /**
     * Extract student UUID from SecurityContext
     * Assumes the principal is a String containing the UUID
     */
    private UUID extractStudentUUIDFromJWT() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principal = (String) authentication.getPrincipal();
        // Principal should contain the student UUID
        // TODO: Parse the JWT properly to extract the student UUID
        return UUID.fromString(principal);
    }
}
