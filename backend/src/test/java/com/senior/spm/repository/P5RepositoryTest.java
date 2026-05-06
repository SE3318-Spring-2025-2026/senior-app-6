package com.senior.spm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ScrumGrade;
import com.senior.spm.entity.ScrumGrade.ScrumGradeValue;
import com.senior.spm.entity.Sprint;
import com.senior.spm.entity.SprintTrackingLog;
import com.senior.spm.entity.SprintTrackingLog.AiValidationResult;
import com.senior.spm.entity.StaffUser;

@DataJpaTest
@TestPropertySource(properties = "spring.sql.init.mode=never")
class P5RepositoryTest extends RepositoryTestBase {

    @Autowired
    SprintRepository sprintRepo;

    @Autowired
    SprintTrackingLogRepository trackingRepo;

    @Autowired
    ScrumGradeRepository gradeRepo;

    // ── helpers ──────────────────────────────────────────────────────────────

    private SprintTrackingLog makeLog(ProjectGroup group, Sprint sprint, String issueKey) {
        SprintTrackingLog log = new SprintTrackingLog();
        log.setGroup(group);
        log.setSprint(sprint);
        log.setIssueKey(issueKey);
        log.setFetchedAt(LocalDateTime.now());
        return em.persistAndFlush(log);
    }

    private ScrumGrade makeGrade(ProjectGroup group, Sprint sprint, StaffUser advisor,
            ScrumGradeValue pointA, ScrumGradeValue pointB) {
        ScrumGrade grade = new ScrumGrade();
        grade.setGroup(group);
        grade.setSprint(sprint);
        grade.setAdvisor(advisor);
        grade.setPointAGrade(pointA);
        grade.setPointBGrade(pointB);
        grade.setGradedAt(LocalDateTime.now());
        return em.persistAndFlush(grade);
    }

    // ── SprintRepository ────────────────────────────────────────────────────────

    @Test
    void findActiveSprint_returnsSprintWhoseDateRangeCoversToday() {
        LocalDate today = LocalDate.now();
        Sprint active = makeSprint(today.minusDays(5), today.plusDays(9));
        makeSprint(today.plusDays(10), today.plusDays(24)); // future sprint, must not appear

        var result = sprintRepo.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(today, today);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(active.getId());
    }

    @Test
    void findActiveSprint_returnsEmptyWhenSprintExpiredYesterday() {
        LocalDate today = LocalDate.now();
        makeSprint(today.minusDays(14), today.minusDays(1)); // ended yesterday

        var result = sprintRepo.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(today, today);

        assertThat(result).isEmpty();
    }

    @Test
    void findActiveSprint_returnsEmptyWhenSprintStartsTomorrow() {
        LocalDate today = LocalDate.now();
        makeSprint(today.plusDays(1), today.plusDays(14)); // starts tomorrow

        var result = sprintRepo.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(today, today);

        assertThat(result).isEmpty();
    }

    @Test
    void findActiveSprint_sprintStartingTodayIsActive() {
        LocalDate today = LocalDate.now();
        Sprint sprint = makeSprint(today, today.plusDays(13)); // starts today

        var result = sprintRepo.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(today, today);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(sprint.getId());
    }

    @Test
    void findActiveSprint_sprintEndingTodayIsActive() {
        LocalDate today = LocalDate.now();
        Sprint sprint = makeSprint(today.minusDays(13), today); // ends today

        var result = sprintRepo.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(today, today);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(sprint.getId());
    }

    // ── SprintTrackingLogRepository ───────────────────────────────────────────

