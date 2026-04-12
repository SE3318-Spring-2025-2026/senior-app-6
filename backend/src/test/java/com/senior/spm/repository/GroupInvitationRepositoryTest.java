package com.senior.spm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import com.senior.spm.entity.GroupInvitation;
import com.senior.spm.entity.GroupInvitation.InvitationStatus;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.Student;

/**
 * Verifies GroupInvitationRepository query methods.
 *
 * Critical methods under test:
 *   countByGroupIdAndStatus           — used for max-team-size check (seq 2.2, 2.3)
 *   autoDenyOtherPendingInvitationsExcept — on accept: deny all other pending invitations (seq 2.3)
 *   autoDenyAllPendingByInviteeId     — on coordinator add: deny all pending invitations for the student
 *   autoDenyAllPendingByGroupId       — on disband: deny all outbound pending invites (seq 2.6)
 */
@DataJpaTest
@TestPropertySource(properties = "spring.sql.init.mode=never")
class GroupInvitationRepositoryTest extends RepositoryTestBase {

    @Autowired
    GroupInvitationRepository repo;

    // ── countByGroupIdAndStatus ────────────────────────────────────────────────
    // Max-team-size check: count = current members + pending outbound invitations

    @Test
    void countByGroupIdAndStatus_countsPendingInvitationsOnly() {
        Student s1 = makeStudent("23070000101");
        Student s2 = makeStudent("23070000102");
        Student s3 = makeStudent("23070000103");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        makeInvitation(group, s1, InvitationStatus.PENDING);
        makeInvitation(group, s2, InvitationStatus.PENDING);
        makeInvitation(group, s3, InvitationStatus.DECLINED); // not PENDING

        long count = repo.countByGroupIdAndStatus(group.getId(), InvitationStatus.PENDING);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void countByGroupIdAndStatus_returnsZeroWhenNoPendingInvitations() {
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        assertThat(repo.countByGroupIdAndStatus(group.getId(), InvitationStatus.PENDING)).isEqualTo(0);
    }

    @Test
    void countByGroupIdAndStatus_doesNotCountOtherGroupsInvitations() {
        Student s1 = makeStudent("23070000104");
        Student s2 = makeStudent("23070000105");
        ProjectGroup groupA = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        ProjectGroup groupB = makeGroup("Group B", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        makeInvitation(groupA, s1, InvitationStatus.PENDING);
        makeInvitation(groupB, s2, InvitationStatus.PENDING);

        assertThat(repo.countByGroupIdAndStatus(groupA.getId(), InvitationStatus.PENDING)).isEqualTo(1);
    }

    // ── autoDenyAllPendingByInviteeId ─────────────────────────────────────────
    // On coordinator add: AUTO_DENY all pending invitations for the student.

    @Test
    void autoDenyAllPendingByInviteeId_deniesEveryPendingInvitationForStudent() {
        Student student = makeStudent("23070000106");
        ProjectGroup group1 = makeGroup("Group 1", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        ProjectGroup group2 = makeGroup("Group 2", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        GroupInvitation pending1 = makeInvitation(group1, student, InvitationStatus.PENDING);
        GroupInvitation pending2 = makeInvitation(group2, student, InvitationStatus.PENDING);

        repo.autoDenyAllPendingByInviteeId(student.getId());
        clearCache();

        assertThat(repo.findById(pending1.getId()).get().getStatus()).isEqualTo(InvitationStatus.AUTO_DENIED);
        assertThat(repo.findById(pending2.getId()).get().getStatus()).isEqualTo(InvitationStatus.AUTO_DENIED);
    }

    @Test
    void autoDenyAllPendingByInviteeId_doesNotTouchNonPendingInvitations() {
        Student student = makeStudent("23070000107");
        ProjectGroup group = makeGroup("Group", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        GroupInvitation declined = makeInvitation(group, student, InvitationStatus.DECLINED);

        repo.autoDenyAllPendingByInviteeId(student.getId());
        clearCache();

        assertThat(repo.findById(declined.getId()).get().getStatus()).isEqualTo(InvitationStatus.DECLINED);
    }

    @Test
    void autoDenyAllPendingByInviteeId_doesNotAffectOtherStudents() {
        Student student = makeStudent("23070000108");
        Student otherStudent = makeStudent("23070000109");
        ProjectGroup group = makeGroup("Group", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        makeInvitation(group, student, InvitationStatus.PENDING);
        GroupInvitation otherStudentInv = makeInvitation(group, otherStudent, InvitationStatus.PENDING);

        repo.autoDenyAllPendingByInviteeId(student.getId());
        clearCache();

        assertThat(repo.findById(otherStudentInv.getId()).get().getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    // ── autoDenyAllPendingByGroupId ───────────────────────────────────────────
    // On group disband: AUTO_DENY all pending outbound invitations from the disbanded group

    @Test
    void autoDenyAllPendingByGroupId_deniesAllPendingForGroup() {
        Student s1 = makeStudent("23070000110");
        Student s2 = makeStudent("23070000111");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        GroupInvitation inv1 = makeInvitation(group, s1, InvitationStatus.PENDING);
        GroupInvitation inv2 = makeInvitation(group, s2, InvitationStatus.PENDING);

        repo.autoDenyAllPendingByGroupId(group.getId());
        clearCache();

        assertThat(repo.findById(inv1.getId()).get().getStatus()).isEqualTo(InvitationStatus.AUTO_DENIED);
        assertThat(repo.findById(inv2.getId()).get().getStatus()).isEqualTo(InvitationStatus.AUTO_DENIED);
    }

    @Test
    void autoDenyAllPendingByGroupId_doesNotAffectOtherGroups() {
        Student s1 = makeStudent("23070000112");
        Student s2 = makeStudent("23070000113");
        ProjectGroup disbanded = makeGroup("Disbanded", "2024-FALL", ProjectGroup.GroupStatus.DISBANDED);
        ProjectGroup active = makeGroup("Active", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        makeInvitation(disbanded, s1, InvitationStatus.PENDING);
        GroupInvitation activeInv = makeInvitation(active, s2, InvitationStatus.PENDING);

        repo.autoDenyAllPendingByGroupId(disbanded.getId());
        clearCache();

        // Active group's invitations must not be touched
        assertThat(repo.findById(activeInv.getId()).get().getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void autoDenyAllPendingByGroupId_doesNotTouchNonPendingInvitations() {
        Student s1 = makeStudent("23070000114");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        GroupInvitation declined = makeInvitation(group, s1, InvitationStatus.DECLINED);

        repo.autoDenyAllPendingByGroupId(group.getId());
        clearCache();

        assertThat(repo.findById(declined.getId()).get().getStatus()).isEqualTo(InvitationStatus.DECLINED);
    }

    // ── findByInviteeIdAndStatus ──────────────────────────────────────────────
    // Used for student's pending invitations inbox (seq 2.3)

    @Test
    void findByInviteeIdAndStatus_returnsStudentsPendingInvitations() {
        Student student = makeStudent("23070000115");
        Student other = makeStudent("23070000116");
        ProjectGroup g1 = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        ProjectGroup g2 = makeGroup("Group B", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        ProjectGroup g3 = makeGroup("Group C", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        makeInvitation(g1, student, InvitationStatus.PENDING);
        makeInvitation(g2, student, InvitationStatus.DECLINED); // not PENDING
        makeInvitation(g3, other, InvitationStatus.PENDING);    // different student

        var result = repo.findByInviteeIdAndStatus(student.getId(), InvitationStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGroup().getId()).isEqualTo(g1.getId());
    }

    // ── existsByGroupIdAndInviteeIdAndStatus ──────────────────────────────────
    // Duplicate-invite check (seq 2.2)

    @Test
    void existsByGroupIdAndInviteeIdAndStatus_trueWhenPendingInviteExists() {
        Student student = makeStudent("23070000117");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        makeInvitation(group, student, InvitationStatus.PENDING);

        assertThat(repo.existsByGroupIdAndInviteeIdAndStatus(
                group.getId(), student.getId(), InvitationStatus.PENDING)).isTrue();
    }

    @Test
    void existsByGroupIdAndInviteeIdAndStatus_falseWhenNoInvite() {
        Student student = makeStudent("23070000118");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        assertThat(repo.existsByGroupIdAndInviteeIdAndStatus(
                group.getId(), student.getId(), InvitationStatus.PENDING)).isFalse();
    }

    @Test
    void existsByGroupIdAndInviteeIdAndStatus_falseWhenInviteIsNotPending() {
        Student student = makeStudent("23070000119");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        makeInvitation(group, student, InvitationStatus.DECLINED);

        assertThat(repo.existsByGroupIdAndInviteeIdAndStatus(
                group.getId(), student.getId(), InvitationStatus.PENDING)).isFalse();
    }

    @Test
    void autoDenyOtherPendingInvitationsExcept_keepsAcceptedInvitationUntouched() {
        Student student = makeStudent("23070000130");
        ProjectGroup accepted = makeGroup("Accepted Group", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        ProjectGroup other = makeGroup("Other Group", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        GroupInvitation acceptedInv = makeInvitation(accepted, student, InvitationStatus.ACCEPTED);
        GroupInvitation pendingInv = makeInvitation(other, student, InvitationStatus.PENDING);

        repo.autoDenyOtherPendingInvitationsExcept(student.getId(), acceptedInv.getId());
        clearCache();

        assertThat(repo.findById(acceptedInv.getId()).get().getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(repo.findById(pendingInv.getId()).get().getStatus()).isEqualTo(InvitationStatus.AUTO_DENIED);
    }

    @Test
    void autoDenyOtherPendingInvitationsExcept_doesNotTouchNonPendingInvitations() {
        Student student = makeStudent("23070000131");
        ProjectGroup accepted = makeGroup("Accepted Group", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        ProjectGroup other = makeGroup("Other Group", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        GroupInvitation acceptedInv = makeInvitation(accepted, student, InvitationStatus.ACCEPTED);
        GroupInvitation declinedInv = makeInvitation(other, student, InvitationStatus.DECLINED);

        repo.autoDenyOtherPendingInvitationsExcept(student.getId(), acceptedInv.getId());
        clearCache();

        assertThat(repo.findById(declinedInv.getId()).get().getStatus()).isEqualTo(InvitationStatus.DECLINED);
    }

    @Test
    void autoDenyOtherPendingInvitationsExcept_doesNotAffectOtherStudents() {
        Student student = makeStudent("23070000132");
        Student otherStudent = makeStudent("23070000133");
        ProjectGroup accepted = makeGroup("Accepted Group", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        ProjectGroup other = makeGroup("Other Group", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        GroupInvitation acceptedInv = makeInvitation(accepted, student, InvitationStatus.ACCEPTED);
        GroupInvitation otherStudentPending = makeInvitation(other, otherStudent, InvitationStatus.PENDING);

        repo.autoDenyOtherPendingInvitationsExcept(student.getId(), acceptedInv.getId());
        clearCache();

        assertThat(repo.findById(otherStudentPending.getId()).get().getStatus()).isEqualTo(InvitationStatus.PENDING);
    }
}
