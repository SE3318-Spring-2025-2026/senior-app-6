package com.senior.spm.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;

import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    // ── DeliverableSubmission foreign keys ─────────────────────────────────────

    @Test
    void deliverableSubmission_group_joinColumn_isCorrect() throws Exception {
        Field f = DeliverableSubmission.class.getDeclaredField("group");
        JoinColumn jc = f.getAnnotation(JoinColumn.class);
        assertThat(jc.name()).isEqualTo("group_id");
        assertThat(jc.nullable()).isFalse();
    }

    @Test
    void deliverableSubmission_deliverable_joinColumn_isCorrect() throws Exception {
        Field f = DeliverableSubmission.class.getDeclaredField("deliverable");
        JoinColumn jc = f.getAnnotation(JoinColumn.class);
        assertThat(jc.name()).isEqualTo("deliverable_id");
        assertThat(jc.nullable()).isFalse();
    }

    @Test
    void deliverableSubmission_revisionNumber_hasDefaultValue() throws Exception {
        Field f = DeliverableSubmission.class.getDeclaredField("revisionNumber");
        assertThat(f.getType()).isEqualTo(int.class);
    }

    // ── RubricMapping foreign keys ──────────────────────────────────────────────

    @Test
    void rubricMapping_submission_joinColumn_isCorrect() throws Exception {
        Field f = RubricMapping.class.getDeclaredField("submission");
        JoinColumn jc = f.getAnnotation(JoinColumn.class);
        assertThat(jc.name()).isEqualTo("submission_id");
        assertThat(jc.nullable()).isFalse();
    }

    @Test
    void rubricMapping_rubricCriterion_joinColumn_isCorrect() throws Exception {
        Field f = RubricMapping.class.getDeclaredField("rubricCriterion");
        JoinColumn jc = f.getAnnotation(JoinColumn.class);
        assertThat(jc.name()).isEqualTo("rubric_criterion_id");
        assertThat(jc.nullable()).isFalse();
    }

    // ── SubmissionComment foreign keys ──────────────────────────────────────────

    @Test
    void submissionComment_submission_joinColumn_isCorrect() throws Exception {
        Field f = SubmissionComment.class.getDeclaredField("submission");
        JoinColumn jc = f.getAnnotation(JoinColumn.class);
        assertThat(jc.name()).isEqualTo("submission_id");
        assertThat(jc.nullable()).isFalse();
    }

    @Test
    void submissionComment_commenter_joinColumn_isCorrect() throws Exception {
        Field f = SubmissionComment.class.getDeclaredField("commenter");
        JoinColumn jc = f.getAnnotation(JoinColumn.class);
        assertThat(jc.name()).isEqualTo("commenter_id");
        assertThat(jc.nullable()).isFalse();
    }

    // ── FinalGrade entity ─────────────────────────────────────────────────────

    @Test
    void finalGrade_tableName_is_final_grade() {
        Table table = FinalGrade.class.getAnnotation(Table.class);
        assertThat(table.name()).isEqualTo("final_grade");
    }

    @Test
    void finalGrade_hasExactlyOneUniqueConstraint() {
        Table table = FinalGrade.class.getAnnotation(Table.class);
        assertThat(table.uniqueConstraints()).hasSize(1);
    }

    @Test
    void finalGrade_uniqueConstraint_isNamedCorrectlyAndCoversStudentId() {
        UniqueConstraint uc = findConstraint(FinalGrade.class, "uq_fg_student");
        assertThat(uc.columnNames()).containsExactly("student_id");
    }

    @Test
    void finalGrade_student_joinColumn_isNotNullableAndNamedCorrectly() throws Exception {
        Field f = FinalGrade.class.getDeclaredField("student");
        JoinColumn jc = f.getAnnotation(JoinColumn.class);
        assertThat(jc.name()).isEqualTo("student_id");
        assertThat(jc.nullable()).isFalse();
    }

    @Test
    void finalGrade_group_joinColumn_isNotNullableAndNamedCorrectly() throws Exception {
        Field f = FinalGrade.class.getDeclaredField("group");
        JoinColumn jc = f.getAnnotation(JoinColumn.class);
        assertThat(jc.name()).isEqualTo("group_id");
        assertThat(jc.nullable()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"weightedTotal", "completionRatio", "finalGrade"})
    void finalGrade_bigDecimalFields_areNullableWithPrecision10Scale4(String fieldName) throws Exception {
        Field f = FinalGrade.class.getDeclaredField(fieldName);
        jakarta.persistence.Column col = f.getAnnotation(jakarta.persistence.Column.class);
        assertThat(col.nullable()).isTrue();
        assertThat(col.precision()).isEqualTo(10);
        assertThat(col.scale()).isEqualTo(4);
    }

    @Test
    void finalGrade_calculatedAt_isNullable() throws Exception {
        Field f = FinalGrade.class.getDeclaredField("calculatedAt");
        jakarta.persistence.Column col = f.getAnnotation(jakarta.persistence.Column.class);
        assertThat(col.nullable()).isTrue();
    }

    // ── RubricGrade entity constraints ────────────────────────────────────────

    @Test
    void rubricGrade_tableName_is_rubric_grade() {
        Table table = RubricGrade.class.getAnnotation(Table.class);
        assertThat(table.name()).isEqualTo("rubric_grade");
    }

    @Test
    void rubricGrade_hasExactlyOneUniqueConstraint() {
        Table table = RubricGrade.class.getAnnotation(Table.class);
        assertThat(table.uniqueConstraints()).hasSize(1);
    }

    @Test
    void rubricGrade_uniqueConstraint_hasCorrectColumnsAndName() {
        UniqueConstraint uc = findConstraint(RubricGrade.class, "uq_rg_submission_criterion_reviewer");
        assertThat(uc.columnNames()).containsExactlyInAnyOrder("submission_id", "criterion_id", "reviewer_id");
    }

    @Test
    void rubricGrade_submission_joinColumn_isCorrect() throws Exception {
        Field f = RubricGrade.class.getDeclaredField("submission");
        JoinColumn jc = f.getAnnotation(JoinColumn.class);
        assertThat(jc.name()).isEqualTo("submission_id");
        assertThat(jc.nullable()).isFalse();
    }

    @Test
    void rubricGrade_criterion_joinColumn_isCorrect() throws Exception {
        Field f = RubricGrade.class.getDeclaredField("criterion");
        JoinColumn jc = f.getAnnotation(JoinColumn.class);
        assertThat(jc.name()).isEqualTo("criterion_id");
        assertThat(jc.nullable()).isFalse();
    }

    @Test
    void rubricGrade_reviewer_joinColumn_isCorrect() throws Exception {
        Field f = RubricGrade.class.getDeclaredField("reviewer");
        JoinColumn jc = f.getAnnotation(JoinColumn.class);
        assertThat(jc.name()).isEqualTo("reviewer_id");
        assertThat(jc.nullable()).isFalse();
    }

    @Test
    void rubricGrade_selectedGrade_isNotNullable() throws Exception {
        Field f = RubricGrade.class.getDeclaredField("selectedGrade");
        jakarta.persistence.Column col = f.getAnnotation(jakarta.persistence.Column.class);
        assertThat(col.nullable()).isFalse();
    }

    @Test
    void rubricGrade_gradedAt_isNotNullable() throws Exception {
        Field f = RubricGrade.class.getDeclaredField("gradedAt");
        jakarta.persistence.Column col = f.getAnnotation(jakarta.persistence.Column.class);
        assertThat(col.nullable()).isFalse();
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
