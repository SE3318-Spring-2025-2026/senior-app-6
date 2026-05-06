package com.senior.spm.controller.response;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-deliverable intermediate values returned in the FinalGradeResponse breakdown.
 * These values are computed on-the-fly and are NOT persisted (per D3 in endpoints_p7.md).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliverableBreakdown {

    private UUID deliverableId;

    private String deliverableName;

    /** B — Base Deliverable Grade (stub: 0.0 until #249 RubricGrade is shipped). */
    private BigDecimal baseGrade;

    /** ScrumScalar = AVG(pointAGrade.toNumeric()) / 100 across contributing sprints. */
    private BigDecimal scrumScalar;

    /** ReviewScalar = AVG(pointBGrade.toNumeric()) / 100 across contributing sprints. */
    private BigDecimal reviewScalar;

    /** DS = (ScrumScalar + ReviewScalar) / 2.0 */
    private BigDecimal deliverableScalar;

    /** ScaledGrade = B × DS */
    private BigDecimal scaledGrade;

    /** deliverable.weight (%) as configured by coordinator. */
    private BigDecimal weight;

    /** weightedContribution = ScaledGrade × (weight / 100) */
    private BigDecimal weightedContribution;
}
