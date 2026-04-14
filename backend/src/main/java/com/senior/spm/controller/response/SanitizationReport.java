package com.senior.spm.controller.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for {@code POST /api/coordinator/sanitize}.
 *
 * <p>Reports the outcome of a single sanitization run, whether triggered manually
 * by a coordinator or automatically by the scheduler. Counts reflect the state at
 * the time the run was initiated — groups that were skipped due to an
 * {@code OptimisticLockingFailureException} are NOT deducted from {@code disbandedCount}
 * (they will be caught on the next scheduler tick).
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code disbandedCount} — number of unadvised groups found and targeted for disbanding</li>
 *   <li>{@code autoRejectedRequestCount} — total PENDING advisor requests that were auto-rejected
 *       across all targeted groups</li>
 *   <li>{@code triggeredAt} — UTC timestamp when the manual trigger was received</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanitizationReport {

    private int disbandedCount;
    private long autoRejectedRequestCount;
    private LocalDateTime triggeredAt;
}
