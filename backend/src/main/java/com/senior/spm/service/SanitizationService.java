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

import com.senior.spm.entity.AdvisorRequest.RequestStatus;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.entity.ScheduleWindow.WindowType;
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
