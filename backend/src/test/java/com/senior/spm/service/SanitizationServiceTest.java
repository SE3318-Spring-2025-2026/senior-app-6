package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import com.senior.spm.entity.AdvisorRequest.RequestStatus;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.entity.ScheduleWindow.WindowType;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.ScheduleWindowRepository;

/**
 * SanitizationServiceTest — verifies correct behavior of SanitizationService.
 *
 * <p>Tests are grouped by topic. Bug-regression tests (Bug1–Bug5 suites) document
 * what was broken in PR #121 and prove the current implementation is correct.
 * "Happy path" tests verify the core contract directly.
 *
 * <p>Self-proxy design note: {@code SanitizationService.self} is set to the real
 * service instance via {@code ReflectionTestUtils} in {@code setUp()}, mirroring
 * what Spring's {@code @Lazy @Autowired} does at runtime.
 */
@ExtendWith(MockitoExtension.class)
class SanitizationServiceTest {

    @Mock private ScheduleWindowRepository  scheduleWindowRepo;
    @Mock private ProjectGroupRepository    projectGroupRepo;
    @Mock private GroupMembershipRepository groupMembershipRepo;
    @Mock private AdvisorRequestRepository  advisorRequestRepo;

    @InjectMocks
    private SanitizationService sanitizationService;

    // ── shared fixtures ───────────────────────────────────────────────────────

    private static final String TERM_2024 = "2024-FALL";
    private static final String TERM_2025 = "2025-SPRING";

    private ProjectGroup group1, group2, group3;
    private ScheduleWindow window2024, window2025;

