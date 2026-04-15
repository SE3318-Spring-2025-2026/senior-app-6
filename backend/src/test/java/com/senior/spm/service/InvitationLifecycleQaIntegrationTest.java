package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.senior.spm.entity.GroupInvitation;
import com.senior.spm.entity.GroupInvitation.InvitationStatus;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.Student;
import com.senior.spm.entity.SystemConfig;
import com.senior.spm.repository.GroupInvitationRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.repository.SystemConfigRepository;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
class InvitationLifecycleQaIntegrationTest {

    private static final String TERM_ID = "2026-SPRING";

    @Autowired private MockMvc mockMvc;
    @Autowired private GroupInvitationRepository groupInvitationRepository;
    @Autowired private GroupMembershipRepository groupMembershipRepository;
    @Autowired private ProjectGroupRepository projectGroupRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private SystemConfigRepository systemConfigRepository;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        seedConfig("active_term_id", TERM_ID);
        seedConfig("max_team_size", "5");
    }

    @AfterEach
    void cleanUp() {
        cleanDatabase();
    }

    @Test
    @DisplayName("QA #47: Team leader can dispatch invitation; non-leader gets 403")
    void sendInvitation_teamLeaderAuthorizationAndSuccessfulDispatch() throws Exception {
        Student leader = createStudent("23070010001", "leader-auth");
        Student member = createStudent("23070010002", "member-auth");
        Student target = createStudent("23070010003", "target-auth");
        ProjectGroup group = createGroup("QA Dispatch Group", GroupStatus.FORMING);
        createMembership(group, leader, MemberRole.TEAM_LEADER);
        createMembership(group, member, MemberRole.MEMBER);

        mockMvc.perform(post("/api/groups/{groupId}/invitations", group.getId())
                .with(authentication(studentAuth(member)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStudentId\":\"" + target.getStudentId() + "\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/groups/{groupId}/invitations", group.getId())
                .with(authentication(studentAuth(leader)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStudentId\":\"" + target.getStudentId() + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.targetStudentId").value(target.getStudentId()))
            .andExpect(jsonPath("$.status").value("PENDING"));

        List<GroupInvitation> invitations = groupInvitationRepository.findByInviteeId(target.getId());
        assertThat(invitations).hasSize(1);
        assertThat(invitations.get(0).getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    @DisplayName("QA #47: Accept creates membership and auto-denies competing invitations in DB")
    void acceptInvitation_createsMembershipAndAutoDeniesCompetingInvitations() throws Exception {
        Student invitee = createStudent("23070011001", "invitee-accept");
        ProjectGroup groupA = createGroupWithLeader("QA Accept Group A", "23070011002");
        ProjectGroup groupB = createGroupWithLeader("QA Accept Group B", "23070011003");
        GroupInvitation acceptedInvitation = createPendingInvitation(groupA, invitee);
        GroupInvitation competingInvitation = createPendingInvitation(groupB, invitee);

        mockMvc.perform(patch("/api/invitations/{invitationId}/respond", acceptedInvitation.getId())
                .with(authentication(studentAuth(invitee)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(groupA.getId().toString()));

        GroupInvitation accepted = groupInvitationRepository.findById(acceptedInvitation.getId()).orElseThrow();
        GroupInvitation autoDenied = groupInvitationRepository.findById(competingInvitation.getId()).orElseThrow();

        assertThat(accepted.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(autoDenied.getStatus()).isEqualTo(InvitationStatus.AUTO_DENIED);
        assertThat(groupMembershipRepository.findByGroupIdAndStudentId(groupA.getId(), invitee.getId()))
            .isPresent()
            .get()
            .extracting(GroupMembership::getRole)
            .isEqualTo(MemberRole.MEMBER);
    }

    @Test
    @DisplayName("QA #47: DISBANDED group invitation attempt returns 400")
    void sendInvitation_disbandedGroup_returns400() throws Exception {
        Student leader = createStudent("23070012001", "leader-disbanded");
        Student target = createStudent("23070012002", "target-disbanded");
        ProjectGroup group = createGroup("QA Disbanded Group", GroupStatus.DISBANDED);
        createMembership(group, leader, MemberRole.TEAM_LEADER);

        mockMvc.perform(post("/api/groups/{groupId}/invitations", group.getId())
                .with(authentication(studentAuth(leader)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStudentId\":\"" + target.getStudentId() + "\"}"))
            .andExpect(status().isBadRequest());

        assertThat(groupInvitationRepository.findByInviteeId(target.getId())).isEmpty();
    }

    @Test
    @DisplayName("QA #47: Accept on TOOLS_BOUND group returns 400 and leaves invitation pending")
    void acceptInvitation_toolsBoundGroup_returns400() throws Exception {
        Student invitee = createStudent("23070013001", "invitee-tools-bound");
        ProjectGroup group = createGroupWithLeader("QA Tools Bound Group", "23070013002");
        group.setStatus(GroupStatus.TOOLS_BOUND);
        group = projectGroupRepository.save(group);
        GroupInvitation invitation = createPendingInvitation(group, invitee);

        mockMvc.perform(patch("/api/invitations/{invitationId}/respond", invitation.getId())
                .with(authentication(studentAuth(invitee)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":true}"))
            .andExpect(status().isBadRequest());

        assertThat(groupInvitationRepository.findById(invitation.getId()).orElseThrow().getStatus())
            .isEqualTo(InvitationStatus.PENDING);
        assertThat(groupMembershipRepository.findByGroupIdAndStudentId(group.getId(), invitee.getId()))
            .isEmpty();
    }

    @Test
    @DisplayName("QA #47: Over-capacity invitation attempt returns 400")
    void sendInvitation_membersPlusPendingAtLimit_returns400() throws Exception {
        Student leader = createStudent("23070014001", "leader-capacity");
        Student pendingInvitee = createStudent("23070014002", "pending-capacity");
        Student target = createStudent("23070014003", "target-capacity");
        ProjectGroup group = createGroup("QA Capacity Group", GroupStatus.FORMING);
        createMembership(group, leader, MemberRole.TEAM_LEADER);
        createMembership(group, createStudent("23070014004", "member-capacity-1"), MemberRole.MEMBER);
        createMembership(group, createStudent("23070014005", "member-capacity-2"), MemberRole.MEMBER);
        createMembership(group, createStudent("23070014006", "member-capacity-3"), MemberRole.MEMBER);
        createPendingInvitation(group, pendingInvitee);

        mockMvc.perform(post("/api/groups/{groupId}/invitations", group.getId())
                .with(authentication(studentAuth(leader)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStudentId\":\"" + target.getStudentId() + "\"}"))
            .andExpect(status().isBadRequest());

        assertThat(groupInvitationRepository.findByInviteeId(target.getId())).isEmpty();
    }

    private void cleanDatabase() {
        groupInvitationRepository.deleteAll();
        groupMembershipRepository.deleteAll();
        projectGroupRepository.deleteAll();
        studentRepository.deleteAll();
        systemConfigRepository.deleteAll();
    }

    private void seedConfig(String key, String value) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        systemConfigRepository.save(config);
    }

    private ProjectGroup createGroupWithLeader(String groupName, String leaderStudentId) {
        ProjectGroup group = createGroup(groupName, GroupStatus.FORMING);
        createMembership(group, createStudent(leaderStudentId, "leader-" + UUID.randomUUID()), MemberRole.TEAM_LEADER);
        return group;
    }

    private ProjectGroup createGroup(String name, GroupStatus status) {
        ProjectGroup group = new ProjectGroup();
        group.setGroupName(name);
        group.setStatus(status);
        group.setTermId(TERM_ID);
        group.setCreatedAt(LocalDateTime.now());
        group.setVersion(0L);
        return projectGroupRepository.save(group);
    }

    private Student createStudent(String studentId, String githubUsername) {
        Student student = new Student();
        student.setStudentId(studentId);
        student.setGithubUsername(githubUsername);
        return studentRepository.save(student);
    }

    private GroupMembership createMembership(ProjectGroup group, Student student, MemberRole role) {
        GroupMembership membership = new GroupMembership();
        membership.setGroup(group);
        membership.setStudent(student);
        membership.setRole(role);
        membership.setJoinedAt(LocalDateTime.now());
        return groupMembershipRepository.save(membership);
    }

    private GroupInvitation createPendingInvitation(ProjectGroup group, Student invitee) {
        GroupInvitation invitation = new GroupInvitation();
        invitation.setGroup(group);
        invitation.setInvitee(invitee);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setSentAt(LocalDateTime.now());
        return groupInvitationRepository.save(invitation);
    }

    private Authentication studentAuth(Student student) {
        return new UsernamePasswordAuthenticationToken(
            student.getId().toString(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }
}
