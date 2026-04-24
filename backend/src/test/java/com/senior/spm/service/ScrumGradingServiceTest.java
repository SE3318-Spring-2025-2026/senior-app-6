package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.senior.spm.controller.request.ScrumGradeRequest;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ScrumGrade;
import com.senior.spm.entity.ScrumGrade.ScrumGradeValue;
import com.senior.spm.entity.Sprint;
import com.senior.spm.entity.SprintTrackingLog;
import com.senior.spm.entity.SprintTrackingLog.AiValidationResult;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.GroupNotFoundException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.ScrumGradeRepository;
import com.senior.spm.repository.SprintRepository;
import com.senior.spm.repository.SprintTrackingLogRepository;

@ExtendWith(MockitoExtension.class)
class ScrumGradingServiceTest {

    @Mock SprintRepository sprintRepository;
    @Mock ProjectGroupRepository projectGroupRepository;
    @Mock ScrumGradeRepository scrumGradeRepository;
    @Mock SprintTrackingLogRepository sprintTrackingLogRepository;
    @Mock GroupMembershipRepository groupMembershipRepository;
    @Mock TermConfigService termConfigService;

    @InjectMocks
    ScrumGradingService service;

    private static final UUID ADVISOR_ID       = UUID.randomUUID();
    private static final UUID OTHER_ADVISOR_ID = UUID.randomUUID();
    private static final UUID GROUP_ID         = UUID.randomUUID();
    private static final UUID SPRINT_ID        = UUID.randomUUID();
    private static final UUID STUDENT_ID       = UUID.randomUUID();
    private static final String TERM_ID        = "2024-FALL";

    private StaffUser advisor;
    private ProjectGroup group;
    private Sprint sprint;