    @BeforeEach
    void setUp() {
        group1 = makeGroup("Alpha", TERM_2024, GroupStatus.FORMING);
        group2 = makeGroup("Beta",  TERM_2024, GroupStatus.TOOLS_PENDING);
        group3 = makeGroup("Gamma", TERM_2024, GroupStatus.TOOLS_BOUND);

        window2024 = makeWindow(TERM_2024, LocalDateTime.now().minusDays(1));
        window2025 = makeWindow(TERM_2025, LocalDateTime.now().minusDays(2));

        // Wire the self-proxy field the same way Spring would at runtime.
        // Without this, scheduler calls to self.runSanitization() / self.disbandGroup()
        // would throw NullPointerException.
        ReflectionTestUtils.setField(sanitizationService, "self", sanitizationService);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // BUG 1 regression — Self-invocation / atomicity
    // Fix: scheduler calls self.runSanitization() through self-proxy
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Bug1 regression — atomicity: deleteByGroupId or bulkUpdate failure propagates out of disbandGroup")
    class Bug1_Regression_Atomicity {

        /**
         * With {@code REQUIRES_NEW} on {@code disbandGroup}, any RuntimeException thrown
         * by {@code deleteByGroupId} propagates out of the per-group transaction boundary.
         * In production Hibernate, the REQUIRES_NEW transaction is rolled back before the
         * exception surfaces — so {@code save(group)} is also rolled back.
         *
         * <p>In mock context there is no real transaction, so we verify the call sequence:
         * save was called, delete threw, bulkUpdate was never reached, and the scheduler
         * absorbed the exception (now with a log.error).
         */
        @Test
        @DisplayName("deleteByGroupId failure: save emitted, bulkUpdate skipped, scheduler absorbs — REQUIRES_NEW rolls back save in production")
        void deleteFailure_saveEmitted_bulkUpdateNeverCalled_schedulerAbsorbs() {
            when(scheduleWindowRepo.findByTypeAndClosesAtLessThan(eq(WindowType.ADVISOR_ASSOCIATION), any()))
                .thenReturn(List.of(window2024));
            when(projectGroupRepo.findByTermIdAndStatusInAndAdvisorIsNull(eq(TERM_2024), any()))
                .thenReturn(List.of(group1));
            when(projectGroupRepo.save(group1)).thenReturn(group1);
            doThrow(new RuntimeException("simulated DB error on membership delete"))
                .when(groupMembershipRepo).deleteByGroupId(group1.getId());

            assertThatCode(() -> sanitizationService.runSanitizationIfWindowClosed())
                .doesNotThrowAnyException();

            verify(projectGroupRepo).save(group1);
            verify(groupMembershipRepo).deleteByGroupId(group1.getId());
            verify(advisorRequestRepo, never()).bulkUpdateStatusByGroupId(any(), any());
        }

        /**
         * Calling {@code runSanitization} directly (as a future coordinator controller would,
         * through Spring proxy) must propagate a RuntimeException from {@code disbandGroup}
         * so the transaction manager can roll back REQUIRES_NEW.
         */
        @Test
        @DisplayName("Direct call: RuntimeException from disbandGroup propagates — transaction manager can roll back")
        void directCall_runtimeExceptionPropagates() {
            when(projectGroupRepo.findByTermIdAndStatusInAndAdvisorIsNull(eq(TERM_2024), any()))
                .thenReturn(List.of(group1));
            when(projectGroupRepo.save(group1)).thenReturn(group1);
            doThrow(new RuntimeException("simulated constraint violation"))
                .when(groupMembershipRepo).deleteByGroupId(group1.getId());

            assertThatThrownBy(() -> sanitizationService.runSanitization(TERM_2024, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated constraint violation");

            verify(projectGroupRepo).save(group1);
            verify(advisorRequestRepo, never()).bulkUpdateStatusByGroupId(any(), any());
        }

        /**
         * P3.6 atomicity: all three ops (save / deleteByGroupId / bulkUpdate) run in one
         * REQUIRES_NEW transaction. If bulkUpdate fails, save and delete are also rolled back.
         * In mock context: bulkUpdate is called and throws; scheduler absorbs the error.
         */
        @Test
        @DisplayName("bulkUpdateStatus failure: scheduler absorbs; REQUIRES_NEW rolls back all three ops in production")
        void bulkUpdateFailure_schedulerAbsorbs_requiresNewRollsBackAllInProduction() {
            when(scheduleWindowRepo.findByTypeAndClosesAtLessThan(eq(WindowType.ADVISOR_ASSOCIATION), any()))
                .thenReturn(List.of(window2024));
            when(projectGroupRepo.findByTermIdAndStatusInAndAdvisorIsNull(eq(TERM_2024), any()))
                .thenReturn(List.of(group1));
            when(projectGroupRepo.save(group1)).thenReturn(group1);
            doThrow(new RuntimeException("bulk update failed"))
                .when(advisorRequestRepo).bulkUpdateStatusByGroupId(any(), any());

            assertThatCode(() -> sanitizationService.runSanitizationIfWindowClosed())
                .doesNotThrowAnyException();

            verify(projectGroupRepo).save(group1);
            verify(groupMembershipRepo).deleteByGroupId(group1.getId());
            verify(advisorRequestRepo).bulkUpdateStatusByGroupId(
                eq(RequestStatus.AUTO_REJECTED), eq(group1.getId()));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // BUG 2 regression — REQUIRES_NEW isolates per-group transactions
    // Fix: disbandGroup has Propagation.REQUIRES_NEW
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Bug2 regression — OL isolation: REQUIRES_NEW ensures only the conflicting group is skipped")
    class Bug2_Regression_OLIsolation {

        /**
         * OL on group2: group2's REQUIRES_NEW transaction rolls back.
         * group1 and group3 each have their own committed transactions (REQUIRES_NEW).
         * In mock context this is demonstrated via call verification.
         */
        @Test
        @DisplayName("OL on group2: group1 and group3 fully processed; group2 memberships and requests untouched")
        void olOnGroup2_group1And3FullyProcessed_group2Skipped() {
            when(projectGroupRepo.findByTermIdAndStatusInAndAdvisorIsNull(eq(TERM_2024), any()))
                .thenReturn(List.of(group1, group2, group3));
            when(projectGroupRepo.save(group1)).thenReturn(group1);
            when(projectGroupRepo.save(group2))
                .thenThrow(new OptimisticLockingFailureException("version conflict on group2"));
            when(projectGroupRepo.save(group3)).thenReturn(group3);

            assertThatCode(() -> sanitizationService.runSanitization(TERM_2024, false))
                .doesNotThrowAnyException();

            // group1 fully processed
            verify(groupMembershipRepo).deleteByGroupId(group1.getId());
            verify(advisorRequestRepo).bulkUpdateStatusByGroupId(
                eq(RequestStatus.AUTO_REJECTED), eq(group1.getId()));

            // group2 skipped after OL
            verify(groupMembershipRepo, never()).deleteByGroupId(group2.getId());
            verify(advisorRequestRepo, never()).bulkUpdateStatusByGroupId(any(), eq(group2.getId()));

            // group3 fully processed
            verify(groupMembershipRepo).deleteByGroupId(group3.getId());
            verify(advisorRequestRepo).bulkUpdateStatusByGroupId(
                eq(RequestStatus.AUTO_REJECTED), eq(group3.getId()));
        }

        /**
         * Verifies the REQUIRES_NEW design via a spy on the service itself.
         * OL on group2 is caught; disbandGroup is called for group1 and group3.
         * In production, each completed disbandGroup call committed its own transaction.
         */
        @Test
        @DisplayName("REQUIRES_NEW design: disbandGroup called per group; OL on group2 caught; group1 and group3 dispatched")
        void requiresNew_disbandGroupCalledPerGroup_olOnGroup2Caught() {
            SanitizationService spyService = spy(sanitizationService);
            ReflectionTestUtils.setField(spyService, "self", spyService);

            when(projectGroupRepo.findByTermIdAndStatusInAndAdvisorIsNull(eq(TERM_2024), any()))
                .thenReturn(List.of(group1, group2, group3));
            doNothing().when(spyService).disbandGroup(group1);
            doThrow(new OptimisticLockingFailureException("OL on group2"))
                .when(spyService).disbandGroup(group2);
            doNothing().when(spyService).disbandGroup(group3);

            assertThatCode(() -> spyService.runSanitization(TERM_2024, false))
                .doesNotThrowAnyException();

            verify(spyService).disbandGroup(group1);
            verify(spyService).disbandGroup(group2);  // called but threw OL — was caught
            verify(spyService).disbandGroup(group3);  // still dispatched after group2 OL
        }

        /**
         * OL on first group: remaining groups are still dispatched.
         * In production, each has its own REQUIRES_NEW transaction.
         */
        @Test
        @DisplayName("OL on first group: group2 and group3 still dispatched and committed independently")
        void olOnFirstGroup_remainingGroupsStillProcessed() {
            when(projectGroupRepo.findByTermIdAndStatusInAndAdvisorIsNull(eq(TERM_2024), any()))
                .thenReturn(List.of(group1, group2, group3));
            when(projectGroupRepo.save(group1))
                .thenThrow(new OptimisticLockingFailureException("conflict on group1"));
            when(projectGroupRepo.save(group2)).thenReturn(group2);
            when(projectGroupRepo.save(group3)).thenReturn(group3);

            assertThatCode(() -> sanitizationService.runSanitization(TERM_2024, false))
                .doesNotThrowAnyException();

            verify(groupMembershipRepo, never()).deleteByGroupId(group1.getId());
            verify(groupMembershipRepo).deleteByGroupId(group2.getId());
            verify(groupMembershipRepo).deleteByGroupId(group3.getId());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // BUG 3 regression — scheduler no longer swallows silently
    // Fix: catch (Exception e) now calls log.error(...)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Bug3 regression — scheduler continues on per-term failure (log.error is called)")
    class Bug3_Regression_ExceptionHandling {

        @Test
        @DisplayName("Exception from runSanitization does not halt the scheduler loop")
        void exceptionFromRunSanitization_doesNotHaltScheduler() {
            when(scheduleWindowRepo.findByTypeAndClosesAtLessThan(eq(WindowType.ADVISOR_ASSOCIATION), any()))
                .thenReturn(List.of(window2024));
            when(projectGroupRepo.findByTermIdAndStatusInAndAdvisorIsNull(eq(TERM_2024), any()))
                .thenThrow(new RuntimeException("catastrophic DB failure"));

            assertThatCode(() -> sanitizationService.runSanitizationIfWindowClosed())
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Failure on window2024 does not stop window2025 processing")
        void failureOnFirstWindow_secondWindowStillProcessed() {
            when(scheduleWindowRepo.findByTypeAndClosesAtLessThan(eq(WindowType.ADVISOR_ASSOCIATION), any()))
                .thenReturn(List.of(window2024, window2025));
            when(projectGroupRepo.findByTermIdAndStatusInAndAdvisorIsNull(eq(TERM_2024), any()))
                .thenThrow(new RuntimeException("term 2024 error"));
            when(projectGroupRepo.findByTermIdAndStatusInAndAdvisorIsNull(eq(TERM_2025), any()))
                .thenReturn(Collections.emptyList());

            assertThatCode(() -> sanitizationService.runSanitizationIfWindowClosed())
                .doesNotThrowAnyException();

            verify(projectGroupRepo).findByTermIdAndStatusInAndAdvisorIsNull(eq(TERM_2025), any());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // BUG 4 regression — force=true logs a warning, force=false does not
    // Fix: if (force) log.warn(...)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Bug4 regression — force parameter: same repo behavior regardless of flag")
    class Bug4_Regression_ForceParameter {

        @Test
        @DisplayName("force=true and force=false make identical repository calls")
        void forceTrueAndForceFalse_identicalRepoCalls() {
            when(projectGroupRepo.findByTermIdAndStatusInAndAdvisorIsNull(eq(TERM_2024), any()))
                .thenReturn(List.of(group1));
            when(projectGroupRepo.save(any())).thenReturn(group1);

            sanitizationService.runSanitization(TERM_2024, false);

            org.mockito.Mockito.reset(projectGroupRepo, groupMembershipRepo, advisorRequestRepo);
            when(projectGroupRepo.findByTermIdAndStatusInAndAdvisorIsNull(eq(TERM_2024), any()))
                .thenReturn(List.of(group1));
            when(projectGroupRepo.save(any())).thenReturn(group1);

            sanitizationService.runSanitization(TERM_2024, true);

            verify(projectGroupRepo, times(1)).findByTermIdAndStatusInAndAdvisorIsNull(any(), any());
            verify(projectGroupRepo, times(1)).save(group1);
            verify(groupMembershipRepo, times(1)).deleteByGroupId(group1.getId());
            verify(advisorRequestRepo, times(1)).bulkUpdateStatusByGroupId(any(), any());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // BUG 5 — Historical re-scan (behavior unchanged by design; idempotent)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Bug5 — Historical re-scan: idempotent by design (already-disbanded groups excluded by status filter)")
    class Bug5_HistoricalRescanning {

        @Test
        @DisplayName("All historical closed windows queried on every tick — idempotent because disbanded groups are excluded from query")
        void allHistoricalWindowsQueried_idempotentBecauseDisbandedGroupsExcluded() {
            ScheduleWindow oldWindow1 = makeWindow("2023-FALL",   LocalDateTime.now().minusYears(1));
            ScheduleWindow oldWindow2 = makeWindow("2023-SPRING", LocalDateTime.now().minusYears(2));

            when(scheduleWindowRepo.findByTypeAndClosesAtLessThan(eq(WindowType.ADVISOR_ASSOCIATION), any()))
                .thenReturn(List.of(window2024, window2025, oldWindow1, oldWindow2));
            when(projectGroupRepo.findByTermIdAndStatusInAndAdvisorIsNull(any(), any()))
                .thenReturn(Collections.emptyList());

            assertThatCode(() -> sanitizationService.runSanitizationIfWindowClosed())
                .doesNotThrowAnyException();

            // All 4 windows processed — old terms return empty (nothing to do)
            verify(projectGroupRepo, times(4))
                .findByTermIdAndStatusInAndAdvisorIsNull(any(), any());
        }

        @Test
        @DisplayName("No closed windows: no group queries issued")
        void noClosedWindows_noGroupQueriesIssued() {
            when(scheduleWindowRepo.findByTypeAndClosesAtLessThan(any(), any()))
                .thenReturn(Collections.emptyList());

            sanitizationService.runSanitizationIfWindowClosed();

            verifyNoInteractions(projectGroupRepo, groupMembershipRepo, advisorRequestRepo);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // HAPPY PATH — core contract verification
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Happy path — core contract")
    class HappyPath {

        @Test
        @DisplayName("disbandGroup: three-step sequence in correct order (save → deleteMemberships → rejectRequests)")
        void disbandGroup_threeStepSequenceInCorrectOrder() {
            when(projectGroupRepo.save(group1)).thenReturn(group1);

            sanitizationService.disbandGroup(group1);

            InOrder order = inOrder(projectGroupRepo, groupMembershipRepo, advisorRequestRepo);
            order.verify(projectGroupRepo).save(group1);
            order.verify(groupMembershipRepo).deleteByGroupId(group1.getId());
            order.verify(advisorRequestRepo).bulkUpdateStatusByGroupId(
                eq(RequestStatus.AUTO_REJECTED), eq(group1.getId()));
        }

        @Test
        @DisplayName("disbandGroup: group status set to DISBANDED before save()")
        void disbandGroup_statusSetToDisbandedBeforeSave() {
            when(projectGroupRepo.save(any())).thenAnswer(inv -> {
                ProjectGroup saved = inv.getArgument(0);
                assertThat(saved.getStatus()).isEqualTo(GroupStatus.DISBANDED);
                return saved;
            });

            sanitizationService.disbandGroup(group1);
        }

        @Test
        @DisplayName("runSanitization: queries FORMING + TOOLS_PENDING + TOOLS_BOUND statuses only")
        void runSanitization_queriesCorrectStatuses() {
            org.mockito.ArgumentCaptor<List<GroupStatus>> statusCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
            when(projectGroupRepo.findByTermIdAndStatusInAndAdvisorIsNull(
                    eq(TERM_2024), statusCaptor.capture()))
                .thenReturn(Collections.emptyList());

            sanitizationService.runSanitization(TERM_2024, false);

            assertThat(statusCaptor.getValue())
                .containsExactlyInAnyOrder(
                    GroupStatus.FORMING,
                    GroupStatus.TOOLS_PENDING,
                    GroupStatus.TOOLS_BOUND);
        }

        @Test
        @DisplayName("runSanitization: no groups found → no save/delete/update called")
        void runSanitization_noGroupsFound_noWritesIssued() {
            when(projectGroupRepo.findByTermIdAndStatusInAndAdvisorIsNull(eq(TERM_2024), any()))
                .thenReturn(Collections.emptyList());

            sanitizationService.runSanitization(TERM_2024, false);

            verify(projectGroupRepo, never()).save(any());
            verifyNoInteractions(groupMembershipRepo, advisorRequestRepo);
        }

        @Test
        @DisplayName("runSanitization: multiple groups each receive all three operations")
        void runSanitization_multipleGroups_eachReceiveAllThreeOps() {
            when(projectGroupRepo.findByTermIdAndStatusInAndAdvisorIsNull(eq(TERM_2024), any()))
                .thenReturn(List.of(group1, group2, group3));
            when(projectGroupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            sanitizationService.runSanitization(TERM_2024, false);

            verify(projectGroupRepo, times(3)).save(any());
            List.of(group1, group2, group3).forEach(g -> {
                verify(groupMembershipRepo).deleteByGroupId(g.getId());
                verify(advisorRequestRepo).bulkUpdateStatusByGroupId(
                    eq(RequestStatus.AUTO_REJECTED), eq(g.getId()));
            });
        }

        @Test
        @DisplayName("Scheduler dispatches runSanitization for each closed window with correct termId")
        void scheduler_dispatchesForEachClosedWindow_correctTermId() {
            when(scheduleWindowRepo.findByTypeAndClosesAtLessThan(eq(WindowType.ADVISOR_ASSOCIATION), any()))
                .thenReturn(List.of(window2024, window2025));
            when(projectGroupRepo.findByTermIdAndStatusInAndAdvisorIsNull(any(), any()))
                .thenReturn(Collections.emptyList());

            sanitizationService.runSanitizationIfWindowClosed();

            verify(projectGroupRepo).findByTermIdAndStatusInAndAdvisorIsNull(eq(TERM_2024), any());
            verify(projectGroupRepo).findByTermIdAndStatusInAndAdvisorIsNull(eq(TERM_2025), any());
        }

        @Test
        @DisplayName("Scheduler only queries ADVISOR_ASSOCIATION type windows")
        void scheduler_onlyQueriesAdvisorAssociationWindows() {
            when(scheduleWindowRepo.findByTypeAndClosesAtLessThan(
                    eq(WindowType.ADVISOR_ASSOCIATION), any()))
                .thenReturn(Collections.emptyList());

            sanitizationService.runSanitizationIfWindowClosed();

            verify(scheduleWindowRepo).findByTypeAndClosesAtLessThan(
                eq(WindowType.ADVISOR_ASSOCIATION), any());
            verifyNoInteractions(projectGroupRepo, groupMembershipRepo, advisorRequestRepo);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static ProjectGroup makeGroup(String name, String termId, GroupStatus status) {
        ProjectGroup g = new ProjectGroup();
        g.setGroupName(name);
        g.setTermId(termId);
        g.setStatus(status);
        g.setCreatedAt(LocalDateTime.now());
        try {
            java.lang.reflect.Field idField = ProjectGroup.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(g, UUID.randomUUID());

            java.lang.reflect.Field versionField = ProjectGroup.class.getDeclaredField("version");
            versionField.setAccessible(true);
            versionField.set(g, 0L);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return g;
    }

    private static ScheduleWindow makeWindow(String termId, LocalDateTime closesAt) {
        ScheduleWindow w = new ScheduleWindow();
        w.setTermId(termId);
        w.setType(WindowType.ADVISOR_ASSOCIATION);
        w.setOpensAt(closesAt.minusDays(14));
        w.setClosesAt(closesAt);
        return w;
    }
}
