package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.senior.spm.controller.response.DeliverableBreakdown;
import com.senior.spm.controller.response.FinalGradeResponse;
import com.senior.spm.entity.Deliverable;
import com.senior.spm.entity.DeliverableSubmission;
import com.senior.spm.entity.FinalGrade;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.RubricCriterion;
import com.senior.spm.entity.RubricGrade;
import com.senior.spm.entity.ScrumGrade;
import com.senior.spm.entity.ScrumGrade.ScrumGradeValue;
import com.senior.spm.entity.Sprint;
import com.senior.spm.entity.SprintDeliverableMapping;
import com.senior.spm.entity.SprintTrackingLog;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.Student;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.DeliverableRepository;
import com.senior.spm.repository.DeliverableSubmissionRepository;
import com.senior.spm.repository.FinalGradeRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.RubricCriterionRepository;
import com.senior.spm.repository.RubricGradeRepository;
import com.senior.spm.repository.ScrumGradeRepository;
import com.senior.spm.repository.SprintDeliverableMappingRepository;
import com.senior.spm.repository.SprintRepository;
import com.senior.spm.repository.SprintTrackingLogRepository;
import com.senior.spm.repository.StudentRepository;

@ExtendWith(MockitoExtension.class)
class FinalGradeCalculationServiceTest {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final String STUDENT_ID  = "12345678901";
    private static final String TERM_ID     = "2024-FALL";
    private static final String GITHUB_USER = "alice";

    private static final UUID STUDENT_UUID     = UUID.randomUUID();
    private static final UUID GROUP_UUID       = UUID.randomUUID();
    private static final UUID SPRINT1_UUID     = UUID.randomUUID();
    private static final UUID SPRINT2_UUID     = UUID.randomUUID();
    private static final UUID DELIVERABLE_UUID = UUID.randomUUID();
    private static final UUID SUBMISSION_UUID  = UUID.randomUUID();
    private static final UUID CRIT1_UUID       = UUID.randomUUID();
    private static final UUID CRIT2_UUID       = UUID.randomUUID();
    private static final UUID REVIEWER_UUID    = UUID.randomUUID();

    // ── Mocks ─────────────────────────────────────────────────────────────────
    @Mock StudentRepository                  studentRepository;
    @Mock GroupMembershipRepository          groupMembershipRepository;
    @Mock DeliverableRepository              deliverableRepository;
    @Mock SprintRepository                   sprintRepository;
    @Mock SprintDeliverableMappingRepository sprintDeliverableMappingRepository;
    @Mock ScrumGradeRepository               scrumGradeRepository;
    @Mock SprintTrackingLogRepository        sprintTrackingLogRepository;
    @Mock FinalGradeRepository               finalGradeRepository;
    @Mock TermConfigService                  termConfigService;
    @Mock DeliverableSubmissionRepository    deliverableSubmissionRepository;
    @Mock RubricGradeRepository              rubricGradeRepository;
    @Mock RubricCriterionRepository          rubricCriterionRepository;

    @InjectMocks FinalGradeCalculationService service;

    // ── Entities ──────────────────────────────────────────────────────────────
    private Student                  student;
    private ProjectGroup             group;
    private GroupMembership          membership;
    private Deliverable              deliverable;
    private Sprint                   sprint1;
    private Sprint                   sprint2;
    private SprintDeliverableMapping mapping1;
    private SprintDeliverableMapping mapping2;

