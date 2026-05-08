package com.senior.spm.controller.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response body for the final grade endpoints:
 * <ul>
 *   <li>{@code GET /api/students/{studentId}/grade} — returns the stored grade (deliverableBreakdown is empty {@code []}).</li>
 *   <li>{@code GET /api/students/{studentId}/grade/calculate} — recalculates and upserts; returns full breakdown.</li>
 * </ul>
 * Per-deliverable breakdowns are computed on-the-fly and are NOT persisted — see endpoints_p7.md D3.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalGradeResponse {

    /** 11-digit student number (path parameter). */
    private String studentId;

    private UUID groupId;

    /** deliverableBreakdown — on-the-fly, not persisted. */
    private List<DeliverableBreakdown> deliverableBreakdown;

    /** WeightedTotal = SUM(ScaledGrade × deliverable.weight / 100) */
    private BigDecimal weightedTotal;

    /** C_i = completedStoryPoints / targetStoryPoints (not capped at 1.0). */
    private BigDecimal completionRatio;

    /** G_i = WeightedTotal × C_i */
    private BigDecimal finalGrade;

    private LocalDateTime calculatedAt;
}
