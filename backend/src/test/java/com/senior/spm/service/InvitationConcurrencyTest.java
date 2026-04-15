package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

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

/**
 * Concurrency test for the invitation accept flow.
 *
 * Verifies that when two threads simultaneously attempt to accept different
 * invitations for the same student, the DB-level unique constraint
 * (uq_gm_student on student_id) guarantees exactly one GroupMembership row.
 *
 * NOTE: This class intentionally has NO @Transactional — each thread must run
 * its own transaction boundary. Manual cleanup is done in @AfterEach.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:test.properties")
class InvitationConcurrencyTest {

    @Autowired private InvitationService invitationService;
    @Autowired private GroupInvitationRepository groupInvitationRepository;
    @Autowired private GroupMembershipRepository groupMembershipRepository;
    @Autowired private ProjectGroupRepository projectGroupRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private SystemConfigRepository systemConfigRepository;

    private static final String TERM_ID = "2026-SPRING";

    private Student invitee;
    private GroupInvitation invA;
    private GroupInvitation invB;

    @BeforeEach
    void setUp() {
        // Clean up first (child tables before parents)
        groupInvitationRepository.deleteAll();
        groupMembershipRepository.deleteAll();
        projectGroupRepository.deleteAll();
        studentRepository.deleteAll();
        systemConfigRepository.deleteAll();

        // Seed term config
        seedConfig("active_term_id", TERM_ID);
        seedConfig("max_team_size", "5");

        // One student who will try to accept two invitations simultaneously
        invitee = createStudent("23070099001", "invitee-concurrent");

        // Two groups, each with a team leader
        ProjectGroup groupA = createGroup("Concurrent Group A");
        ProjectGroup groupB = createGroup("Concurrent Group B");
        createLeader(groupA);
        createLeader(groupB);

        // Two pending invitations for the same student
        invA = createPendingInvitation(groupA, invitee);
        invB = createPendingInvitation(groupB, invitee);
    }

    @AfterEach
    void cleanUp() {
        groupInvitationRepository.deleteAll();
        groupMembershipRepository.deleteAll();
        projectGroupRepository.deleteAll();
        studentRepository.deleteAll();
        systemConfigRepository.deleteAll();
    }

    @Test
    @DisplayName("Concurrent accept of two invitations for the same student creates exactly one membership row")
    void concurrentAccept_onlyOneMembershipCreated() throws Exception {
        UUID inviteeId = invitee.getId();
        UUID invAId = invA.getId();
        UUID invBId = invB.getId();

        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<AcceptAttempt> futureA = executor.submit(() -> {
            try {
                gate.await();
                invitationService.respondToInvitation(invAId, inviteeId, true);
                return AcceptAttempt.success();
            } catch (Exception e) {
                return AcceptAttempt.failure(e);
            }
        });

        Future<AcceptAttempt> futureB = executor.submit(() -> {
            try {
                gate.await();
                invitationService.respondToInvitation(invBId, inviteeId, true);
                return AcceptAttempt.success();
            } catch (Exception e) {
                return AcceptAttempt.failure(e);
            }
        });

        // Release both threads simultaneously
        gate.countDown();

        AcceptAttempt resultA = futureA.get(10, TimeUnit.SECONDS);
        AcceptAttempt resultB = futureB.get(10, TimeUnit.SECONDS);

        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS))
                .as("Executor should shut down cleanly after both accept attempts finish")
                .isTrue();

        // Exactly one must succeed and one must fail
        assertThat(resultA.succeeded() ^ resultB.succeeded())
                .as("Exactly one of the two concurrent accepts must succeed")
                .isTrue();

        List<Throwable> failures = new ArrayList<>();
        if (!resultA.succeeded()) {
            failures.add(resultA.failure());
        }
        if (!resultB.succeeded()) {
            failures.add(resultB.failure());
        }
        assertThat(failures)
                .as("The losing concurrent accept must fail instead of being silently ignored")
                .hasSize(1)
                .allSatisfy(error -> assertThat(error).isNotNull());

        List<GroupMembership> memberships = groupMembershipRepository.findAll()
                .stream()
                .filter(m -> m.getStudent().getId().equals(inviteeId))
                .toList();

        assertThat(memberships)
                .as("Student must have exactly one GroupMembership row regardless of race outcome")
                .hasSize(1);

        List<InvitationStatus> finalStatuses = groupInvitationRepository.findByInviteeId(inviteeId)
                .stream()
                .map(GroupInvitation::getStatus)
                .toList();

        assertThat(finalStatuses)
                .as("Concurrent accept must leave one accepted invite and auto-deny the competing invite")
                .containsExactlyInAnyOrder(InvitationStatus.ACCEPTED, InvitationStatus.AUTO_DENIED);
    }

    // ── Seed helpers ─────────────────────────────────────────────────────────────

    private record AcceptAttempt(boolean succeeded, Throwable failure) {
        static AcceptAttempt success() {
            return new AcceptAttempt(true, null);
        }

        static AcceptAttempt failure(Throwable failure) {
            return new AcceptAttempt(false, failure);
        }
    }

    private void seedConfig(String key, String value) {
        SystemConfig cfg = new SystemConfig();
        cfg.setConfigKey(key);
        cfg.setConfigValue(value);
        systemConfigRepository.save(cfg);
    }

    private Student createStudent(String studentId, String github) {
        Student s = new Student();
        s.setStudentId(studentId);
        s.setGithubUsername(github);
        return studentRepository.save(s);
    }

    private ProjectGroup createGroup(String name) {
        ProjectGroup g = new ProjectGroup();
        g.setGroupName(name);
        g.setStatus(GroupStatus.FORMING);
        g.setTermId(TERM_ID);
        g.setCreatedAt(LocalDateTime.now());
        g.setVersion(0L);
        return projectGroupRepository.save(g);
    }

    private void createLeader(ProjectGroup group) {
        String idStr = String.format("%011d",
                Math.abs(UUID.randomUUID().getMostSignificantBits()) % 100_000_000_000L);
        Student leader = createStudent(idStr, "leader-" + UUID.randomUUID());
        GroupMembership m = new GroupMembership();
        m.setGroup(group);
        m.setStudent(leader);
        m.setRole(MemberRole.TEAM_LEADER);
        m.setJoinedAt(LocalDateTime.now());
        groupMembershipRepository.save(m);
    }

    private GroupInvitation createPendingInvitation(ProjectGroup group, Student student) {
        GroupInvitation inv = new GroupInvitation();
        inv.setGroup(group);
        inv.setInvitee(student);
        inv.setStatus(InvitationStatus.PENDING);
        inv.setSentAt(LocalDateTime.now());
        return groupInvitationRepository.save(inv);
    }
}
