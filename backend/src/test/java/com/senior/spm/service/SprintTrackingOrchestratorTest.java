package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.Sprint;
import com.senior.spm.entity.SprintTrackingLog;
import com.senior.spm.entity.SprintTrackingLog.AiValidationResult;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.SprintRepository;
import com.senior.spm.repository.SprintTrackingLogRepository;
import com.senior.spm.service.dto.JiraIssueDto;
import com.senior.spm.service.dto.SprintRefreshStats;

/**
 * Unit tests for {@link SprintTrackingOrchestrator}.
 *
 * <p>Self-proxy design note: {@code SprintTrackingOrchestrator.self} is set to the
 * real service instance via {@link ReflectionTestUtils} in {@code setUp()}, mirroring
 * what Spring's {@code @Lazy @Autowired} does at runtime. This is required so that
 * calls to {@code self.processGroup()} invoke the real method rather than a missing
 * proxy.
 *
 * <p>LENIENT strictness is used because the {@code termConfigService} stub in
 * {@code @BeforeEach} is not consumed by guard tests that throw before reaching it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SprintTrackingOrchestratorTest {

    @Mock private SprintRepository             sprintRepository;
    @Mock private ProjectGroupRepository       projectGroupRepository;
    @Mock private SprintTrackingLogRepository  sprintTrackingLogRepository;
    @Mock private JiraSprintService            jiraSprintService;
    @Mock private GithubSprintService          githubSprintService;
    @Mock private AiValidationService          aiValidationService;
    @Mock private TermConfigService            termConfigService;

    @InjectMocks
    private SprintTrackingOrchestrator orchestrator;

    private static final String TERM_ID = "2025-SPRING";

    @BeforeEach
    void setUp() {
        // Wire the self-proxy field the same way Spring would at runtime
        ReflectionTestUtils.setField(orchestrator, "self", orchestrator);
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Sprint sprintEnded(LocalDate endDate) {
        Sprint s = new Sprint();
        ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
        s.setStartDate(endDate.minusDays(14));
        s.setEndDate(endDate);
        return s;
    }

    private ProjectGroup group(GroupStatus status) {
        ProjectGroup g = new ProjectGroup();
        ReflectionTestUtils.setField(g, "id", UUID.randomUUID());
        g.setStatus(status);
        return g;
    }

    private JiraIssueDto issue(String key) {
        return new JiraIssueDto(key, "dev@example.com", 3, "description");
    }

    // ── triggerForSprint guard tests ──────────────────────────────────────────

    @Nested
    @DisplayName("triggerForSprint — guard conditions")
    class TriggerGuards {

        @Test
        @DisplayName("throws NotFoundException when sprint does not exist")
        void unknownSprint() {
            UUID id = UUID.randomUUID();
            when(sprintRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orchestrator.triggerForSprint(id, false))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("throws BusinessRuleException when sprint has not ended and force=false")
        void sprintNotEnded_noForce() {
            Sprint future = sprintEnded(LocalDate.now(ZoneId.of("UTC")).plusDays(3));
            when(sprintRepository.findById(future.getId())).thenReturn(Optional.of(future));

            assertThatThrownBy(() -> orchestrator.triggerForSprint(future.getId(), false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("force=true");
        }

        @Test
        @DisplayName("proceeds when sprint has not ended but force=true")
        void sprintNotEnded_withForce() {
            Sprint future = sprintEnded(LocalDate.now(ZoneId.of("UTC")).plusDays(3));
            when(sprintRepository.findById(future.getId())).thenReturn(Optional.of(future));
            when(projectGroupRepository.findByTermIdAndStatusIn(eq(TERM_ID), any()))
                    .thenReturn(Collections.emptyList());

            SprintRefreshStats stats = orchestrator.triggerForSprint(future.getId(), true);

            assertThat(stats.groupsProcessed()).isZero();
        }

        @Test
        @DisplayName("does NOT call deleteBySprintId — idempotency is per-group inside processGroup")
        void noOuterBulkDelete() {
            Sprint ended = sprintEnded(LocalDate.now(ZoneId.of("UTC")).minusDays(1));
            when(sprintRepository.findById(ended.getId())).thenReturn(Optional.of(ended));
            when(projectGroupRepository.findByTermIdAndStatusIn(eq(TERM_ID), any()))
                    .thenReturn(Collections.emptyList());

            orchestrator.triggerForSprint(ended.getId(), false);

            verify(sprintTrackingLogRepository, never()).deleteBySprintId(any());
        }
    }

    // ── per-group isolation (AC item 3) ───────────────────────────────────────

    @Nested
    @DisplayName("per-group isolation — failing group must not block others")
    class GroupIsolation {

        @Test
        @DisplayName("group 2 JIRA failure does not prevent groups 1 and 3 from being tracked")
        void jiraFailureOnGroup2_doesNotBlockOthers() {
            Sprint ended = sprintEnded(LocalDate.now(ZoneId.of("UTC")).minusDays(1));
            when(sprintRepository.findById(ended.getId())).thenReturn(Optional.of(ended));

            ProjectGroup g1 = group(GroupStatus.TOOLS_BOUND);
            ProjectGroup g2 = group(GroupStatus.ADVISOR_ASSIGNED);
            ProjectGroup g3 = group(GroupStatus.TOOLS_BOUND);

            when(projectGroupRepository.findByTermIdAndStatusIn(eq(TERM_ID), any()))
                    .thenReturn(List.of(g1, g2, g3));

            // g1 and g3 return one issue each; g2 throws
            when(jiraSprintService.fetchSprintStories(g1))
                    .thenReturn(List.of(issue("SPM-1")));
            when(jiraSprintService.fetchSprintStories(g2))
                    .thenThrow(new RuntimeException("JIRA auth failure for group 2"));
            when(jiraSprintService.fetchSprintStories(g3))
                    .thenReturn(List.of(issue("SPM-3")));

            // GitHub returns empty for all — no branch found, AI skipped
            when(githubSprintService.findBranchByIssueKey(any(), any()))
                    .thenReturn(Optional.empty());

            // saveAll echoes the argument back
            when(sprintTrackingLogRepository.saveAll(any()))
                    .thenAnswer(inv -> inv.getArgument(0));

            SprintRefreshStats stats = orchestrator.triggerForSprint(ended.getId(), false);

            // g1 and g3 processed successfully; g2 skipped due to exception
            assertThat(stats.groupsProcessed()).isEqualTo(2);
            assertThat(stats.issuesFetched()).isEqualTo(2);

            // processGroup calls saveAll twice per group (initial persist + post-GitHub update)
            // g2 threw before saveAll was reached → 2 groups × 2 calls = 4 total
            verify(sprintTrackingLogRepository, times(4)).saveAll(any());

            // JIRA was attempted for all three groups — failure on g2 did not abort the loop
            verify(jiraSprintService, times(1)).fetchSprintStories(g1);
            verify(jiraSprintService, times(1)).fetchSprintStories(g2);
            verify(jiraSprintService, times(1)).fetchSprintStories(g3);
        }

        @Test
        @DisplayName("rows for groups 1 and 3 have correct issueKey and SKIPPED AI (no branch found)")
        void savedLogsHaveCorrectFinalState() {
            Sprint ended = sprintEnded(LocalDate.now(ZoneId.of("UTC")).minusDays(1));
            when(sprintRepository.findById(ended.getId())).thenReturn(Optional.of(ended));

            ProjectGroup g1 = group(GroupStatus.TOOLS_BOUND);
            ProjectGroup g3 = group(GroupStatus.ADVISOR_ASSIGNED);

            when(projectGroupRepository.findByTermIdAndStatusIn(eq(TERM_ID), any()))
                    .thenReturn(List.of(g1, g3));

            when(jiraSprintService.fetchSprintStories(g1))
                    .thenReturn(List.of(issue("SPM-10")));
            when(jiraSprintService.fetchSprintStories(g3))
                    .thenReturn(List.of(issue("SPM-30")));

            // No branch found → AI results set to SKIPPED
            when(githubSprintService.findBranchByIssueKey(any(), any()))
                    .thenReturn(Optional.empty());

            // saveAll echoes argument back; the list is mutated in place between the two
            // calls (initial persist → post-GitHub update), so we capture the final state
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<SprintTrackingLog>> captor =
                    ArgumentCaptor.forClass(List.class);
            when(sprintTrackingLogRepository.saveAll(captor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));

            orchestrator.triggerForSprint(ended.getId(), false);

            // 2 groups × 2 saveAll calls each = 4 total captures
            List<List<SprintTrackingLog>> allSaved = captor.getAllValues();
            assertThat(allSaved).hasSize(4);

            // Captures: [g1-initial, g1-final, g3-initial, g3-final]
            // The list object is reused, so both g1 captures reference the same instance
            // — check the final (index 1 and 3) which reflect post-GitHub state
            SprintTrackingLog log1 = allSaved.get(1).get(0);
            assertThat(log1.getIssueKey()).isEqualTo("SPM-10");
            assertThat(log1.getAiPrResult()).isEqualTo(AiValidationResult.SKIPPED);
            assertThat(log1.getAiDiffResult()).isEqualTo(AiValidationResult.SKIPPED);

            SprintTrackingLog log3 = allSaved.get(3).get(0);
            assertThat(log3.getIssueKey()).isEqualTo("SPM-30");
            assertThat(log3.getAiPrResult()).isEqualTo(AiValidationResult.SKIPPED);
            assertThat(log3.getAiDiffResult()).isEqualTo(AiValidationResult.SKIPPED);
        }
    }
}
