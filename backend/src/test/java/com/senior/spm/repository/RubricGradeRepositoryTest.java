package com.senior.spm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import com.senior.spm.entity.Deliverable;
import com.senior.spm.entity.DeliverableSubmission;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.RubricCriterion;
import com.senior.spm.entity.RubricGrade;
import com.senior.spm.entity.StaffUser;

@DataJpaTest
@TestPropertySource(properties = "spring.sql.init.mode=never")
class RubricGradeRepositoryTest extends RepositoryTestBase {

    @Autowired
    RubricGradeRepository rubricGradeRepo;

    // ── helpers ───────────────────────────────────────────────────────────────

    private Deliverable makeDeliverable(String name) {
        Deliverable d = new Deliverable();
        d.setName(name);
        d.setType(Deliverable.DeliverableType.Proposal);
        d.setSubmissionDeadline(LocalDateTime.now().plusDays(7));
        d.setReviewDeadline(LocalDateTime.now().plusDays(14));
        d.setWeight(BigDecimal.valueOf(30));
        return em.persistAndFlush(d);
    }

    private DeliverableSubmission makeSubmission(ProjectGroup group, Deliverable deliverable) {
        DeliverableSubmission s = new DeliverableSubmission();
        s.setGroup(group);
        s.setDeliverable(deliverable);
        s.setMarkdownContent("# Test");
        s.setSubmittedAt(LocalDateTime.now());
        return em.persistAndFlush(s);
    }

    private RubricCriterion makeCriterion(Deliverable deliverable, String name) {
        RubricCriterion c = new RubricCriterion();
        c.setDeliverable(deliverable);
        c.setCriterionName(name);
        c.setGradingType(RubricCriterion.GradingType.Soft);
        c.setWeight(BigDecimal.valueOf(50));
        return em.persistAndFlush(c);
    }

    private RubricGrade makeRubricGrade(DeliverableSubmission submission, RubricCriterion criterion,
            StaffUser reviewer, String grade) {
        RubricGrade rg = new RubricGrade();
        rg.setSubmission(submission);
        rg.setCriterion(criterion);
        rg.setReviewer(reviewer);
        rg.setSelectedGrade(grade);
        rg.setGradedAt(LocalDateTime.now());
        return em.persistAndFlush(rg);
    }

    // ── findBySubmissionId ────────────────────────────────────────────────────

    @Test
    void findBySubmissionId_returnsAllGradesForSubmission() {
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        Deliverable deliverable = makeDeliverable("Proposal");
        DeliverableSubmission submission = makeSubmission(group, deliverable);
        RubricCriterion c1 = makeCriterion(deliverable, "Criterion 1");
        RubricCriterion c2 = makeCriterion(deliverable, "Criterion 2");
        StaffUser reviewer = makeProfessor("reviewer@test.com");

        makeRubricGrade(submission, c1, reviewer, "A");
        makeRubricGrade(submission, c2, reviewer, "B");

        var result = rubricGradeRepo.findBySubmissionId(submission.getId());

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(rg -> rg.getSubmission().getId().equals(submission.getId()));
    }

    @Test
    void findBySubmissionId_returnsEmptyWhenNoGradesExist() {
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        Deliverable deliverable = makeDeliverable("Proposal");
        DeliverableSubmission submission = makeSubmission(group, deliverable);

        var result = rubricGradeRepo.findBySubmissionId(submission.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findBySubmissionId_doesNotReturnGradesFromOtherSubmissions() {
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        ProjectGroup group2 = makeGroup("G2", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        Deliverable deliverable = makeDeliverable("Proposal");
        DeliverableSubmission s1 = makeSubmission(group, deliverable);
        DeliverableSubmission s2 = makeSubmission(group2, deliverable);
        RubricCriterion criterion = makeCriterion(deliverable, "Criterion 1");
        StaffUser reviewer = makeProfessor("reviewer@test.com");

        makeRubricGrade(s1, criterion, reviewer, "A");

        var result = rubricGradeRepo.findBySubmissionId(s2.getId());

        assertThat(result).isEmpty();
    }

    // ── findBySubmissionIdAndCriterionIdAndReviewerId ─────────────────────────

    @Test
    void findBySubmissionIdAndCriterionIdAndReviewerId_returnsGradeWhenExists() {
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        Deliverable deliverable = makeDeliverable("Proposal");
        DeliverableSubmission submission = makeSubmission(group, deliverable);
        RubricCriterion criterion = makeCriterion(deliverable, "Criterion 1");
        StaffUser reviewer = makeProfessor("reviewer@test.com");

        makeRubricGrade(submission, criterion, reviewer, "B");

        var result = rubricGradeRepo.findBySubmissionIdAndCriterionIdAndReviewerId(
                submission.getId(), criterion.getId(), reviewer.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getSelectedGrade()).isEqualTo("B");
    }

    @Test
    void findBySubmissionIdAndCriterionIdAndReviewerId_returnsEmptyWhenNotFound() {
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        Deliverable deliverable = makeDeliverable("Proposal");
        DeliverableSubmission submission = makeSubmission(group, deliverable);
        RubricCriterion criterion = makeCriterion(deliverable, "Criterion 1");
        StaffUser reviewer = makeProfessor("reviewer@test.com");

        var result = rubricGradeRepo.findBySubmissionIdAndCriterionIdAndReviewerId(
                submission.getId(), criterion.getId(), reviewer.getId());

        assertThat(result).isEmpty();
    }

    // ── unique constraint enforcement ─────────────────────────────────────────

    @Test
    void uniqueConstraint_uqRgSubmissionCriterionReviewer_preventsInsertOfDuplicate() {
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        Deliverable deliverable = makeDeliverable("Proposal");
        DeliverableSubmission submission = makeSubmission(group, deliverable);
        RubricCriterion criterion = makeCriterion(deliverable, "Criterion 1");
        StaffUser reviewer = makeProfessor("reviewer@test.com");

        makeRubricGrade(submission, criterion, reviewer, "A");

        RubricGrade duplicate = new RubricGrade();
        duplicate.setSubmission(submission);
        duplicate.setCriterion(criterion);
        duplicate.setReviewer(reviewer);
        duplicate.setSelectedGrade("B");
        duplicate.setGradedAt(LocalDateTime.now());

        assertThatThrownBy(() -> em.persistAndFlush(duplicate))
                .isInstanceOf(PersistenceException.class);
    }
}