    @Test
    void findByGroupIdAndSprintId_returnsOnlyMatchingRows() {
        Sprint sprint = makeSprint(LocalDate.now(), LocalDate.now().plusDays(14));
        ProjectGroup g1 = makeGroup("G1", "T1", ProjectGroup.GroupStatus.TOOLS_BOUND);
        ProjectGroup g2 = makeGroup("G2", "T1", ProjectGroup.GroupStatus.TOOLS_BOUND);

        makeLog(g1, sprint, "SPM-1");
        makeLog(g1, sprint, "SPM-2");
        makeLog(g2, sprint, "SPM-3");

        var result = trackingRepo.findByGroupIdAndSprintId(g1.getId(), sprint.getId());

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(l -> l.getGroup().getId().equals(g1.getId()));
    }

    @Test
    void findByGroupIdAndSprintId_returnsEmptyWhenNoRows() {
        Sprint sprint = makeSprint(LocalDate.now(), LocalDate.now().plusDays(14));
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.TOOLS_BOUND);

        var result = trackingRepo.findByGroupIdAndSprintId(group.getId(), sprint.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findBySprintId_returnsAllRowsForSprint_notOtherSprints() {
        Sprint sprint1 = makeSprint(LocalDate.now(), LocalDate.now().plusDays(14));
        Sprint sprint2 = makeSprint(LocalDate.now().plusDays(15), LocalDate.now().plusDays(28));
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.TOOLS_BOUND);

        makeLog(group, sprint1, "SPM-1");
        makeLog(group, sprint1, "SPM-2");
        makeLog(group, sprint2, "SPM-3");

        var result = trackingRepo.findBySprintId(sprint1.getId());

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(l -> l.getSprint().getId().equals(sprint1.getId()));
    }

    @Test
    void deleteByGroupIdAndSprintId_removesOnlyMatchingRows() {
        Sprint sprint1 = makeSprint(LocalDate.now(), LocalDate.now().plusDays(14));
        Sprint sprint2 = makeSprint(LocalDate.now().plusDays(15), LocalDate.now().plusDays(28));
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.TOOLS_BOUND);

        makeLog(group, sprint1, "SPM-1");
        makeLog(group, sprint1, "SPM-2");
        makeLog(group, sprint2, "SPM-3");

        trackingRepo.deleteByGroupIdAndSprintId(group.getId(), sprint1.getId());
        clearCache();

