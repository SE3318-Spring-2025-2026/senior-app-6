package com.senior.spm.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.dto.GroupDetailResponse;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.entity.Student;
import com.senior.spm.exception.AlreadyInGroupException;
import com.senior.spm.exception.DuplicateGroupNameException;
import com.senior.spm.exception.ScheduleWindowClosedException;
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
}
