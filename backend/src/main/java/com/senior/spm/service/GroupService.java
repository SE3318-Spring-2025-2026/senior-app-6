package com.senior.spm.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.dto.GroupDetailResponse;
import com.senior.spm.entity.AdvisorRequest;
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

        // Verify window is still open (closesAt > now)
        if (window.getClosesAt().isBefore(LocalDateTime.now(ZoneId.of("UTC")))) {
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
            .orElseThrow(() -> new RuntimeException(
                "You are not a member of any group"
            ));

        // Get group details
        ProjectGroup group = projectGroupRepository.findById(membership.getGroup().getId())
            .orElseThrow(() -> new RuntimeException("Group not found"));

        return toGroupDetailResponse(group, studentUUID);
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
        response.setJiraBound(group.getJiraSpaceUrl() != null && group.getJiraProjectKey() != null);
        response.setGithubOrgName(group.getGithubOrgName());
        response.setGithubBound(group.getGithubOrgName() != null);

        // Get all members of this group
        List<GroupMembership> members = groupMembershipRepository.findByGroupId(group.getId());
        List<GroupDetailResponse.MemberResponse> memberResponses = members.stream()
            .map(m -> {
                GroupDetailResponse.MemberResponse mr = new GroupDetailResponse.MemberResponse();
                mr.setStudentId(m.getStudent().getId());
                mr.setRole(m.getRole().toString());
                mr.setJoinedAt(m.getJoinedAt());
                return mr;
            })
            .collect(Collectors.toList());

        response.setMembers(memberResponses);
        return response;
    }

    // ========== COORDINATOR BYPASS METHODS ==========

    @Transactional(readOnly = true)
    public List<GroupDetailResponse> getGroupsForActiveTerm() {
        String termId = termConfigService.getActiveTermId();
        List<ProjectGroup> groups = projectGroupRepository.findByTermId(termId);
        return groups.stream()
            .map(group -> toGroupDetailResponse(group, null))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GroupDetailResponse getGroupDetail(UUID groupId) {
        ProjectGroup group = projectGroupRepository.findById(groupId)
            .orElseThrow(() -> new GroupNotFoundException("Group not found"));
        return toGroupDetailResponse(group, null);
    }

    @Transactional
    public GroupDetailResponse coordinatorAddStudent(UUID groupId, String studentId) {
        // 1. Find group
        ProjectGroup group = projectGroupRepository.findById(groupId)
            .orElseThrow(() -> new GroupNotFoundException("Group not found"));

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

        // 6. Auto-deny all PENDING invitations for this student (excluding this group if any existed)
        groupInvitationRepository.autoDenyOtherPendingInvitations(student.getId(), groupId);

        return toGroupDetailResponse(group, null);
    }

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