        assertThat(trackingRepo.findByGroupIdAndSprintId(group.getId(), sprint1.getId())).isEmpty();
        assertThat(trackingRepo.findByGroupIdAndSprintId(group.getId(), sprint2.getId())).hasSize(1);
    }

    @Test
    void deleteBySprintId_removesAllRowsForSprint_leavesOtherSprintsIntact() {
        Sprint sprint1 = makeSprint(LocalDate.now(), LocalDate.now().plusDays(14));
        Sprint sprint2 = makeSprint(LocalDate.now().plusDays(15), LocalDate.now().plusDays(28));
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.TOOLS_BOUND);

        makeLog(group, sprint1, "SPM-1");
        makeLog(group, sprint1, "SPM-2");
        makeLog(group, sprint2, "SPM-3");

        trackingRepo.deleteBySprintId(sprint1.getId());
        clearCache();

        assertThat(trackingRepo.findBySprintId(sprint1.getId())).isEmpty();
        assertThat(trackingRepo.findBySprintId(sprint2.getId())).hasSize(1);
    }

    @Test
    void newLog_aiResultsDefaultToPending() {
        Sprint sprint = makeSprint(LocalDate.now(), LocalDate.now().plusDays(14));
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.TOOLS_BOUND);

        SprintTrackingLog log = makeLog(group, sprint, "SPM-1");

        assertThat(log.getAiPrResult()).isEqualTo(AiValidationResult.PENDING);
        assertThat(log.getAiDiffResult()).isEqualTo(AiValidationResult.PENDING);
    }

    @Test
    void uniqueConstraint_uqStlGroupSprintIssue_preventsInsertOfDuplicate() {
        Sprint sprint = makeSprint(LocalDate.now(), LocalDate.now().plusDays(14));
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.TOOLS_BOUND);
        makeLog(group, sprint, "SPM-1");

        SprintTrackingLog duplicate = new SprintTrackingLog();
        duplicate.setGroup(group);
        duplicate.setSprint(sprint);
        duplicate.setIssueKey("SPM-1");
        duplicate.setFetchedAt(LocalDateTime.now());

        assertThatThrownBy(() -> em.persistAndFlush(duplicate))
                .isInstanceOf(PersistenceException.class);
    }

    // ── ScrumGradeRepository ──────────────────────────────────────────────────

    @Test
    void findByGroupIdAndSprintId_returnsGradeWhenExists() {
        Sprint sprint = makeSprint(LocalDate.now(), LocalDate.now().plusDays(14));
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        StaffUser advisor = makeProfessor("advisor@test.com");

        makeGrade(group, sprint, advisor, ScrumGradeValue.A, ScrumGradeValue.B);

        var result = gradeRepo.findByGroupIdAndSprintId(group.getId(), sprint.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getPointAGrade()).isEqualTo(ScrumGradeValue.A);
        assertThat(result.get().getPointBGrade()).isEqualTo(ScrumGradeValue.B);
    }

    @Test
    void findByGroupIdAndSprintId_returnsEmptyWhenNoGrade() {
        Sprint sprint = makeSprint(LocalDate.now(), LocalDate.now().plusDays(14));
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);

        var result = gradeRepo.findByGroupIdAndSprintId(group.getId(), sprint.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByAdvisorIdAndSprintId_returnsOnlyAdvisorsGrades() {
        Sprint sprint = makeSprint(LocalDate.now(), LocalDate.now().plusDays(14));
        ProjectGroup g1 = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        ProjectGroup g2 = makeGroup("G2", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        StaffUser advisor1 = makeProfessor("adv1@test.com");
        StaffUser advisor2 = makeProfessor("adv2@test.com");

        makeGrade(g1, sprint, advisor1, ScrumGradeValue.A, ScrumGradeValue.A);
        makeGrade(g2, sprint, advisor2, ScrumGradeValue.B, ScrumGradeValue.C);

        var result = gradeRepo.findByAdvisorIdAndSprintId(advisor1.getId(), sprint.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAdvisor().getId()).isEqualTo(advisor1.getId());
    }

    @Test
    void existsByGroupIdAndSprintId_trueWhenGradeExists() {
        Sprint sprint = makeSprint(LocalDate.now(), LocalDate.now().plusDays(14));
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        StaffUser advisor = makeProfessor("adv@test.com");

        makeGrade(group, sprint, advisor, ScrumGradeValue.C, ScrumGradeValue.D);

        assertThat(gradeRepo.existsByGroupIdAndSprintId(group.getId(), sprint.getId())).isTrue();
    }

    @Test
    void existsByGroupIdAndSprintId_falseWhenNoGrade() {
        Sprint sprint = makeSprint(LocalDate.now(), LocalDate.now().plusDays(14));
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);

        assertThat(gradeRepo.existsByGroupIdAndSprintId(group.getId(), sprint.getId())).isFalse();
    }

    @Test
    void uniqueConstraint_uqSgGroupSprint_preventsInsertOfDuplicate() {
        Sprint sprint = makeSprint(LocalDate.now(), LocalDate.now().plusDays(14));
        ProjectGroup group = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        StaffUser advisor = makeProfessor("adv@test2.com");
        makeGrade(group, sprint, advisor, ScrumGradeValue.A, ScrumGradeValue.B);

        ScrumGrade duplicate = new ScrumGrade();
        duplicate.setGroup(group);
        duplicate.setSprint(sprint);
        duplicate.setAdvisor(advisor);
        duplicate.setPointAGrade(ScrumGradeValue.B);
        duplicate.setPointBGrade(ScrumGradeValue.C);
        duplicate.setGradedAt(LocalDateTime.now());

        assertThatThrownBy(() -> em.persistAndFlush(duplicate))
                .isInstanceOf(PersistenceException.class);
    }
}
