package com.senior.spm.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.dto.SanitizationReport;
import com.senior.spm.entity.AdvisorRequest.RequestStatus;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.entity.ScheduleWindow.WindowType;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.ScheduleWindowRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SanitizationService handles the automatic disbanding of unadvised groups
 * once the ADVISOR_ASSOCIATION window closes.
 *
 * <p>Scheduled job runs periodically to detect newly closed windows.
 * Manual trigger (force=true) available for coordinators via a future endpoint.
 *
 * <p>Transaction design:
 * <ul>
 *   <li>{@code runSanitizationIfWindowClosed} — no transaction; calls self-proxy to
 *       ensure Spring AOP is active on {@code runSanitization}.</li>
 *   <li>{@code runSanitization} — read-only transaction for group fetch; delegates
 *       per-group writes to {@code disbandGroup} through self-proxy.</li>
 *   <li>{@code disbandGroup} — {@code REQUIRES_NEW} transaction; one commit per group.
 *       {@code OptimisticLockingFailureException} rolls back only this group's
 *       transaction and is caught by the caller, allowing the loop to continue.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanitizationService {

    private final ScheduleWindowRepository scheduleWindowRepository;
    private final ProjectGroupRepository   projectGroupRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final AdvisorRequestRepository advisorRequestRepository;
    private final TermConfigService        termConfigService;

    /**
     * Self-proxy reference — injected lazily to avoid circular dependency.
     * Required so that internal calls to {@code runSanitization} and
     * {@code disbandGroup} pass through the Spring AOP proxy, activating
     * the {@code @Transactional} annotations on those methods.
     */
    @Autowired
    @Lazy
    private SanitizationService self;

    // ── scheduled entry point ────────────────────────────────────────────────

    /**
     * Runs every 5 minutes. Finds all closed ADVISOR_ASSOCIATION windows and
     * triggers sanitization per term. Logs and continues on per-term failure.
     */
    @Scheduled(fixedRate = 300_000)
    public void runSanitizationIfWindowClosed() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

        List<ScheduleWindow> closedWindows = scheduleWindowRepository
            .findByTypeAndClosesAtLessThan(WindowType.ADVISOR_ASSOCIATION, now);

        for (ScheduleWindow window : closedWindows) {
            try {
                // Call through self-proxy so @Transactional on runSanitization is applied
                self.runSanitization(window.getTermId(), false);
            } catch (Exception e) {
                log.error("Sanitization failed for term {}: {}", window.getTermId(), e.getMessage(), e);
            }
        }
    }

    // ── per-term logic ───────────────────────────────────────────────────────

    /**
     * Queries all unadvised groups for {@code termId} and disbands each one in its
     * own transaction. Groups that raise an {@code OptimisticLockingFailureException}
     * (concurrent advisor acceptance) are skipped and will be re-evaluated on the
     * next scheduler tick.
     *
     * @param termId the term to sanitize
     * @param force  when {@code true}, logs a warning for audit purposes (coordinator trigger)
     */
    @Transactional(readOnly = true)
    public void runSanitization(String termId, boolean force) {
        if (force) {
            log.warn("Forced sanitization triggered for term {}", termId);
        }

        List<ProjectGroup> unadvisedGroups = projectGroupRepository
            .findByTermIdAndStatusInAndAdvisorIsNull(termId,
                List.of(GroupStatus.FORMING, GroupStatus.TOOLS_PENDING, GroupStatus.TOOLS_BOUND));

        for (ProjectGroup group : unadvisedGroups) {
            try {
                // Call through self-proxy so REQUIRES_NEW starts a fresh transaction per group
                self.disbandGroup(group);
            } catch (OptimisticLockingFailureException e) {
                // Concurrent advisor acceptance modified this group between our read and
                // this write. The group's own transaction was already rolled back by
                // REQUIRES_NEW. Skip — next tick will re-query and decide correctly.
                log.warn("Skipping group {} (term {}) — concurrent OL conflict; will retry on next tick",
                    group.getId(), termId);
            }
        }
    }

    // ── coordinator manual trigger ───────────────────────────────────────────

    /**
     * Manual trigger for the sanitization job, exposed via
     * {@code POST /api/coordinator/sanitize} (DFD 3.4 / sequence 3.4_sanitization_p3.md).
     *
     * <p>Window check:
     * <ul>
     *   <li>If the {@code ADVISOR_ASSOCIATION} window is still active AND {@code force = false} →
     *       throws {@link BusinessRuleException} (400).</li>
     *   <li>If {@code force = true} OR the window is already closed → proceeds immediately.</li>
     * </ul>
     *
     * <p>Counts for the report are computed <em>before</em> the sanitization run so they reflect
     * the groups that were targeted. Groups that fail with an
     * {@code OptimisticLockingFailureException} are skipped (caught in {@link #runSanitization}),
     * which means the returned counts may be slightly optimistic — this is acceptable per the spec.
     *
     * @param force when {@code true}, skips the window-active guard (coordinator override)
     * @return {@link SanitizationReport} with disbanded count, auto-rejected request count, and trigger timestamp
     * @throws BusinessRuleException if the window is still active and {@code force = false}
     */
    public SanitizationReport triggerManually(boolean force) {
        String termId = termConfigService.getActiveTermId();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

        // Window check — only bypass when force=true
        scheduleWindowRepository.findByTermIdAndType(termId, WindowType.ADVISOR_ASSOCIATION)
            .ifPresent(window -> {
                boolean windowActive = !window.getOpensAt().isAfter(now) && window.getClosesAt().isAfter(now);
                if (windowActive && !force) {
                    throw new BusinessRuleException(
                        "Advisor association window is still active — cannot sanitize early without confirmation");
                }
            });

        // Pre-count groups that will be targeted (for report)
        List<ProjectGroup> targetGroups = projectGroupRepository.findByTermIdAndStatusInAndAdvisorIsNull(termId,
            List.of(GroupStatus.FORMING, GroupStatus.TOOLS_PENDING, GroupStatus.TOOLS_BOUND));

        int disbandedCount = targetGroups.size();

        // Pre-count PENDING advisor requests across all targeted groups (for report)
        long autoRejectedRequestCount = targetGroups.stream()
            .mapToLong(g -> advisorRequestRepository.countByGroupIdAndStatus(
                g.getId(), RequestStatus.PENDING))
            .sum();

        // Run the actual sanitization (calls disbandGroup per group via self-proxy)
        self.runSanitization(termId, force);

        return SanitizationReport.builder()
            .disbandedCount(disbandedCount)
            .autoRejectedRequestCount(autoRejectedRequestCount)
            .triggeredAt(now)
            .build();
    }

    // ── per-group atomic unit ────────────────────────────────────────────────

    /**
     * Disbands a single group atomically (P3.6):
     * <ol>
     *   <li>Mark group DISBANDED and persist.</li>
     *   <li>Hard-delete all {@code GroupMembership} rows (frees students).</li>
     *   <li>Bulk-update all {@code PENDING} {@code AdvisorRequest} rows → {@code AUTO_REJECTED}.</li>
     * </ol>
     *
     * <p>Uses {@code REQUIRES_NEW} so this group's transaction is independent of any
     * outer transaction. An {@code OptimisticLockingFailureException} rolls back only
     * this group without poisoning the outer session.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void disbandGroup(ProjectGroup group) {
        group.setStatus(GroupStatus.DISBANDED);
        projectGroupRepository.save(group);

        groupMembershipRepository.deleteByGroupId(group.getId());

        advisorRequestRepository.bulkUpdateStatusByGroupId(
            RequestStatus.AUTO_REJECTED,
            group.getId()
        );
    }
}
