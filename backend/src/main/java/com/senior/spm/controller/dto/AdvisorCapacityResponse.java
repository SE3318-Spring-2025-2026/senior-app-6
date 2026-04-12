package com.senior.spm.controller.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO carrying advisor capacity information.
 *
 * <p>Used by two endpoints with slightly different semantics:
 * <ul>
 *   <li>{@code GET /api/advisors} (Student) — {@code atCapacity} is always {@code null}; only
 *       advisors below capacity are returned.</li>
 *   <li>{@code GET /api/coordinator/advisors} (Coordinator) — {@code atCapacity} is populated;
 *       all professors are included, even those at or above capacity.</li>
 * </ul>
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code advisorId} — internal UUID of the StaffUser (Professor)</li>
 *   <li>{@code name} — display name (derived from the StaffUser's mail for now; P1 may add a name field)</li>
 *   <li>{@code mail} — institutional e-mail address of the professor</li>
 *   <li>{@code currentGroupCount} — number of active (non-DISBANDED) groups assigned to this advisor in the active term</li>
 *   <li>{@code capacity} — maximum groups this advisor can take ({@code StaffUser.advisorCapacity}, default 5)</li>
 *   <li>{@code atCapacity} — {@code true} if {@code currentGroupCount >= capacity}; {@code null} in the student-facing variant</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvisorCapacityResponse {

    private UUID advisorId;
    private String name;
    private String mail;
    private int currentGroupCount;
    private int capacity;
    /** Populated only for coordinator endpoint; null for student-facing endpoint. */
    private Boolean atCapacity;
}
