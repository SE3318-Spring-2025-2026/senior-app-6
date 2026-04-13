package com.senior.spm.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.dto.BindToolResponse;
import com.senior.spm.controller.dto.GroupDetailResponse;
import com.senior.spm.controller.response.GroupSummaryResponse;
import com.senior.spm.entity.AdvisorRequest.RequestStatus;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.entity.Student;
import com.senior.spm.exception.AlreadyInGroupException;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.exception.DuplicateGroupNameException;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.GroupNotFoundException;
import com.senior.spm.exception.NotInGroupException;
import com.senior.spm.exception.ScheduleWindowClosedException;
import com.senior.spm.exception.StudentNotFoundException;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.GroupInvitationRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.ScheduleWindowRepository;
import com.senior.spm.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final ScheduleWindowRepository scheduleWindowRepository;
    private final ProjectGroupRepository projectGroupRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final StudentRepository studentRepository;
    private final GroupInvitationRepository groupInvitationRepository;
    private final AdvisorRequestRepository advisorRequestRepository;
    private final TermConfigService termConfigService;
    private final JiraValidationService jiraValidationService;
    private final GitHubValidationService gitHubValidationService;
    private final EncryptionService encryptionService;

    @Transactional
    public GroupDetailResponse createGroup(String groupName, UUID studentUUID) {
        // 1. Get active term ID
        String termId = termConfigService.getActiveTermId();

        // 2. Check if schedule window is open
        ScheduleWindow window = scheduleWindowRepository
            .findByTermIdAndType(termId, ScheduleWindow.WindowType.GROUP_CREATION)
            .orElseThrow(() -> new ScheduleWindowClosedException(
                "Group creation window is not currently active"
            ));

        // Verify window is currently active: opensAt <= now <= closesAt
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        if (window.getOpensAt().isAfter(now) || window.getClosesAt().isBefore(now)) {
            throw new ScheduleWindowClosedException(
                "Group creation window is not currently active"
            );
        }

        // 3. Check if student is already in a group
        if (groupMembershipRepository.existsByStudentId(studentUUID)) {
            throw new AlreadyInGroupException(
                "You are already a member of a group"
            );
        }

        // 4. Check if group name is unique in this term
        if (projectGroupRepository.existsByGroupNameAndTermId(groupName, termId)) {
            throw new DuplicateGroupNameException(
                String.format("A group named '%s' already exists for this term", groupName)
            );
        }

        // 5. Create new ProjectGroup
        ProjectGroup newGroup = new ProjectGroup();
        newGroup.setGroupName(groupName);
        newGroup.setStatus(GroupStatus.FORMING);
        newGroup.setTermId(termId);
        newGroup.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));

        ProjectGroup savedGroup = projectGroupRepository.save(newGroup);

        // 6. Fetch student entity
        Student student = studentRepository.findById(studentUUID)
            .orElseThrow(() -> new RuntimeException("Student not found"));

        // 7. Create GroupMembership with TEAM_LEADER role
        GroupMembership membership = new GroupMembership();
        membership.setStudent(student);
        membership.setGroup(savedGroup);
        membership.setRole(MemberRole.TEAM_LEADER);
        membership.setJoinedAt(LocalDateTime.now(ZoneId.of("UTC")));

        groupMembershipRepository.save(membership);

        // 8. Return GroupDetailResponse
        return toGroupDetailResponse(savedGroup, studentUUID);
    }

    @Transactional(readOnly = true)
    public GroupDetailResponse getMyGroup(UUID studentUUID) {
        // Find group membership for this student
        GroupMembership membership = groupMembershipRepository.findByStudentId(studentUUID)
            .orElseThrow(() -> new NotInGroupException(
                "You are not a member of any group"
            ));

        // Get group details
        ProjectGroup group = projectGroupRepository.findById(membership.getGroup().getId())
            .orElseThrow(() -> new RuntimeException("Group not found"));

        return toGroupDetailResponse(group, studentUUID);
    }

    /**
     * Validates and binds a JIRA workspace to the group.
     *
     * <p>Sequence (DFD 2.4):
     * <ol>
     *   <li>Verify the requester holds the {@code TEAM_LEADER} role in this group.</li>
     *   <li>Fetch the group and enforce the DISBANDED freeze — throw 400 if disbanded.</li>
     *   <li>Call {@link JiraValidationService#validate} with a live HTTP request to JIRA.
     *       Nothing is persisted if this throws.</li>
     *   <li>Encrypt the API token via {@link EncryptionService#encrypt}.</li>
     *   <li>Determine the new {@link GroupStatus}:
     *       <ul>
     *         <li>GitHub already bound → {@code TOOLS_BOUND}</li>
     *         <li>GitHub not bound, group is {@code FORMING} → {@code TOOLS_PENDING}</li>
     *         <li>Otherwise keep current status (already {@code TOOLS_PENDING})</li>
     *       </ul>
     *   </li>
     *   <li>Persist group with JIRA fields set; return {@link BindToolResponse}.</li>
     * </ol>
     *
     * @param groupId        UUID of the group to bind
     * @param jiraSpaceUrl   the JIRA space base URL (e.g., {@code https://company.atlassian.net})
     * @param jiraProjectKey the JIRA project key (e.g., {@code SPM})
     * @param jiraApiToken   the plain-text JIRA API token — encrypted before storage
     * @param requesterUUID  internal UUID of the authenticated student
     * @return {@link BindToolResponse} with binding status and non-sensitive JIRA metadata
     * @throws ForbiddenException              if the requester is not the TEAM_LEADER
     * @throws GroupNotFoundException          if no group with {@code groupId} exists
     * @throws BusinessRuleException           if the group is DISBANDED
     * @throws JiraValidationException         if the JIRA live call fails (→ 422)
     */
    @Transactional
    public BindToolResponse bindJira(UUID groupId, String jiraSpaceUrl, String jiraProjectKey,
                                     String jiraApiToken, UUID requesterUUID) {
        // 1. Verify requester is TEAM_LEADER of this group
        GroupMembership membership = groupMembershipRepository
            .findByGroupIdAndStudentId(groupId, requesterUUID)
            .orElseThrow(() -> new ForbiddenException("Only the Team Leader can bind tool integrations"));

        if (membership.getRole() != MemberRole.TEAM_LEADER) {
            throw new ForbiddenException("Only the Team Leader can bind tool integrations");
        }

        // 2. Fetch group and enforce DISBANDED freeze
        ProjectGroup group = projectGroupRepository.findById(groupId)
            .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        if (group.getStatus() == GroupStatus.DISBANDED) {
            throw new BusinessRuleException("This group has been disbanded");
        }

        // 3. Live validation — nothing is stored until this call returns without throwing
        jiraValidationService.validate(jiraSpaceUrl, jiraProjectKey, jiraApiToken);

        // 4. Encrypt token before persistence (NFR-7: AES-256 at rest)
        String encryptedJiraToken = encryptionService.encrypt(jiraApiToken);

        // 5. Determine new status
        //    GitHub already bound → TOOLS_BOUND; otherwise keep or advance to TOOLS_PENDING
        boolean githubAlreadyBound = group.getEncryptedGithubPat() != null;
        GroupStatus newStatus = githubAlreadyBound
            ? GroupStatus.TOOLS_BOUND
            : (group.getStatus() == GroupStatus.FORMING ? GroupStatus.TOOLS_PENDING : group.getStatus());

        // 6. Persist group with JIRA fields
        group.setJiraSpaceUrl(jiraSpaceUrl);
        group.setJiraProjectKey(jiraProjectKey);
        group.setEncryptedJiraToken(encryptedJiraToken);
        group.setStatus(newStatus);
        ProjectGroup savedGroup = projectGroupRepository.save(group);

        // 7. Build response — encrypted token is never included
        BindToolResponse response = new BindToolResponse();
        response.setGroupId(savedGroup.getId());
        response.setStatus(savedGroup.getStatus().toString());
        response.setJiraSpaceUrl(savedGroup.getJiraSpaceUrl());
        response.setJiraProjectKey(savedGroup.getJiraProjectKey());
        response.setJiraBound(true);
        response.setGithubBound(savedGroup.getEncryptedGithubPat() != null);
        return response;
    }

    /**
     * Validates and binds a GitHub organization to the group.
     *
     * <p>Sequence (DFD 2.5):
     * <ol>
     *   <li>Verify the requester holds the {@code TEAM_LEADER} role in this group.</li>
     *   <li>Fetch the group and enforce the DISBANDED freeze — throw 400 if disbanded.</li>
     *   <li>Call {@link GitHubValidationService#validate} with two sequential live HTTP
     *       requests to the GitHub REST API (org existence + {@code repo} scope check).
     *       Nothing is persisted if this throws.</li>
     *   <li>Encrypt the PAT via {@link EncryptionService#encrypt}.</li>
     *   <li>Determine the new {@link GroupStatus}:
     *       <ul>
     *         <li>JIRA already bound → {@code TOOLS_BOUND}</li>
     *         <li>JIRA not bound → {@code TOOLS_PENDING}</li>
     *       </ul>
     *   </li>
     *   <li>Persist group with GitHub fields set; return {@link BindToolResponse}.</li>
     * </ol>
     *
     * @param groupId       UUID of the group to bind
     * @param githubOrgName the GitHub organization name
     * @param githubPat     the plain-text GitHub Personal Access Token — encrypted before storage
     * @param requesterUUID internal UUID of the authenticated student
     * @return {@link BindToolResponse} with binding status and non-sensitive GitHub metadata
     * @throws ForbiddenException              if the requester is not the TEAM_LEADER
     * @throws GroupNotFoundException          if no group with {@code groupId} exists
     * @throws BusinessRuleException           if the group is DISBANDED
     * @throws GitHubValidationException       if the GitHub live call fails (→ 422)
     */
    @Transactional
    public BindToolResponse bindGitHub(UUID groupId, String githubOrgName,
                                       String githubPat, UUID requesterUUID) {
        // 1. Verify requester is TEAM_LEADER of this group
        GroupMembership membership = groupMembershipRepository
            .findByGroupIdAndStudentId(groupId, requesterUUID)
            .orElseThrow(() -> new ForbiddenException("Only the Team Leader can bind tool integrations"));

        if (membership.getRole() != MemberRole.TEAM_LEADER) {
            throw new ForbiddenException("Only the Team Leader can bind tool integrations");
        }

        // 2. Fetch group and enforce DISBANDED freeze
        ProjectGroup group = projectGroupRepository.findById(groupId)
            .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        if (group.getStatus() == GroupStatus.DISBANDED) {
            throw new BusinessRuleException("This group has been disbanded");
        }

        // 3. Live validation — two sequential calls (org check + repo scope check)
        //    Nothing is stored if either call throws.
        gitHubValidationService.validate(githubOrgName, githubPat);

        // 4. Encrypt PAT before persistence (NFR-7: AES-256 at rest)
        String encryptedGithubPat = encryptionService.encrypt(githubPat);

        // 5. Determine new status
        //    JIRA already bound → TOOLS_BOUND; otherwise TOOLS_PENDING
        boolean jiraAlreadyBound = group.getEncryptedJiraToken() != null;
        GroupStatus newStatus = jiraAlreadyBound ? GroupStatus.TOOLS_BOUND : GroupStatus.TOOLS_PENDING;

        // 6. Persist group with GitHub fields
        group.setGithubOrgName(githubOrgName);
        group.setEncryptedGithubPat(encryptedGithubPat);
        group.setStatus(newStatus);
        ProjectGroup savedGroup = projectGroupRepository.save(group);

        // 7. Build response — encrypted PAT is never included
        BindToolResponse response = new BindToolResponse();
        response.setGroupId(savedGroup.getId());
        response.setStatus(savedGroup.getStatus().toString());
        response.setGithubOrgName(savedGroup.getGithubOrgName());
        response.setGithubBound(true);
        response.setJiraBound(savedGroup.getEncryptedJiraToken() != null);
        return response;
    }

    private GroupDetailResponse toGroupDetailResponse(ProjectGroup group, UUID studentUUID) {
        GroupDetailResponse response = new GroupDetailResponse();
        response.setId(group.getId());
        response.setGroupName(group.getGroupName());
        response.setTermId(group.getTermId());
        response.setStatus(group.getStatus().toString());
        response.setCreatedAt(group.getCreatedAt());
        response.setJiraSpaceUrl(group.getJiraSpaceUrl());
        response.setJiraProjectKey(group.getJiraProjectKey());
        response.setJiraBound(group.getEncryptedJiraToken() != null);
        response.setGithubOrgName(group.getGithubOrgName());
        response.setGithubBound(group.getEncryptedGithubPat() != null);

        // Get all members of this group
        List<GroupMembership> members = groupMembershipRepository.findByGroupId(group.getId());
        List<GroupDetailResponse.MemberResponse> memberResponses = members.stream()
            .map(m -> {
                GroupDetailResponse.MemberResponse mr = new GroupDetailResponse.MemberResponse();
                mr.setStudentId(m.getStudent().getStudentId());
                mr.setRole(m.getRole().toString());
                mr.setJoinedAt(m.getJoinedAt());
                return mr;
            })
            .collect(Collectors.toList());

        response.setMembers(memberResponses);
        return response;
    }

    // ========== COORDINATOR BYPASS METHODS ==========

    /**
     * Retrieves detailed information for a specific project group.
     * <p>
     * This coordinator method provides full access to group details without any access restrictions,
     * including all members, binding status, and configuration information.
     *
     * @param groupId the UUID of the group to retrieve
     * @return a {@link GroupDetailResponse} containing complete group information
     * @throws GroupNotFoundException if no group exists with the specified ID
     */
    @Transactional(readOnly = true)
    public GroupDetailResponse getGroupDetail(UUID groupId) {
        ProjectGroup group = projectGroupRepository.findById(groupId)
            .orElseThrow(() -> new GroupNotFoundException("Group not found"));
        return toGroupDetailResponse(group, null);
    }


    // New method implemented to satisfy PR #84 / DFD 2.6 requirements
    @Transactional(readOnly = true)
    public List<GroupSummaryResponse> getGroupSummariesForActiveTerm() {
        String termId = termConfigService.getActiveTermId();
        List<ProjectGroup> groups = projectGroupRepository.findByTermId(termId);
        return groups.stream()
            .map(this::toGroupSummaryResponse)
            .collect(Collectors.toList());
    }

    //  Helper method with safe long-to-int conversion
    private GroupSummaryResponse toGroupSummaryResponse(ProjectGroup group) {
        GroupSummaryResponse response = new GroupSummaryResponse();
        response.setId(group.getId());
        response.setGroupName(group.getGroupName());
        response.setTermId(group.getTermId());
        response.setStatus(group.getStatus().toString());
        // Safely cast the long result from the repository to an int
        response.setMemberCount(Math.toIntExact(groupMembershipRepository.countByGroupId(group.getId())));
        response.setJiraBound(group.getEncryptedJiraToken() != null);
        response.setGithubBound(group.getEncryptedGithubPat() != null);
        return response;
    }

    /**
     * Force-adds a student to a project group by coordinator override.
     * <p>
     * This coordinator method bypasses normal group formation rules and directly adds a student
     * to a group without requiring their invitation or consent. Performs comprehensive validation:
     * <ul>
     *   <li>Verifies group exists</li>
     *   <li>Verifies student exists by studentId</li>
     *   <li>Checks student is not already in another group</li>
     *   <li>Validates group has not reached maximum team size (current members + pending invitations)</li>
     *   <li>Creates membership atomically as MEMBER role</li>
     *   <li>Auto-denies all PENDING invitations for this student</li>
     * </ul>
     * <p>
     * All operations are executed within a single @Transactional block to ensure atomicity.
     *
     * @param groupId the UUID of the target group
     * @param studentId the student ID (as String) of the student to add
     * @return a {@link GroupDetailResponse} containing updated group information
     * @throws GroupNotFoundException if no group exists with the specified ID
     * @throws StudentNotFoundException if no student exists with the specified studentId
     * @throws BusinessRuleException if:
     *         - student is already a member of another group
     *         - group has reached maximum team size (currentMembers + pendingInvitations >= maxTeamSize)
     */
    @Transactional
    public GroupDetailResponse coordinatorAddStudent(UUID groupId, String studentId) {
        // 1. Find group
        ProjectGroup group = projectGroupRepository.findById(groupId)
            .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        // 1a. DISBANDED freeze — cannot add members to a disbanded group
        if (group.getStatus() == GroupStatus.DISBANDED) {
            throw new BusinessRuleException("Cannot add a student to a disbanded group");
        }

        // 2. Find student by studentId
        Student student = studentRepository.findByStudentId(studentId)
            .orElseThrow(() -> new StudentNotFoundException(String.format("Student '%s' not found", studentId)));

        // 3. Check if student is already in a group
        if (groupMembershipRepository.existsByStudentId(student.getId())) {
            throw new BusinessRuleException(String.format("Student '%s' is already a member of a group", studentId));
        }

        // 4. Check max team size (current members + pending outbound invitations)
        long currentMembers = groupMembershipRepository.countByGroupId(groupId);
        long pendingInvitations = groupInvitationRepository.countByGroupIdAndStatus(
            groupId, 
            com.senior.spm.entity.GroupInvitation.InvitationStatus.PENDING
        );
        long totalCount = currentMembers + pendingInvitations;
        
        int maxTeamSize = termConfigService.getMaxTeamSize();
        if (totalCount >= maxTeamSize) {
            throw new BusinessRuleException("Group has reached maximum team size");
        }

        // 5. Create membership (atomically in same transaction)
        GroupMembership membership = new GroupMembership();
        membership.setStudent(student);
        membership.setGroup(group);
        membership.setRole(MemberRole.MEMBER);
        membership.setJoinedAt(LocalDateTime.now(ZoneId.of("UTC")));
        groupMembershipRepository.save(membership);

        // 6. Auto-deny all PENDING invitations for this student
        groupInvitationRepository.autoDenyAllPendingByInviteeId(student.getId());

        return toGroupDetailResponse(group, null);
    }

    /**
     * Force-removes a student from a project group by coordinator override.
     * <p>
     * This coordinator method bypasses normal group formation rules and directly removes a student
     * from a group. Includes a critical security check to prevent accidental removal of team leadership:
     * <ul>
     *   <li>Verifies group exists</li>
     *   <li>Verifies student exists by studentId</li>
     *   <li>Finds the membership relationship</li>
     *   <li>Blocks removal if student holds TEAM_LEADER role (security protection)</li>
     *   <li>Deletes membership atomically</li>
     * </ul>
     * <p>
     * All operations are executed within a single @Transactional block to ensure atomicity.
     *
     * @param groupId the UUID of the group from which to remove the student
     * @param studentId the student ID (as String) of the student to remove
     * @return a {@link GroupDetailResponse} containing updated group information
     * @throws GroupNotFoundException if:
     *         - no group exists with the specified ID, or
     *         - student is not a member of the specified group
     * @throws StudentNotFoundException if no student exists with the specified studentId
     * @throws ForbiddenException if the student holds TEAM_LEADER role (leadership transfer required first)
     */
    @Transactional
    public GroupDetailResponse coordinatorRemoveStudent(UUID groupId, String studentId) {
        // 1. Find group
        ProjectGroup group = projectGroupRepository.findById(groupId)
            .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        // 2. Find student by studentId
        Student student = studentRepository.findByStudentId(studentId)
            .orElseThrow(() -> new StudentNotFoundException(String.format("Student '%s' not found", studentId)));

        // 3. Find membership
        GroupMembership membership = groupMembershipRepository.findByGroupIdAndStudentId(groupId, student.getId())
            .orElseThrow(() -> new GroupNotFoundException(String.format("Student '%s' is not a member of this group", studentId)));

        // 4. Block removal of TEAM_LEADER
        if (membership.getRole() == MemberRole.TEAM_LEADER) {
            throw new ForbiddenException("Cannot remove Team Leader; transfer leadership first");
        }

        // 5. Delete membership
        groupMembershipRepository.delete(membership);

        return toGroupDetailResponse(group, null);
    }

    /**
     * Disbands a project group with full cascade cleanup.
     * <p>
     * This coordinator method permanently closes a group and performs comprehensive cascade operations
     * to ensure referential consistency:
     * <ul>
     *   <li>Verifies group exists</li>
     *   <li>Prevents disbanding already-disbanded groups (idempotency check)</li>
     *   <li>Updates group status to DISBANDED</li>
     *   <li>Hard-deletes all GroupMembership rows to free students for reassignment</li>
     *   <li>Auto-denies all PENDING GroupInvitations outbound from this group (P2 requirement)</li>
     *   <li>Auto-rejects all PENDING AdvisorRequests for this group (P3 requirement, future amendment)</li>
     * </ul>
     * <p>
     * All operations are executed within a single @Transactional block to ensure atomicity.
     * If any step fails, the entire transaction rolls back to maintain consistency.
     * <p>
     * <strong>P3 Integration Note:</strong> This method is prepared for P3 integration as specified
     * in p3_issues.md line 111. The AdvisorRequest auto-reject step will be activated when P3 advisor
     * workflow is implemented.
     *
     * @param groupId the UUID of the group to disband
     * @return a {@link GroupDetailResponse} containing the disbanded group information
     * @throws GroupNotFoundException if no group exists with the specified ID
     * @throws BusinessRuleException if the group is already in DISBANDED status
     */
    @Transactional
    public GroupDetailResponse disbandGroup(UUID groupId) {
        // 1. Find group
        ProjectGroup group = projectGroupRepository.findById(groupId)
            .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        // 2. Check if already disbanded
        if (group.getStatus() == GroupStatus.DISBANDED) {
            throw new BusinessRuleException("Group is already disbanded");
        }

        // 3. Update group status to DISBANDED
        group.setStatus(GroupStatus.DISBANDED);
        projectGroupRepository.save(group);

        // 4. Hard-delete all membership rows to free students
        groupMembershipRepository.deleteByGroupId(groupId);

        // 5. Auto-deny all PENDING outbound invitations from this group (P2 requirement)
        groupInvitationRepository.autoDenyAllPendingByGroupId(groupId);

        // 6. Auto-reject all PENDING advisor requests for this group (P3 requirement)
        // Note: This is part of the P3 cleanup step per p3_issues.md line 111
        // When P3 is implemented, this will handle advisor request cascade cleanup
        advisorRequestRepository.bulkUpdateStatusByGroupId(
            RequestStatus.AUTO_REJECTED,
            groupId
        );

        return toGroupDetailResponse(group, null);
    }
}
