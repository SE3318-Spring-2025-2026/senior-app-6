package com.senior.spm.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;

import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import org.junit.jupiter.api.Test;

/**
 * Verifies entity-level constraints and annotations that the spec mandates.
 * Pure unit tests — no database or Spring context required.
 */
class EntityAnnotationTest {

    // ── termId type (must be String, not UUID — TermConfigService returns String) ──

    @Test
    void projectGroup_termId_isString() throws Exception {
        Field f = ProjectGroup.class.getDeclaredField("termId");
        assertThat(f.getType()).isEqualTo(String.class);
    }

    @Test
    void scheduleWindow_termId_isString() throws Exception {
        Field f = ScheduleWindow.class.getDeclaredField("termId");
        assertThat(f.getType()).isEqualTo(String.class);
    }

    // ── Optimistic locking — @Version Long version on ProjectGroup ───────────

    @Test
    void projectGroup_version_hasVersionAnnotation() throws Exception {
        Field f = ProjectGroup.class.getDeclaredField("version");
        assertThat(f.isAnnotationPresent(Version.class)).isTrue();
    }

    @Test
    void projectGroup_version_isLong() throws Exception {
        Field f = ProjectGroup.class.getDeclaredField("version");
        assertThat(f.getType()).isEqualTo(Long.class);
    }

    // ── GroupMembership TWO unique constraints ────────────────────────────────

    @Test
    void groupMembership_hasTwoUniqueConstraints() {
        Table table = GroupMembership.class.getAnnotation(Table.class);
        assertThat(table.uniqueConstraints()).hasSize(2);
    }

    @Test
    void groupMembership_compositeConstraint_isNamedCorrectlyAndCoversGroupAndStudent() {
        UniqueConstraint composite = findConstraint(GroupMembership.class, "uq_gm_group_student");
        assertThat(composite.columnNames()).containsExactlyInAnyOrder("group_id", "student_id");
    }

    @Test
    void groupMembership_singleConstraint_isNamedCorrectlyAndCoversStudentOnly() {
        UniqueConstraint single = findConstraint(GroupMembership.class, "uq_gm_student");
        assertThat(single.columnNames()).containsExactly("student_id");
    }

    // ── GroupInvitation column naming per ER spec ─────────────────────────────

    @Test
    void groupInvitation_invitee_joinColumnName_isInviteeStudentId() throws Exception {
        Field f = GroupInvitation.class.getDeclaredField("invitee");
        JoinColumn jc = f.getAnnotation(JoinColumn.class);
        assertThat(jc.name()).isEqualTo("invitee_student_id");
    }

    // ── ProjectGroup advisor FK is nullable ───────────────────────────────────

    @Test
    void projectGroup_advisor_joinColumn_isNullable() throws Exception {
        Field f = ProjectGroup.class.getDeclaredField("advisor");
        JoinColumn jc = f.getAnnotation(JoinColumn.class);
        assertThat(jc.nullable()).isTrue();
    }

    // ── Enum completeness ─────────────────────────────────────────────────────

    @Test
    void groupStatus_hasExactlyFiveValues() {
        assertThat(ProjectGroup.GroupStatus.values())
                .containsExactlyInAnyOrder(
                        ProjectGroup.GroupStatus.FORMING,
                        ProjectGroup.GroupStatus.TOOLS_PENDING,
                        ProjectGroup.GroupStatus.TOOLS_BOUND,
                        ProjectGroup.GroupStatus.ADVISOR_ASSIGNED,
                        ProjectGroup.GroupStatus.DISBANDED);
    }

    @Test
    void groupStatus_locksRosterOnlyAfterToolsBoundBeforeDisband() {
        assertThat(ProjectGroup.GroupStatus.FORMING.locksRoster()).isFalse();
        assertThat(ProjectGroup.GroupStatus.TOOLS_PENDING.locksRoster()).isFalse();
        assertThat(ProjectGroup.GroupStatus.TOOLS_BOUND.locksRoster()).isTrue();
        assertThat(ProjectGroup.GroupStatus.ADVISOR_ASSIGNED.locksRoster()).isTrue();
        assertThat(ProjectGroup.GroupStatus.DISBANDED.locksRoster()).isFalse();
    }

