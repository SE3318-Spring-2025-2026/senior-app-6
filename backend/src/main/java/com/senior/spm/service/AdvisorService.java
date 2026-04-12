package com.senior.spm.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.dto.AdvisorCapacityResponse;
import com.senior.spm.controller.dto.AdvisorOverrideResponse;
import com.senior.spm.controller.dto.AdvisorRequestResponse;
import com.senior.spm.entity.AdvisorRequest;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.exception.AdvisorAtCapacityException;
import com.senior.spm.exception.AdvisorNotFoundException;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.GroupNotFoundException;
import com.senior.spm.exception.RequestNotFoundException;
import com.senior.spm.exception.RequestNotPendingException;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.service.dto.AdvisorRequestDetail;
import com.senior.spm.service.dto.AdvisorRequestSummary;
import com.senior.spm.service.dto.AdvisorRespondResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdvisorService {

    private final AdvisorRequestRepository advisorRequestRepository;
    private final ProjectGroupRepository projectGroupRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final StaffUserRepository staffUserRepository;
    private final TermConfigService termConfigService;

    // =========================================================================
    // STUDENT — Browse & Request Flow (P3-API-01)
    // Implemented in: [Backend] AdvisorService — Browse & Request Flow (Issue #59)
    // =========================================================================

    /**
     * Returns all professors whose current active group count is below their capacity.
     * termId is resolved server-side via TermConfigService — never passed from the client.
     *
     * <p>Sequence (DFD 3.1 / sequence 3.1_advisor_request_p3.md):
     * <ol>
     *   <li>Resolve termId via {@link TermConfigService#getActiveTermId()}.</li>
     *   <li>Fetch all StaffUsers with role = PROFESSOR.</li>
     *   <li>For each professor, count non-DISBANDED groups where advisorId = professor.id AND termId = termId.</li>
     *   <li>Include only professors where count {@literal <} advisorCapacity.</li>
     * </ol>
     *
     * @return list of available advisors with capacity info (atCapacity is null/absent)
     *
     * TODO: Issue #59 — [Backend] AdvisorService — Browse & Request Flow
     */
    @Transactional(readOnly = true)
    public List<AdvisorCapacityResponse> getAvailableAdvisors() {
        // TODO: Issue #59 — implement getAvailableAdvisors()
        throw new UnsupportedOperationException("Not implemented yet — see Issue #59");
    }

    /**
     * Sends an advisor request from a student (must be TEAM_LEADER) to the specified advisor.
     *
     * <p>Sequence (DFD 3.1 / sequence 3.1_advisor_request_p3.md):
     * <ol>
     *   <li>Verify requester is TEAM_LEADER of groupId → 403 if not.</li>
     *   <li>Fetch group → 404 if not found; 400 if status != TOOLS_BOUND.</li>
     *   <li>Verify ADVISOR_ASSOCIATION window is active → 400 if not.</li>
     *   <li>Check no PENDING request already exists for the group → 409 if duplicate.</li>
     *   <li>Fetch advisor (must be Professor) → 404 if not found or wrong role.</li>
     *   <li>Check advisor currentGroupCount {@literal <} advisorCapacity → 400 if at capacity.</li>
     *   <li>Persist AdvisorRequest{status=PENDING, sentAt=now} and return 201 response.</li>
     * </ol>
     *
     * @param groupId       UUID of the group
     * @param advisorId     UUID of the target professor
     * @param requesterUUID internal student UUID extracted from JWT
     * @return response with requestId, groupId, advisorId, status=PENDING, sentAt
     *
     * TODO: Issue #59 — [Backend] AdvisorService — Browse & Request Flow
     */
    @Transactional
    public AdvisorRequestResponse sendAdvisorRequest(UUID groupId, UUID advisorId, UUID requesterUUID) {
        // TODO: Issue #59 — implement sendAdvisorRequest()
        throw new UnsupportedOperationException("Not implemented yet — see Issue #59");
    }

    /**
     * Returns the most recent AdvisorRequest for the group (any status).
     * The requester must be a member of the group.
     *
     * <p>Sequence (DFD 3.1 / sequence 3.1_advisor_request_p3.md):
     * <ol>
     *   <li>Verify requester is a member of groupId → 403 if not.</li>
     *   <li>Fetch most recent AdvisorRequest via findTopByGroupIdOrderBySentAtDesc → 404 if none.</li>
     *   <li>Return response including advisorName and respondedAt.</li>
     * </ol>
     *
     * @param groupId       UUID of the group
     * @param studentUUID   internal student UUID extracted from JWT
     * @return response with requestId, advisorId, advisorName, status, sentAt, respondedAt
     *
     * TODO: Issue #59 — [Backend] AdvisorService — Browse & Request Flow
     */
    @Transactional(readOnly = true)
    public AdvisorRequestResponse getAdvisorRequest(UUID groupId, UUID studentUUID) {
        // TODO: Issue #59 — implement getAdvisorRequest()
        throw new UnsupportedOperationException("Not implemented yet — see Issue #59");
    }

    /**
     * Cancels the active PENDING advisor request for the group.
     * The requester must be TEAM_LEADER of the group.
     *
     * <p>Per CLAUDE.md P3 business rules: use findTopByGroupIdOrderBySentAtDesc first.
     * If empty → 404. If found but status != PENDING → 400.
     * Do NOT use findByGroupIdAndStatus(PENDING) for both checks — the 404 case becomes unreachable.
     *
     * <p>Sequence (DFD 3.1 / sequence 3.1_advisor_request_p3.md):
     * <ol>
     *   <li>Verify requester is TEAM_LEADER of groupId → 403 if not.</li>
     *   <li>Fetch group → 404 if not found.</li>
     *   <li>findTopByGroupIdOrderBySentAtDesc → 404 if empty.</li>
     *   <li>Check status == PENDING → 400 if not pending.</li>
     *   <li>Set status = CANCELLED, persist, return response.</li>
     * </ol>
     *
     * @param groupId       UUID of the group
     * @param requesterUUID internal student UUID extracted from JWT
     * @return response with requestId and status=CANCELLED
     *
     * TODO: Issue #59 — [Backend] AdvisorService — Browse & Request Flow
     */
    @Transactional
    public AdvisorRequestResponse cancelAdvisorRequest(UUID groupId, UUID requesterUUID) {
        // TODO: Issue #59 — implement cancelAdvisorRequest()
        throw new UnsupportedOperationException("Not implemented yet — see Issue #59");
    }

    // =========================================================================
    // PROFESSOR — Request Review Flow (P3-API-02)
    // =========================================================================

    /**
     * Returns all PENDING advisor requests addressed to the authenticated professor.
     * An empty list is valid — never returns 404.
     *
     * <p>Sequence (DFD 3.2 / sequence 3.2_3.3_advisor_respond_p3.md):
     * <ol>
     *   <li>Query AdvisorRequestRepository.findByAdvisorIdAndStatus(professorId, PENDING).</li>
     *   <li>Map each request to AdvisorRequestSummary (includes memberCount via groupMembershipRepository).</li>
     * </ol>
     *
     * @param professorId internal UUID of the authenticated Professor (from JWT)
     * @return list of pending request summaries; may be empty
     */
    @Transactional(readOnly = true)
    public List<AdvisorRequestSummary> getPendingRequestsForAdvisor(UUID professorId) {
        List<AdvisorRequest> requests = advisorRequestRepository.findByAdvisorIdAndStatus(professorId, AdvisorRequest.RequestStatus.PENDING);

        return requests.stream().map(req ->
            AdvisorRequestSummary.builder()
                .requestId(req.getId())
                .groupId(req.getGroup().getId())
                .groupName(req.getGroup().getGroupName())
                .termId(req.getGroup().getTermId())
                .memberCount((int) groupMembershipRepository.countByGroupId(req.getGroup().getId()))
                .sentAt(req.getSentAt())
                .build()
        ).collect(Collectors.toList());
    }

    /**
     * Returns full detail for a single advisor request.
     * The authenticated professor must be the target advisor of the request.
     *
     * <p>Sequence (DFD 3.2 / sequence 3.2_3.3_advisor_respond_p3.md):
     * <ol>
     *   <li>findById(requestId) → 404 if not found.</li>
     *   <li>Verify request.advisor.id == professorId → 403 if mismatch.</li>
     *   <li>Build detail response including group members (Proposal summary deferred to P6).</li>
     * </ol>
     *
     * @param requestId   UUID of the advisor request
     * @param professorId internal UUID of the authenticated Professor (from JWT)
     * @return detailed request view with group + member list
     * @throws RequestNotFoundException if no request with the given ID exists
     * @throws ForbiddenException       if the authenticated professor is not the target advisor
     */
    @Transactional(readOnly = true)
    public AdvisorRequestDetail getRequestDetail(UUID requestId, UUID professorId) {
        AdvisorRequest request = advisorRequestRepository.findById(requestId)
            .orElseThrow(() -> new RequestNotFoundException("Request not found"));

        if (!request.getAdvisor().getId().equals(professorId)) {
            throw new ForbiddenException("This request is not addressed to you");
        }

        ProjectGroup group = request.getGroup();

        var members = group.getMembers().stream()
            .map(m -> AdvisorRequestDetail.GroupMemberDetail.builder()
                .studentId(m.getStudent().getStudentId())
                .role(m.getRole().name())
                .joinedAt(m.getJoinedAt())
                .build()
            ).collect(Collectors.toList());

        var groupDetail = AdvisorRequestDetail.RequestGroupDetail.builder()
            .id(group.getId())
            .groupName(group.getGroupName())
            .termId(group.getTermId())
            .status(group.getStatus())
            .members(members)
            .build();

        return AdvisorRequestDetail.builder()
            .requestId(request.getId())
            .group(groupDetail)
            .sentAt(request.getSentAt())
            .build();
    }

    // =========================================================================
    // PROFESSOR — Association Response Flow (P3-API-03)
    // =========================================================================

    /**
     * Processes an advisor's accept or reject decision on a pending request.
     *
     * <p>On {@code accept = true} (all three writes are atomic in one {@code @Transactional}):
     * <ol>
     *   <li>Capacity re-check inside the transaction to guard against concurrent acceptances.</li>
     *   <li>Set request.status = ACCEPTED, request.respondedAt = now.</li>
     *   <li>Set group.advisorId = professor, group.status = ADVISOR_ASSIGNED.</li>
     *   <li>Bulk AUTO_REJECT all other PENDING requests for the same group (excludes this request).</li>
     * </ol>
     *
     * <p>On {@code accept = false}:
     * <ol>
     *   <li>Set request.status = REJECTED, request.respondedAt = now.</li>
     *   <li>Group status remains TOOLS_BOUND; group may send a new request.</li>
     * </ol>
     *
     * @param requestId   UUID of the advisor request
     * @param professorId internal UUID of the authenticated Professor (from JWT)
     * @param accept      {@code true} to accept, {@code false} to reject
     * @return response with requestId, status, and (on accept) groupId + groupStatus
     * @throws RequestNotFoundException  if no request with the given ID exists
     * @throws ForbiddenException        if the authenticated professor is not the target advisor
     * @throws RequestNotPendingException if the request is not in PENDING status
     * @throws AdvisorAtCapacityException if the professor is at capacity at accept time (capacity re-check)
     */
    @Transactional
    public AdvisorRespondResponse respondToRequest(UUID requestId, UUID professorId, boolean accept) {
        AdvisorRequest request = advisorRequestRepository.findById(requestId)
            .orElseThrow(() -> new RequestNotFoundException("Request not found"));

        if (!request.getAdvisor().getId().equals(professorId)) {
            throw new ForbiddenException("This request is not addressed to you");
        }

        if (request.getStatus() != AdvisorRequest.RequestStatus.PENDING) {
            throw new RequestNotPendingException("Request is no longer pending");
        }

        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("UTC"));

        if (!accept) {
            request.setStatus(AdvisorRequest.RequestStatus.REJECTED);
            request.setRespondedAt(now);
            advisorRequestRepository.save(request);

            return AdvisorRespondResponse.builder()
                .requestId(request.getId())
                .status(AdvisorRequest.RequestStatus.REJECTED)
                .build();
        }

        // Accept flow — capacity re-check inside the transaction (CLAUDE.md P3 rule 3.1)
        String activeTermId = termConfigService.getActiveTermId();

        long currentGroupCount = projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                professorId, activeTermId, ProjectGroup.GroupStatus.DISBANDED);

        if (currentGroupCount >= request.getAdvisor().getAdvisorCapacity()) {
            throw new AdvisorAtCapacityException("You have reached your maximum group capacity for this term");
        }

        // Verify group is still TOOLS_BOUND before accepting (prevents stale state issues)
        ProjectGroup group = request.getGroup();
        if (group.getStatus() != ProjectGroup.GroupStatus.TOOLS_BOUND) {
            throw new RequestNotPendingException("Group is no longer in TOOLS_BOUND status");
        }

        // Atomic writes: request → group → auto-reject others
        request.setStatus(AdvisorRequest.RequestStatus.ACCEPTED);
        request.setRespondedAt(now);
        advisorRequestRepository.save(request);

        group.setAdvisor(request.getAdvisor());
        group.setStatus(ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        projectGroupRepository.save(group);

        // Auto-reject all other PENDING requests for this group (same group, different advisors)
        advisorRequestRepository.bulkUpdateStatusForGroup(AdvisorRequest.RequestStatus.AUTO_REJECTED, group.getId(), request.getId());

        return AdvisorRespondResponse.builder()
            .requestId(request.getId())
            .status(AdvisorRequest.RequestStatus.ACCEPTED)
            .groupId(group.getId())
            .groupStatus(ProjectGroup.GroupStatus.ADVISOR_ASSIGNED)
            .build();
    }

    // =========================================================================
    // COORDINATOR — Advisor Override (P3-API-05, DFD 3.5)
    // =========================================================================

    /**
     * Returns all professors with their current group count and capacity for the active term.
     * Unlike the student-facing endpoint, this includes advisors at or above capacity,
     * and adds the {@code atCapacity} flag to each entry.
     *
     * <p>termId is resolved server-side via {@link TermConfigService#getActiveTermId()} —
     * never passed from the client.
     *
     * <p>Sequence (DFD 3.5 / sequence 3.5_coordinator_advisor_p3.md):
     * <ol>
     *   <li>Resolve termId from TermConfigService.</li>
     *   <li>Fetch all StaffUsers with role = PROFESSOR.</li>
     *   <li>For each professor, count non-DISBANDED groups
     *       ({@code countByAdvisorIdAndTermIdAndStatusNot(..., DISBANDED)}).</li>
     *   <li>Set {@code atCapacity = currentGroupCount >= advisorCapacity}.</li>
     * </ol>
     *
     * @return list of all professors with capacity metadata; never null
     */
    @Transactional(readOnly = true)
    public List<AdvisorCapacityResponse> getAllAdvisorsWithCapacity() {
        String termId = termConfigService.getActiveTermId();

        List<StaffUser> professors = staffUserRepository.findByRole(StaffUser.Role.Professor);

        return professors.stream().map(professor -> {
            long currentGroupCount = projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                    professor.getId(), termId, ProjectGroup.GroupStatus.DISBANDED);
            boolean atCapacity = currentGroupCount >= professor.getAdvisorCapacity();

            return AdvisorCapacityResponse.builder()
                    .advisorId(professor.getId())
                    .name(professor.getMail())   // P1 may add a name field to StaffUser; mail used until then
                    .mail(professor.getMail())
                    .currentGroupCount((int) currentGroupCount)
                    .capacity(professor.getAdvisorCapacity())
                    .atCapacity(atCapacity)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Coordinator force-assigns an advisor to a group, bypassing capacity and window checks.
     *
     * <p>Business rule guards (CLAUDE.md P3 rule 3.5 / sequence 3.5_coordinator_advisor_p3.md):
     * <ol>
     *   <li>Find group → 404 if not found.</li>
     *   <li>Check {@code group.status == DISBANDED} → 400 (no DISBANDED → ADVISOR_ASSIGNED transition).</li>
     *   <li>Check {@code group.status} is not {@code TOOLS_BOUND} and not {@code ADVISOR_ASSIGNED} → 400.</li>
     *   <li>Find advisor (must be Professor) → 404 if not found or wrong role.</li>
     *   <li>Check {@code group.advisorId == advisorId} → 400 "already assigned".</li>
     * </ol>
     *
     * <p>Atomic writes (one {@code @Transactional}):
     * <ul>
     *   <li>Set {@code group.advisor}, {@code group.status = ADVISOR_ASSIGNED}, persist.</li>
     *   <li>Bulk AUTO_REJECT all PENDING advisor requests for this group.</li>
     * </ul>
     *
     * <p>No capacity check. No window check. Coordinator override.
     *
     * @param groupId   UUID of the target group
     * @param advisorId UUID of the professor to assign
     * @return updated group state: groupId, status=ADVISOR_ASSIGNED, advisorId
     * @throws GroupNotFoundException   if no group with {@code groupId} exists
     * @throws BusinessRuleException    if group is DISBANDED, status is invalid, or advisor already assigned
     * @throws AdvisorNotFoundException if no Professor with {@code advisorId} exists
     */
    @Transactional
    public AdvisorOverrideResponse assignAdvisor(UUID groupId, UUID advisorId) {
        // 1. Find group
        ProjectGroup group = projectGroupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        // 2. Guard: cannot assign to a disbanded group (CLAUDE.md P3 rule 3.5.a)
        if (group.getStatus() == ProjectGroup.GroupStatus.DISBANDED) {
            throw new BusinessRuleException("Group is disbanded");
        }

        // 3. Guard: status must be TOOLS_BOUND or ADVISOR_ASSIGNED (CLAUDE.md P3 rule 3.5.b)
        if (group.getStatus() != ProjectGroup.GroupStatus.TOOLS_BOUND
                && group.getStatus() != ProjectGroup.GroupStatus.ADVISOR_ASSIGNED) {
            throw new BusinessRuleException("Group must be in TOOLS_BOUND or ADVISOR_ASSIGNED status for advisor assignment");
        }

        // 4. Find advisor — must be a Professor
        StaffUser advisor = staffUserRepository.findById(advisorId)
                .filter(u -> u.getRole() == StaffUser.Role.Professor)
                .orElseThrow(() -> new AdvisorNotFoundException("Advisor not found"));

        // 5. Guard: advisor already assigned
        if (group.getAdvisor() != null && group.getAdvisor().getId().equals(advisorId)) {
            throw new BusinessRuleException("Group already has this advisor assigned");
        }

        // 6. Atomic: update group + auto-reject any PENDING requests
        group.setAdvisor(advisor);
        group.setStatus(ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        projectGroupRepository.save(group);

        advisorRequestRepository.bulkUpdateStatusByGroupId(AdvisorRequest.RequestStatus.AUTO_REJECTED, groupId);

        return AdvisorOverrideResponse.builder()
                .groupId(group.getId())
                .status(ProjectGroup.GroupStatus.ADVISOR_ASSIGNED)
                .advisorId(advisor.getId())
                .build();
    }

    /**
     * Coordinator force-removes the assigned advisor from a group.
     * Reverts group status from {@code ADVISOR_ASSIGNED} to {@code TOOLS_BOUND}.
     *
     * <p>Sequence (DFD 3.5 / sequence 3.5_coordinator_advisor_p3.md):
     * <ol>
     *   <li>Find group → 404 if not found.</li>
     *   <li>Check {@code group.advisor == null} → 400 "Group has no advisor to remove".</li>
     *   <li>Clear {@code group.advisor}, set {@code group.status = TOOLS_BOUND}, persist.</li>
     * </ol>
     *
     * <p>Note per CLAUDE.md P3 rule 3.3: If the coordinator removes an advisor after the window
     * has closed, the group is left at TOOLS_BOUND without a path back to ADVISOR_ASSIGNED
     * except via coordinator force-assign. This is resolved at the UI/docs layer, not here.
     *
     * @param groupId UUID of the group from which to remove the advisor
     * @return updated group state: groupId, status=TOOLS_BOUND, advisorId=null
     * @throws GroupNotFoundException if no group with {@code groupId} exists
     * @throws BusinessRuleException  if the group currently has no advisor
     */
    @Transactional
    public AdvisorOverrideResponse removeAdvisor(UUID groupId) {
        // 1. Find group
        ProjectGroup group = projectGroupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        // 2. Guard: must have an advisor to remove
        if (group.getAdvisor() == null) {
            throw new BusinessRuleException("Group has no advisor to remove");
        }

        // 3. Clear advisor, revert status
        group.setAdvisor(null);
        group.setStatus(ProjectGroup.GroupStatus.TOOLS_BOUND);
        projectGroupRepository.save(group);

        return AdvisorOverrideResponse.builder()
                .groupId(group.getId())
                .status(ProjectGroup.GroupStatus.TOOLS_BOUND)
                .advisorId(null)
                .build();
    }
}
