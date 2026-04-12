package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.entity.GroupInvitation;
import com.senior.spm.entity.GroupInvitation.InvitationStatus;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.Student;
import com.senior.spm.entity.SystemConfig;
import com.senior.spm.exception.AlreadyInGroupException;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.repository.GroupInvitationRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.repository.SystemConfigRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@TestPropertySource(locations = "classpath:test.properties")
@Transactional
class InvitationServicePersistenceTest {

    @Autowired private InvitationService invitationService;
    @Autowired private GroupInvitationRepository groupInvitationRepository;
    @Autowired private GroupMembershipRepository groupMembershipRepository;
    @Autowired private ProjectGroupRepository projectGroupRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private SystemConfigRepository systemConfigRepository;
    @Autowired private EntityManager entityManager;

    private Student student;
    private ProjectGroup groupA;
    private ProjectGroup groupB;
    private ProjectGroup groupC;
    private final String activeTerm = "2026-SPRING";

    @BeforeEach
    void setUp() {
        // Clear repositories to ensure isolation (child first)
        groupInvitationRepository.deleteAll();
        groupMembershipRepository.deleteAll();
        projectGroupRepository.deleteAll();
        studentRepository.deleteAll();
        systemConfigRepository.deleteAll();

        // Seed System Config
        seedConfig("active_term_id", activeTerm);
        seedConfig("max_team_size", "5");

        // Setup Student
        student = createStudent("23070006001", "student-1");

        // Setup 3 Groups
        groupA = createGroup("Group A");
        groupB = createGroup("Group B");
        groupC = createGroup("Group C");

        // Set up Team Leaders for each group so they are valid
        createMembership(groupA, MemberRole.TEAM_LEADER);
        createMembership(groupB, MemberRole.TEAM_LEADER);
        createMembership(groupC, MemberRole.TEAM_LEADER);
        
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Acceptance must atomically auto-deny all other pending invitations for that student")
    void respondToInvitation_accept_performsAtomicAutoDenyInDB() {
        // Reload student and groups in current context
        student = studentRepository.findById(student.getId()).orElseThrow();
        groupA = projectGroupRepository.findById(groupA.getId()).orElseThrow();
        groupB = projectGroupRepository.findById(groupB.getId()).orElseThrow();
        groupC = projectGroupRepository.findById(groupC.getId()).orElseThrow();

        // 1. Create 3 pending invitations for the same student
        GroupInvitation invA = createInvitation(groupA, student);
        GroupInvitation invB = createInvitation(groupB, student);
        GroupInvitation invC = createInvitation(groupC, student);

        // 2. Student accepts Group A
        invitationService.respondToInvitation(invA.getId(), student.getId(), true);

        // 3. Verify in DB
        entityManager.flush();
        entityManager.clear();

        GroupInvitation updatedA = groupInvitationRepository.findById(invA.getId()).orElseThrow();
        GroupInvitation updatedB = groupInvitationRepository.findById(invB.getId()).orElseThrow();
        GroupInvitation updatedC = groupInvitationRepository.findById(invC.getId()).orElseThrow();

        assertThat(updatedA.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(updatedB.getStatus()).isEqualTo(InvitationStatus.AUTO_DENIED);
        assertThat(updatedC.getStatus()).isEqualTo(InvitationStatus.AUTO_DENIED);
        
        // 4. Verify Membership
        assertThat(groupMembershipRepository.findByGroupIdAndStudentId(groupA.getId(), student.getId()).isPresent()).isTrue();
    }

    @Test
    @DisplayName("Decline must be isolated and not affect other pending invitations")
    void respondToInvitation_decline_isIsolatedInDB() {
        student = studentRepository.findById(student.getId()).orElseThrow();
        groupA = projectGroupRepository.findById(groupA.getId()).orElseThrow();
        groupB = projectGroupRepository.findById(groupB.getId()).orElseThrow();

        GroupInvitation invA = createInvitation(groupA, student);
        GroupInvitation invB = createInvitation(groupB, student);

        // Student declines A
        invitationService.respondToInvitation(invA.getId(), student.getId(), false);

        entityManager.flush();
        entityManager.clear();

        assertThat(groupInvitationRepository.findById(invA.getId()).orElseThrow().getStatus())
            .isEqualTo(InvitationStatus.DECLINED);
        assertThat(groupInvitationRepository.findById(invB.getId()).orElseThrow().getStatus())
            .isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    @DisplayName("Accept must fail and not create membership if the group reached capacity via external change")
    void respondToInvitation_accept_rechecksCapacityAgainstRealDB() {
        student = studentRepository.findById(student.getId()).orElseThrow();
        groupA = projectGroupRepository.findById(groupA.getId()).orElseThrow();

        GroupInvitation inv = createInvitation(groupA, student);
        
        // Setup: Group A is at 4 members. Max is 5.
        // setUp() already created 1 Leader. We add 4 more members to hit 5.
        for (int i = 0; i < 4; i++) {
            createMembership(groupA, MemberRole.MEMBER);
        }
        
        entityManager.flush();
        entityManager.clear();

        // Verify current members = 5
        assertThat(groupMembershipRepository.countByGroupId(groupA.getId())).isEqualTo(5L);

        // Try to accept
        assertThatThrownBy(() -> invitationService.respondToInvitation(inv.getId(), student.getId(), true))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("maximum team size");

        // Verify no membership was created for the invitee
        assertThat(groupMembershipRepository.findByGroupIdAndStudentId(groupA.getId(), student.getId()).isPresent()).isFalse();
    }

    @Test
    @DisplayName("Acceptance must set respondedAt timestamp in DB")
    void respondToInvitation_accept_setsRespondedAtInDB() {
        student = studentRepository.findById(student.getId()).orElseThrow();
        groupA = projectGroupRepository.findById(groupA.getId()).orElseThrow();
        GroupInvitation inv = createInvitation(groupA, student);
        
        invitationService.respondToInvitation(inv.getId(), student.getId(), true);

        entityManager.flush();
        entityManager.clear();

        GroupInvitation saved = groupInvitationRepository.findById(inv.getId()).orElseThrow();
        assertThat(saved.getRespondedAt()).isNotNull();
    }

    @Test
    @DisplayName("Bulk Auto-Deny: Mark all pending invitations for a group as auto-denied")
    void autoDenyAllPendingByGroupId_worksInDB() {
        groupA = projectGroupRepository.findById(groupA.getId()).orElseThrow();
        student = studentRepository.findById(student.getId()).orElseThrow();

        // Create 2 students with pending invites from Group A
        Student s2 = createStudent("23070006002", "student-2");
        createInvitation(groupA, student);
        createInvitation(groupA, s2);

        entityManager.flush();

        // Act
        groupInvitationRepository.autoDenyAllPendingByGroupId(groupA.getId());

        // Assert
        entityManager.clear();
        List<GroupInvitation> invites = groupInvitationRepository.findByGroupId(groupA.getId());
        assertThat(invites).allMatch(i -> i.getStatus() == InvitationStatus.AUTO_DENIED);
    }

    @Test
    @DisplayName("Pending inbox: Response must include status field")
    void getPendingInvitations_returnsStatusField() {
        student = studentRepository.findById(student.getId()).orElseThrow();
        groupA = projectGroupRepository.findById(groupA.getId()).orElseThrow();
        createInvitation(groupA, student);

        entityManager.flush();
        entityManager.clear();

        List<com.senior.spm.controller.dto.InvitationResponse> results =
            invitationService.getPendingInvitations(student.getId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Send Guard: Team Leader cannot invite themselves")
    void sendInvitation_selfInvite_throwsException() {
        groupA = projectGroupRepository.findById(groupA.getId()).orElseThrow();
        Student leader = createStudent("23070006099", "leader-1");
        createMembership(groupA, leader, MemberRole.TEAM_LEADER);

        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> invitationService.sendInvitation(groupA.getId(), leader.getId(), leader.getStudentId()))
            .isInstanceOf(AlreadyInGroupException.class); // Matches implementation in sendInvitation
    }

    private void seedConfig(String key, String value) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        systemConfigRepository.save(config);
    }

    private Student createStudent(String studentId, String github) {
        Student s = new Student();
        s.setStudentId(studentId);
        s.setGithubUsername(github);
        return studentRepository.save(s);
    }

    private ProjectGroup createGroup(String name) {
        ProjectGroup group = new ProjectGroup();
        group.setGroupName(name);
        group.setStatus(GroupStatus.FORMING);
        group.setTermId(activeTerm);
        group.setCreatedAt(LocalDateTime.now());
        group.setVersion(0L);
        return projectGroupRepository.save(group);
    }

    private void createMembership(ProjectGroup group, MemberRole role) {
        Student s = new Student();
        String studentIdStr = String.format("%011d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 100000000000L);
        s.setStudentId(studentIdStr);
        s.setGithubUsername("user-" + UUID.randomUUID());
        s = studentRepository.save(s);
        createMembership(group, s, role);
    }

    private void createMembership(ProjectGroup group, Student s, MemberRole role) {
        GroupMembership m = new GroupMembership();
        m.setGroup(group);
        m.setStudent(s);
        m.setRole(role);
        m.setJoinedAt(LocalDateTime.now());
        groupMembershipRepository.save(m);
    }

    private GroupInvitation createInvitation(ProjectGroup group, Student invitee) {
        GroupInvitation inv = new GroupInvitation();
        inv.setGroup(group);
        inv.setInvitee(invitee);
        inv.setStatus(InvitationStatus.PENDING);
        inv.setSentAt(LocalDateTime.now());
        return groupInvitationRepository.save(inv);
    }
}