    @BeforeEach
    void setUp() {
        advisor = new StaffUser();
        advisor.setId(ADVISOR_ID);

        group = new ProjectGroup();
        group.setId(GROUP_ID);
        group.setGroupName("TeamAlpha");
        group.setTermId(TERM_ID);
        group.setStatus(ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        group.setCreatedAt(LocalDateTime.now());
        group.setVersion(0L);
        group.setAdvisor(advisor);

        sprint = new Sprint();
        sprint.setId(SPRINT_ID);
        sprint.setStartDate(LocalDate.now().minusDays(5));
        sprint.setEndDate(LocalDate.now().plusDays(9));
        sprint.setStoryPointTarget(40);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getActiveSprint
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getActiveSprint_sprintExists_returnsResponse() {
        when(sprintRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(
                any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(sprint));

        var response = service.getActiveSprint();

        assertThat(response.getSprintId()).isEqualTo(SPRINT_ID);
        assertThat(response.getStartDate()).isEqualTo(sprint.getStartDate());
        assertThat(response.getEndDate()).isEqualTo(sprint.getEndDate());
        assertThat(response.getStoryPointTarget()).isEqualTo(40);
        assertThat(response.getDaysRemaining()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void getActiveSprint_noSprint_throwsNotFoundException() {
        when(sprintRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(
                any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getActiveSprint())
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No active sprint");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  submitGrade
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void submitGrade_newGrade_createsWithNullUpdatedAt() {
        ScrumGradeRequest request = new ScrumGradeRequest();
        request.setPointAGrade(ScrumGradeValue.A);
        request.setPointBGrade(ScrumGradeValue.B);

        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));
        when(scrumGradeRepository.findByGroupIdAndSprintId(GROUP_ID, SPRINT_ID)).thenReturn(Optional.empty());
        when(scrumGradeRepository.save(any(ScrumGrade.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrumGrade result = service.submitGrade(ADVISOR_ID, GROUP_ID, SPRINT_ID, request);

        assertThat(result.getUpdatedAt()).isNull();
        assertThat(result.getGradedAt()).isNotNull();
        assertThat(result.getPointAGrade()).isEqualTo(ScrumGradeValue.A);
        assertThat(result.getPointBGrade()).isEqualTo(ScrumGradeValue.B);
        assertThat(result.getAdvisor()).isEqualTo(advisor);
    }

    @Test
    void submitGrade_existingGrade_setsUpdatedAt() {
        ScrumGradeRequest request = new ScrumGradeRequest();
        request.setPointAGrade(ScrumGradeValue.C);
        request.setPointBGrade(ScrumGradeValue.D);

        ScrumGrade existing = new ScrumGrade();
        existing.setGroup(group);
        existing.setSprint(sprint);
        existing.setAdvisor(advisor);
        existing.setPointAGrade(ScrumGradeValue.A);
        existing.setPointBGrade(ScrumGradeValue.A);
        existing.setGradedAt(LocalDateTime.now().minusDays(1));

        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));
        when(scrumGradeRepository.findByGroupIdAndSprintId(GROUP_ID, SPRINT_ID))
                .thenReturn(Optional.of(existing));
        when(scrumGradeRepository.save(any(ScrumGrade.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrumGrade result = service.submitGrade(ADVISOR_ID, GROUP_ID, SPRINT_ID, request);

        assertThat(result.getUpdatedAt()).isNotNull();
        assertThat(result.getPointAGrade()).isEqualTo(ScrumGradeValue.C);
        assertThat(result.getPointBGrade()).isEqualTo(ScrumGradeValue.D);
    }

    @Test
    void submitGrade_groupNotFound_throwsGroupNotFoundException() {
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitGrade(ADVISOR_ID, GROUP_ID, SPRINT_ID, new ScrumGradeRequest()))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void submitGrade_sprintNotFound_throwsNotFoundException() {
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitGrade(ADVISOR_ID, GROUP_ID, SPRINT_ID, new ScrumGradeRequest()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Sprint not found");
    }

    @Test
    void submitGrade_notAdvisor_throwsForbiddenException() {
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> service.submitGrade(OTHER_ADVISOR_ID, GROUP_ID, SPRINT_ID, new ScrumGradeRequest()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void submitGrade_groupHasNoAdvisor_throwsForbiddenException() {
        group.setAdvisor(null);
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> service.submitGrade(ADVISOR_ID, GROUP_ID, SPRINT_ID, new ScrumGradeRequest()))
                .isInstanceOf(ForbiddenException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getGrade
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getGrade_found_returnsGrade() {
        ScrumGrade grade = new ScrumGrade();
        grade.setGroup(group);
        grade.setSprint(sprint);
        grade.setAdvisor(advisor);
        grade.setPointAGrade(ScrumGradeValue.B);
        grade.setPointBGrade(ScrumGradeValue.C);
        grade.setGradedAt(LocalDateTime.now());

        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));
        when(scrumGradeRepository.findByGroupIdAndSprintId(GROUP_ID, SPRINT_ID))
                .thenReturn(Optional.of(grade));

        ScrumGrade result = service.getGrade(ADVISOR_ID, GROUP_ID, SPRINT_ID);

        assertThat(result.getPointAGrade()).isEqualTo(ScrumGradeValue.B);
    }

    @Test
    void getGrade_notFound_throwsNotFoundException() {
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));
        when(scrumGradeRepository.findByGroupIdAndSprintId(GROUP_ID, SPRINT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getGrade(ADVISOR_ID, GROUP_ID, SPRINT_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No grade submitted yet");
    }

    @Test
    void getGrade_notAdvisor_throwsForbiddenException() {
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> service.getGrade(OTHER_ADVISOR_ID, GROUP_ID, SPRINT_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getAdvisorGroupSummaries
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getAdvisorGroupSummaries_noGroups_returnsEmptyList() {
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(projectGroupRepository.findByAdvisor_IdAndTermId(ADVISOR_ID, TERM_ID))
                .thenReturn(List.of());

        var result = service.getAdvisorGroupSummaries(ADVISOR_ID, SPRINT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getAdvisorGroupSummaries_twoGroups_returnsCorrectCounts() {
        ProjectGroup group2 = new ProjectGroup();
        group2.setId(UUID.randomUUID());
        group2.setGroupName("TeamBeta");
        group2.setTermId(TERM_ID);
        group2.setCreatedAt(LocalDateTime.now());
        group2.setVersion(0L);

        SprintTrackingLog log = makeLog("SPM-1", "alice", AiValidationResult.FAIL, AiValidationResult.PASS, true);

        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(projectGroupRepository.findByAdvisor_IdAndTermId(ADVISOR_ID, TERM_ID))
                .thenReturn(List.of(group, group2));
        when(sprintTrackingLogRepository.findByGroupIdAndSprintId(GROUP_ID, SPRINT_ID))
                .thenReturn(List.of(log));
        when(sprintTrackingLogRepository.findByGroupIdAndSprintId(group2.getId(), SPRINT_ID))
                .thenReturn(List.of());
        when(scrumGradeRepository.findByGroupIdAndSprintId(GROUP_ID, SPRINT_ID))
                .thenReturn(Optional.empty());
        when(scrumGradeRepository.findByGroupIdAndSprintId(group2.getId(), SPRINT_ID))
                .thenReturn(Optional.empty());

        var result = service.getAdvisorGroupSummaries(ADVISOR_ID, SPRINT_ID);

        assertThat(result).hasSize(2);
        var summary = result.stream().filter(s -> s.getGroupId().equals(GROUP_ID)).findFirst().orElseThrow();
        assertThat(summary.getTotalIssues()).isEqualTo(1);
        assertThat(summary.getMergedPRs()).isEqualTo(1);
        assertThat(summary.getAiFailCount()).isEqualTo(1);
        assertThat(summary.isGradeSubmitted()).isFalse();
    }

    @Test
    void getAdvisorGroupSummaries_sprintNotFound_throwsNotFoundException() {
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAdvisorGroupSummaries(ADVISOR_ID, SPRINT_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getAdvisorGroupTracking
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getAdvisorGroupTracking_logsExist_returnsResponse() {
        SprintTrackingLog log = makeLog("SPM-1", "alice", AiValidationResult.PASS, AiValidationResult.PASS, true);

        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));
        when(sprintTrackingLogRepository.findByGroupIdAndSprintId(GROUP_ID, SPRINT_ID))
                .thenReturn(List.of(log));

        var response = service.getAdvisorGroupTracking(ADVISOR_ID, GROUP_ID, SPRINT_ID);

        assertThat(response.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(response.getSprintId()).isEqualTo(SPRINT_ID);
        assertThat(response.getIssues()).hasSize(1);
        assertThat(response.getIssues().get(0).getIssueKey()).isEqualTo("SPM-1");
        assertThat(response.getPerStudentSummary()).isNotNull();
        assertThat(response.getPerStudentSummary()).hasSize(1);
    }

    @Test
    void getAdvisorGroupTracking_noLogs_throwsNotFoundException() {
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));
        when(sprintTrackingLogRepository.findByGroupIdAndSprintId(GROUP_ID, SPRINT_ID))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getAdvisorGroupTracking(ADVISOR_ID, GROUP_ID, SPRINT_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("sprint may not have been processed");
    }

    @Test
    void getAdvisorGroupTracking_notAdvisor_throwsForbiddenException() {
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> service.getAdvisorGroupTracking(OTHER_ADVISOR_ID, GROUP_ID, SPRINT_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getStudentGroupTracking
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getStudentGroupTracking_memberNoLogs_returns200WithEmptyIssues() {
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(new GroupMembership()));
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));
        when(sprintTrackingLogRepository.findByGroupIdAndSprintId(GROUP_ID, SPRINT_ID))
                .thenReturn(List.of());

        var response = service.getStudentGroupTracking(STUDENT_ID, GROUP_ID, SPRINT_ID);

        assertThat(response.getIssues()).isEmpty();
        assertThat(response.getPerStudentSummary()).isNull();
    }

    @Test
    void getStudentGroupTracking_memberWithLogs_returnsIssues() {
        SprintTrackingLog log = makeLog("SPM-5", "alice", AiValidationResult.PASS, AiValidationResult.PASS, false);

        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(new GroupMembership()));
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));
        when(sprintTrackingLogRepository.findByGroupIdAndSprintId(GROUP_ID, SPRINT_ID))
                .thenReturn(List.of(log));

        var response = service.getStudentGroupTracking(STUDENT_ID, GROUP_ID, SPRINT_ID);

        assertThat(response.getIssues()).hasSize(1);
        assertThat(response.getIssues().get(0).getIssueKey()).isEqualTo("SPM-5");
    }

    @Test
    void getStudentGroupTracking_nonMember_throwsForbiddenException() {
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStudentGroupTracking(STUDENT_ID, GROUP_ID, SPRINT_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  AI priority logic (via getAdvisorGroupTracking → buildPerStudentSummary)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void aiPriority_failDominatesWorstResult() {
        SprintTrackingLog pass = makeLog("SPM-1", "alice", AiValidationResult.PASS, AiValidationResult.PASS, false);
        SprintTrackingLog warn = makeLog("SPM-2", "alice", AiValidationResult.WARN, AiValidationResult.PASS, false);
        SprintTrackingLog fail = makeLog("SPM-3", "alice", AiValidationResult.FAIL, AiValidationResult.PASS, false);

        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));
        when(sprintTrackingLogRepository.findByGroupIdAndSprintId(GROUP_ID, SPRINT_ID))
                .thenReturn(List.of(pass, warn, fail));

        var response = service.getAdvisorGroupTracking(ADVISOR_ID, GROUP_ID, SPRINT_ID);

        var aliceSummary = response.getPerStudentSummary().stream()
                .filter(s -> "alice".equals(s.getAssigneeGithubUsername()))
                .findFirst().orElseThrow();
        assertThat(aliceSummary.getAiValidationStatus()).isEqualTo(AiValidationResult.FAIL);
    }

    @Test
    void aiPriority_warnBeatsSkipped() {
        SprintTrackingLog log = makeLog("SPM-1", "bob", AiValidationResult.WARN, AiValidationResult.SKIPPED, false);

        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));
        when(sprintTrackingLogRepository.findByGroupIdAndSprintId(GROUP_ID, SPRINT_ID))
                .thenReturn(List.of(log));

        var response = service.getAdvisorGroupTracking(ADVISOR_ID, GROUP_ID, SPRINT_ID);

        var bobSummary = response.getPerStudentSummary().get(0);
        assertThat(bobSummary.getAiValidationStatus()).isEqualTo(AiValidationResult.WARN);
    }

    @Test
    void aiPriority_nullResultsDefaultToPending() {
        SprintTrackingLog log = makeLog("SPM-1", "charlie", null, null, false);

        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));
        when(sprintTrackingLogRepository.findByGroupIdAndSprintId(GROUP_ID, SPRINT_ID))
                .thenReturn(List.of(log));

        var response = service.getAdvisorGroupTracking(ADVISOR_ID, GROUP_ID, SPRINT_ID);

        var charlieSummary = response.getPerStudentSummary().get(0);
        assertThat(charlieSummary.getAiValidationStatus()).isEqualTo(AiValidationResult.PENDING);
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private SprintTrackingLog makeLog(String issueKey, String assignee,
            AiValidationResult aiPr, AiValidationResult aiDiff, boolean prMerged) {
        SprintTrackingLog log = new SprintTrackingLog();
        log.setIssueKey(issueKey);
        log.setAssigneeGithubUsername(assignee);
        log.setAiPrResult(aiPr);
        log.setAiDiffResult(aiDiff);
        log.setPrMerged(prMerged);
        log.setStoryPoints(3);
        log.setFetchedAt(LocalDateTime.now());
        return log;
    }
}
