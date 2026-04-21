package com.senior.spm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.Student;

/**
 * Verifies GroupMembership repository + DB-level unique constraints.
 *
 * Key constraints under test:
 *   uq_gm_group_student (group_id, student_id) — no duplicate row for same pair
 *   uq_gm_student (student_id)               — a student can only be in ONE group
 */
@DataJpaTest
@TestPropertySource(properties = "spring.sql.init.mode=never")
class GroupMembershipRepositoryTest extends RepositoryTestBase {

    @Autowired
    GroupMembershipRepository repo;

    // ── uq_gm_student: student can only be in one group ──────────────────────
    // Uses repo.saveAndFlush() so Spring Data translates PersistenceException → DataIntegrityViolationException

    @Test
    void uq_gm_student_preventsStudentInTwoGroups() {
        Student student = makeStudent("23070000001");
        ProjectGroup g1 = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        ProjectGroup g2 = makeGroup("Group B", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        GroupMembership first = new GroupMembership();
        first.setGroup(g1); first.setStudent(student);
        first.setRole(GroupMembership.MemberRole.TEAM_LEADER); first.setJoinedAt(LocalDateTime.now());
        repo.saveAndFlush(first);

        assertThatThrownBy(() -> {
            GroupMembership second = new GroupMembership();
            second.setGroup(g2); second.setStudent(student);
            second.setRole(GroupMembership.MemberRole.MEMBER); second.setJoinedAt(LocalDateTime.now());
            repo.saveAndFlush(second);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── uq_gm_group_student: no duplicate (group, student) row ───────────────

    @Test
    void uq_gm_group_student_preventsDuplicateMembershipSameGroupSameStudent() {
        Student student = makeStudent("23070000002");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        GroupMembership first = new GroupMembership();
        first.setGroup(group); first.setStudent(student);
        first.setRole(GroupMembership.MemberRole.TEAM_LEADER); first.setJoinedAt(LocalDateTime.now());
        repo.saveAndFlush(first);

        assertThatThrownBy(() -> {
            GroupMembership second = new GroupMembership();
            second.setGroup(group); second.setStudent(student);
            second.setRole(GroupMembership.MemberRole.MEMBER); second.setJoinedAt(LocalDateTime.now());
            repo.saveAndFlush(second);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── Two different students can be in the same group ───────────────────────

    @Test
    void twoDifferentStudents_canJoinSameGroup() {
        Student s1 = makeStudent("23070000003");
        Student s2 = makeStudent("23070000004");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        makeMembership(group, s1, GroupMembership.MemberRole.TEAM_LEADER);
        makeMembership(group, s2, GroupMembership.MemberRole.MEMBER);

        assertThat(repo.countByGroupId(group.getId())).isEqualTo(2);
    }

    // ── existsByStudentId ─────────────────────────────────────────────────────

    @Test
    void existsByStudentId_trueWhenMemberExists() {
        Student student = makeStudent("23070000005");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        makeMembership(group, student, GroupMembership.MemberRole.TEAM_LEADER);

        assertThat(repo.existsByStudentId(student.getId())).isTrue();
    }

    @Test
    void existsByStudentId_falseWhenNoMembership() {
        Student student = makeStudent("23070000006");

        assertThat(repo.existsByStudentId(student.getId())).isFalse();
    }

    // ── findByGroupId ─────────────────────────────────────────────────────────

    @Test
    void findByGroupId_returnsAllMembersOfGroup() {
        Student s1 = makeStudent("23070000007");
        Student s2 = makeStudent("23070000008");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        ProjectGroup other = makeGroup("Group B", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        makeMembership(group, s1, GroupMembership.MemberRole.TEAM_LEADER);
        makeMembership(group, s2, GroupMembership.MemberRole.MEMBER);
        makeStudent("23070000009"); // student in other group
        makeMembership(other, makeStudent("23070000010"), GroupMembership.MemberRole.TEAM_LEADER);

        assertThat(repo.findByGroupId(group.getId())).hasSize(2);
    }

    // ── countByGroupId ────────────────────────────────────────────────────────

    @Test
    void countByGroupId_returnsCorrectMemberCount() {
        Student s1 = makeStudent("23070000011");
        Student s2 = makeStudent("23070000012");
        Student s3 = makeStudent("23070000013");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        makeMembership(group, s1, GroupMembership.MemberRole.TEAM_LEADER);
        makeMembership(group, s2, GroupMembership.MemberRole.MEMBER);
        makeMembership(group, s3, GroupMembership.MemberRole.MEMBER);

        assertThat(repo.countByGroupId(group.getId())).isEqualTo(3);
    }

    // ── deleteByGroupId ───────────────────────────────────────────────────────

    @Test
    void deleteByGroupId_removesAllMembershipsForGroup() {
        Student s1 = makeStudent("23070000014");
        Student s2 = makeStudent("23070000015");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        makeMembership(group, s1, GroupMembership.MemberRole.TEAM_LEADER);
        makeMembership(group, s2, GroupMembership.MemberRole.MEMBER);

        repo.deleteByGroupId(group.getId());
        clearCache();

        assertThat(repo.countByGroupId(group.getId())).isEqualTo(0);
        // students themselves should still exist
        assertThat(em.find(com.senior.spm.entity.Student.class, s1.getId())).isNotNull();
    }

    // ── findByGroupIdAndRole ──────────────────────────────────────────────────

    @Test
    void findByGroupIdAndRole_returnsTeamLeader() {
        Student leader = makeStudent("23070000016");
        Student member = makeStudent("23070000017");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        makeMembership(group, leader, GroupMembership.MemberRole.TEAM_LEADER);
        makeMembership(group, member, GroupMembership.MemberRole.MEMBER);

        var result = repo.findByGroupIdAndRole(group.getId(), GroupMembership.MemberRole.TEAM_LEADER);
        assertThat(result).isPresent();
        assertThat(result.get().getStudent().getId()).isEqualTo(leader.getId());
    }

    // ── findByGroupIdAndStudentId ─────────────────────────────────────────────

    @Test
    void findByGroupIdAndStudentId_returnsCorrectMembership() {
        Student student = makeStudent("23070000018");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        makeMembership(group, student, GroupMembership.MemberRole.TEAM_LEADER);

        var result = repo.findByGroupIdAndStudentId(group.getId(), student.getId());
        assertThat(result).isPresent();
    }
}