    @BeforeEach
    void setUp() {
        student = new Student();
        student.setId(STUDENT_UUID);
        student.setStudentId(STUDENT_ID);
        student.setGithubUsername(GITHUB_USER);

        group = new ProjectGroup();
        group.setId(GROUP_UUID);
        group.setGroupName("TeamAlpha");
        group.setTermId(TERM_ID);
        group.setStatus(ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        group.setCreatedAt(LocalDateTime.now());
        group.setVersion(0L);

        membership = new GroupMembership();
        membership.setGroup(group);
        membership.setStudent(student);
        membership.setRole(GroupMembership.MemberRole.MEMBER);
        membership.setJoinedAt(LocalDateTime.now());

        deliverable = new Deliverable();
        deliverable.setId(DELIVERABLE_UUID);
        deliverable.setName("Proposal");
        deliverable.setType(Deliverable.DeliverableType.Proposal);
        deliverable.setWeight(new BigDecimal("30.00"));
        deliverable.setSubmissionDeadline(LocalDateTime.now().plusDays(30));
        deliverable.setReviewDeadline(LocalDateTime.now().plusDays(35));

        sprint1 = new Sprint();
        sprint1.setId(SPRINT1_UUID);
        sprint1.setStartDate(LocalDate.now().minusDays(20));
        sprint1.setEndDate(LocalDate.now().minusDays(11));
        sprint1.setStoryPointTarget(10);

        sprint2 = new Sprint();
        sprint2.setId(SPRINT2_UUID);
        sprint2.setStartDate(LocalDate.now().minusDays(10));
        sprint2.setEndDate(LocalDate.now().minusDays(1));
        sprint2.setStoryPointTarget(10);

        mapping1 = new SprintDeliverableMapping();
        mapping1.setSprint(sprint1);
        mapping1.setDeliverable(deliverable);
        mapping1.setContributionPercentage(new BigDecimal("50.00"));

        mapping2 = new SprintDeliverableMapping();
        mapping2.setSprint(sprint2);
        mapping2.setDeliverable(deliverable);
        mapping2.setContributionPercentage(new BigDecimal("50.00"));
    }

    // ── Student / membership resolution ──────────────────────────────────────

    @Test
    void studentNotFound_throwsNotFoundException() {
        when(studentRepository.findByStudentId(STUDENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculateFinalGrade(STUDENT_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Student not found");
    }

    @Test
    void noGroupMembership_throwsNotFoundException() {
        when(studentRepository.findByStudentId(STUDENT_ID)).thenReturn(Optional.of(student));
        when(groupMembershipRepository.findByStudentId(STUDENT_UUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculateFinalGrade(STUDENT_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Student has no group membership");
    }

    // ── Scalar calculations ───────────────────────────────────────────────────

    @Test
    void twoSprints_scalarsCorrect() {
        // pointA=[A(100), B(80)], pointB=[B(80), C(60)]
        // ScrumScalar = (100+80)/2 / 100 = 0.9
        // ReviewScalar = (80+60)/2 / 100 = 0.7
        // DS = (0.9+0.7)/2 = 0.8
        mockResolution();
        mockSprintSetup();
        mockNoScrumGrades(); // will be overridden below for specific sprints
        when(scrumGradeRepository.findByGroupIdAndSprintId(GROUP_UUID, SPRINT1_UUID))
                .thenReturn(Optional.of(makeScrumGrade(sprint1, ScrumGradeValue.A, ScrumGradeValue.B)));
        when(scrumGradeRepository.findByGroupIdAndSprintId(GROUP_UUID, SPRINT2_UUID))
                .thenReturn(Optional.of(makeScrumGrade(sprint2, ScrumGradeValue.B, ScrumGradeValue.C)));
        when(deliverableSubmissionRepository
                .findFirstByGroupAndDeliverableOrderBySubmittedAtDesc(group, deliverable))
                .thenReturn(null);
        when(sprintTrackingLogRepository
                .findByGroupIdAndAssigneeGithubUsernameAndPrMergedTrue(GROUP_UUID, GITHUB_USER))
                .thenReturn(List.of());
        mockFinalGradeUpsert(Optional.empty());

        FinalGradeResponse response = service.calculateFinalGrade(STUDENT_ID);

        DeliverableBreakdown bd = response.getDeliverableBreakdown().get(0);
        assertThat(bd.getScrumScalar()).isEqualByComparingTo(new BigDecimal("0.9"));
        assertThat(bd.getReviewScalar()).isEqualByComparingTo(new BigDecimal("0.7"));
        assertThat(bd.getDeliverableScalar()).isEqualByComparingTo(new BigDecimal("0.8"));
    }

    // ── Full pipeline — exact G_i assertion ──────────────────────────────────

    @Test
    void fullPipeline_Gi_exact() {
        // pointA=[A(100), B(80)], pointB=[B(80), C(60)] → ScrumScalar=0.9, ReviewScalar=0.7, DS=0.8
        // 1 reviewer, 2 Soft criteria weight=1 each: A(100)+B(80) → B = (100+80)/2 = 90
        // ScaledGrade = 90 × 0.8 = 72
        // weightedContribution = 72 × 30/100 = 21.6   (deliverable.weight=30)
        // completedSP=15, targetSP=10+10=20 → C_i=0.75
        // G_i = 21.6 × 0.75 = 16.2  ← assert exactly
        mockResolution();
        mockSprintSetup();
        when(scrumGradeRepository.findByGroupIdAndSprintId(GROUP_UUID, SPRINT1_UUID))
                .thenReturn(Optional.of(makeScrumGrade(sprint1, ScrumGradeValue.A, ScrumGradeValue.B)));
        when(scrumGradeRepository.findByGroupIdAndSprintId(GROUP_UUID, SPRINT2_UUID))
                .thenReturn(Optional.of(makeScrumGrade(sprint2, ScrumGradeValue.B, ScrumGradeValue.C)));

        // Rubric setup → B = 90
        DeliverableSubmission submission = new DeliverableSubmission();
        submission.setId(SUBMISSION_UUID);
        StaffUser reviewer = new StaffUser();
        reviewer.setId(REVIEWER_UUID);

        RubricCriterion crit1 = makeCriterion(CRIT1_UUID, RubricCriterion.GradingType.Soft, new BigDecimal("1.00"));
        RubricCriterion crit2 = makeCriterion(CRIT2_UUID, RubricCriterion.GradingType.Soft, new BigDecimal("1.00"));

        when(deliverableSubmissionRepository
                .findFirstByGroupAndDeliverableOrderBySubmittedAtDesc(group, deliverable))
                .thenReturn(submission);
        when(rubricGradeRepository.findBySubmissionId(SUBMISSION_UUID))
                .thenReturn(List.of(
                        makeRubricGrade(submission, crit1, reviewer, "A"),  // 100 × 1
                        makeRubricGrade(submission, crit2, reviewer, "B")   //  80 × 1
                ));
        when(rubricCriterionRepository.findAllByDeliverableId(DELIVERABLE_UUID))
                .thenReturn(List.of(crit1, crit2));

        // C_i setup: completedSP=15, targetSP=20
        SprintTrackingLog log = new SprintTrackingLog();
        log.setStoryPoints(15);
        when(sprintTrackingLogRepository
                .findByGroupIdAndAssigneeGithubUsernameAndPrMergedTrue(GROUP_UUID, GITHUB_USER))
                .thenReturn(List.of(log));

        mockFinalGradeUpsert(Optional.empty());

        FinalGradeResponse response = service.calculateFinalGrade(STUDENT_ID);

        assertThat(response.getCompletionRatio()).isEqualByComparingTo(new BigDecimal("0.75"));
        assertThat(response.getFinalGrade()).isEqualByComparingTo(new BigDecimal("16.2"));

        DeliverableBreakdown bd = response.getDeliverableBreakdown().get(0);
        assertThat(bd.getBaseGrade()).isEqualByComparingTo(new BigDecimal("90"));
        assertThat(bd.getDeliverableScalar()).isEqualByComparingTo(new BigDecimal("0.8"));
        assertThat(bd.getScaledGrade()).isEqualByComparingTo(new BigDecimal("72"));
        assertThat(bd.getWeightedContribution()).isEqualByComparingTo(new BigDecimal("21.6"));
    }

    // ── Edge case: sprint with no ScrumGrade ─────────────────────────────────

    @Test
    void noScrumGrade_sprint_skipped_scalarsAreZero() {
        // Both sprints mapped but no ScrumGrade → pointAValues empty → computeAverage=0 → scalars=0
        mockResolution();
        mockSprintSetup();
        when(scrumGradeRepository.findByGroupIdAndSprintId(any(), any()))
                .thenReturn(Optional.empty());
        when(deliverableSubmissionRepository
                .findFirstByGroupAndDeliverableOrderBySubmittedAtDesc(group, deliverable))
                .thenReturn(null);
        when(sprintTrackingLogRepository
                .findByGroupIdAndAssigneeGithubUsernameAndPrMergedTrue(GROUP_UUID, GITHUB_USER))
                .thenReturn(List.of());
        mockFinalGradeUpsert(Optional.empty());

        FinalGradeResponse response = service.calculateFinalGrade(STUDENT_ID);

        DeliverableBreakdown bd = response.getDeliverableBreakdown().get(0);
        assertThat(bd.getScrumScalar()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bd.getReviewScalar()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bd.getDeliverableScalar()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── C_i calculation ───────────────────────────────────────────────────────

    @Test
    void completionRatio_computed_correctly() {
        // completedSP=15, targetSP=10+10=20 → C_i=0.75
        mockResolution();
        mockSprintSetup();
        when(scrumGradeRepository.findByGroupIdAndSprintId(any(), any()))
                .thenReturn(Optional.empty());
        when(deliverableSubmissionRepository
                .findFirstByGroupAndDeliverableOrderBySubmittedAtDesc(group, deliverable))
                .thenReturn(null);
        SprintTrackingLog log = new SprintTrackingLog();
        log.setStoryPoints(15);
        when(sprintTrackingLogRepository
                .findByGroupIdAndAssigneeGithubUsernameAndPrMergedTrue(GROUP_UUID, GITHUB_USER))
                .thenReturn(List.of(log));
        mockFinalGradeUpsert(Optional.empty());

        FinalGradeResponse response = service.calculateFinalGrade(STUDENT_ID);

        assertThat(response.getCompletionRatio()).isEqualByComparingTo(new BigDecimal("0.75"));
    }

    @Test
    void completionRatio_targetSPZero_returnsZero() {
        // Sprint with null storyPointTarget → targetSP=0 → C_i=0, no division by zero
        student.setGithubUsername(null); // skip tracking log call
        mockResolution();

        Sprint nullTargetSprint = new Sprint();
        nullTargetSprint.setId(UUID.randomUUID());
        nullTargetSprint.setStartDate(LocalDate.now());
        nullTargetSprint.setEndDate(LocalDate.now().plusDays(7));
        nullTargetSprint.setStoryPointTarget(null);

        when(deliverableRepository.findAll()).thenReturn(List.of(deliverable));
        when(sprintRepository.findAll()).thenReturn(List.of(nullTargetSprint));
        when(sprintDeliverableMappingRepository.findAllByDeliverableId(DELIVERABLE_UUID))
                .thenReturn(List.of());
        when(deliverableSubmissionRepository
                .findFirstByGroupAndDeliverableOrderBySubmittedAtDesc(group, deliverable))
                .thenReturn(null);
        mockFinalGradeUpsert(Optional.empty());

        FinalGradeResponse response = service.calculateFinalGrade(STUDENT_ID);

        assertThat(response.getCompletionRatio()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void nullGithubUsername_completionRatioZero() {
        // student.githubUsername=null → tracking repo must NOT be called → completedSP=0 → C_i=0
        student.setGithubUsername(null);
        mockResolution();
        mockSprintSetup();
        when(scrumGradeRepository.findByGroupIdAndSprintId(any(), any()))
                .thenReturn(Optional.empty());
        when(deliverableSubmissionRepository
                .findFirstByGroupAndDeliverableOrderBySubmittedAtDesc(group, deliverable))
                .thenReturn(null);
        mockFinalGradeUpsert(Optional.empty());

        FinalGradeResponse response = service.calculateFinalGrade(STUDENT_ID);

        assertThat(response.getCompletionRatio()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(sprintTrackingLogRepository, never())
                .findByGroupIdAndAssigneeGithubUsernameAndPrMergedTrue(any(), any());
    }

    // ── Upsert behavior ───────────────────────────────────────────────────────

    @Test
    void upsert_newGrade_savesNewEntity() {
        mockResolution();
        mockSprintSetup();
        when(scrumGradeRepository.findByGroupIdAndSprintId(any(), any()))
                .thenReturn(Optional.empty());
        when(deliverableSubmissionRepository
                .findFirstByGroupAndDeliverableOrderBySubmittedAtDesc(group, deliverable))
                .thenReturn(null);
        when(sprintTrackingLogRepository
                .findByGroupIdAndAssigneeGithubUsernameAndPrMergedTrue(GROUP_UUID, GITHUB_USER))
                .thenReturn(List.of());
        mockFinalGradeUpsert(Optional.empty()); // no existing → new entity

        service.calculateFinalGrade(STUDENT_ID);

        ArgumentCaptor<FinalGrade> captor = ArgumentCaptor.forClass(FinalGrade.class);
        verify(finalGradeRepository).save(captor.capture());
        FinalGrade saved = captor.getValue();
        assertThat(saved.getStudent()).isEqualTo(student);
        assertThat(saved.getGroup()).isEqualTo(group);
        assertThat(saved.getTermId()).isEqualTo(TERM_ID);
        assertThat(saved.getCalculatedAt()).isNotNull();
    }

    @Test
    void upsert_existingGrade_updatesInPlace() {
        mockResolution();
        mockSprintSetup();
        when(scrumGradeRepository.findByGroupIdAndSprintId(any(), any()))
                .thenReturn(Optional.empty());
        when(deliverableSubmissionRepository
                .findFirstByGroupAndDeliverableOrderBySubmittedAtDesc(group, deliverable))
                .thenReturn(null);
        when(sprintTrackingLogRepository
                .findByGroupIdAndAssigneeGithubUsernameAndPrMergedTrue(GROUP_UUID, GITHUB_USER))
                .thenReturn(List.of());

        FinalGrade existing = new FinalGrade();
        existing.setStudent(student);
        existing.setGroup(group);
        existing.setTermId(TERM_ID);
        mockFinalGradeUpsert(Optional.of(existing)); // return existing entity

        service.calculateFinalGrade(STUDENT_ID);

        ArgumentCaptor<FinalGrade> captor = ArgumentCaptor.forClass(FinalGrade.class);
        verify(finalGradeRepository).save(captor.capture());
        // same instance mutated, not a new object
        assertThat(captor.getValue()).isSameAs(existing);
    }

    // ── getStoredFinalGrade ───────────────────────────────────────────────────

    @Test
    void getStoredGrade_found_returnsEmptyBreakdown() {
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);

        FinalGrade stored = new FinalGrade();
        stored.setStudent(student);
        stored.setGroup(group);
        stored.setTermId(TERM_ID);
        stored.setWeightedTotal(new BigDecimal("21.6000"));
        stored.setCompletionRatio(new BigDecimal("0.7500"));
        stored.setFinalGrade(new BigDecimal("16.2000"));
        stored.setCalculatedAt(LocalDateTime.now());
        when(finalGradeRepository.findByStudent_StudentIdAndTermId(STUDENT_ID, TERM_ID))
                .thenReturn(Optional.of(stored));

        FinalGradeResponse response = service.getStoredFinalGrade(STUDENT_ID);

        assertThat(response.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(response.getDeliverableBreakdown()).isEmpty();
        assertThat(response.getFinalGrade()).isEqualByComparingTo(new BigDecimal("16.2"));
        assertThat(response.getCompletionRatio()).isEqualByComparingTo(new BigDecimal("0.75"));
    }

    @Test
    void getStoredGrade_notFound_throwsNotFoundException() {
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(finalGradeRepository.findByStudent_StudentIdAndTermId(STUDENT_ID, TERM_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStoredFinalGrade(STUDENT_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void mockResolution() {
        when(studentRepository.findByStudentId(STUDENT_ID)).thenReturn(Optional.of(student));
        when(groupMembershipRepository.findByStudentId(STUDENT_UUID))
                .thenReturn(Optional.of(membership));
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
    }

    private void mockSprintSetup() {
        when(deliverableRepository.findAll()).thenReturn(List.of(deliverable));
        when(sprintRepository.findAll()).thenReturn(List.of(sprint1, sprint2));
        when(sprintDeliverableMappingRepository.findAllByDeliverableId(DELIVERABLE_UUID))
                .thenReturn(List.of(mapping1, mapping2));
    }

    /** Default stub: no ScrumGrade for any group+sprint combination. */
    private void mockNoScrumGrades() {
        when(scrumGradeRepository.findByGroupIdAndSprintId(any(), any()))
                .thenReturn(Optional.empty());
    }

    private void mockFinalGradeUpsert(Optional<FinalGrade> existing) {
        when(finalGradeRepository.findByStudent_StudentIdAndTermId(STUDENT_ID, TERM_ID))
                .thenReturn(existing);
        when(finalGradeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ScrumGrade makeScrumGrade(Sprint sprint, ScrumGradeValue pointA, ScrumGradeValue pointB) {
        ScrumGrade g = new ScrumGrade();
        g.setGroup(group);
        g.setSprint(sprint);
        g.setPointAGrade(pointA);
        g.setPointBGrade(pointB);
        g.setGradedAt(LocalDateTime.now());
        return g;
    }

    private RubricCriterion makeCriterion(UUID id, RubricCriterion.GradingType type, BigDecimal weight) {
        RubricCriterion c = new RubricCriterion();
        c.setId(id);
        c.setDeliverable(deliverable);
        c.setGradingType(type);
        c.setWeight(weight);
        c.setCriterionName("Criterion");
        return c;
    }

    private RubricGrade makeRubricGrade(DeliverableSubmission submission,
                                         RubricCriterion criterion,
                                         StaffUser reviewer,
                                         String selectedGrade) {
        RubricGrade rg = new RubricGrade();
        rg.setSubmission(submission);
        rg.setCriterion(criterion);
        rg.setReviewer(reviewer);
        rg.setSelectedGrade(selectedGrade);
        rg.setGradedAt(LocalDateTime.now());
        return rg;
    }
}