    @Test
    void invitationStatus_hasAllRequiredValues() {
        assertThat(GroupInvitation.InvitationStatus.values())
                .containsExactlyInAnyOrder(
                        GroupInvitation.InvitationStatus.PENDING,
                        GroupInvitation.InvitationStatus.ACCEPTED,
                        GroupInvitation.InvitationStatus.DECLINED,
                        GroupInvitation.InvitationStatus.CANCELLED,
                        GroupInvitation.InvitationStatus.AUTO_DENIED);
    }

    @Test
    void requestStatus_hasAllRequiredValues() {
        assertThat(AdvisorRequest.RequestStatus.values())
                .containsExactlyInAnyOrder(
                        AdvisorRequest.RequestStatus.PENDING,
                        AdvisorRequest.RequestStatus.ACCEPTED,
                        AdvisorRequest.RequestStatus.REJECTED,
                        AdvisorRequest.RequestStatus.AUTO_REJECTED,
                        AdvisorRequest.RequestStatus.CANCELLED);
    }

    @Test
    void memberRole_hasTeamLeaderAndMember() {
        assertThat(GroupMembership.MemberRole.values())
                .containsExactlyInAnyOrder(
                        GroupMembership.MemberRole.TEAM_LEADER,
                        GroupMembership.MemberRole.MEMBER);
    }

    @Test
    void windowType_hasGroupCreationAndAdvisorAssociation() {
        assertThat(ScheduleWindow.WindowType.values())
                .containsExactlyInAnyOrder(
                        ScheduleWindow.WindowType.GROUP_CREATION,
                        ScheduleWindow.WindowType.ADVISOR_ASSOCIATION);
    }

    // ── Encrypted token field lengths per ER spec (VARCHAR 1024) ─────────────

    @Test
    void projectGroup_encryptedJiraToken_columnLength_is1024() throws Exception {
        Field f = ProjectGroup.class.getDeclaredField("encryptedJiraToken");
        jakarta.persistence.Column col = f.getAnnotation(jakarta.persistence.Column.class);
        assertThat(col.length()).isEqualTo(1024);
    }

    @Test
    void projectGroup_encryptedGithubPat_columnLength_is1024() throws Exception {
        Field f = ProjectGroup.class.getDeclaredField("encryptedGithubPat");
        jakarta.persistence.Column col = f.getAnnotation(jakarta.persistence.Column.class);
        assertThat(col.length()).isEqualTo(1024);
    }

    // ── CommitteeProfessor unique constraint ──────────────────────────────────

    @Test
    void committeeProfessor_hasUniqueConstraint() {
        Table table = CommitteeProfessor.class.getAnnotation(Table.class);
        assertThat(table.uniqueConstraints()).hasSize(1);
    }

    @Test
    void committeeProfessor_uniqueConstraint_isNamedCorrectlyAndCoversCommitteeAndProfessor() {
        UniqueConstraint constraint = findConstraint(CommitteeProfessor.class, "uq_committee_professor_committee_professor");
        assertThat(constraint.columnNames()).containsExactlyInAnyOrder("committee_id", "professor_id");
    }

    // ── ProfessorRole enum completeness ───────────────────────────────────────

    @Test
    void professorRole_hasAdvisorAndJury() {
        assertThat(CommitteeProfessor.ProfessorRole.values())
                .containsExactlyInAnyOrder(
                        CommitteeProfessor.ProfessorRole.ADVISOR,
                        CommitteeProfessor.ProfessorRole.JURY);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UniqueConstraint findConstraint(Class<?> entityClass, String name) {
        Table table = entityClass.getAnnotation(Table.class);
        return Arrays.stream(table.uniqueConstraints())
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Constraint '" + name + "' not found on " + entityClass.getSimpleName()));
    }
}
