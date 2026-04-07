package com.senior.spm.controller.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CoordinatorMemberRequest {
    /**
     * The student ID (as String, not UUID) of the student to add or remove.
     * Lookup is performed by this studentId field rather than UUID.
     */
    private String studentId;

    /**
     * The action to perform: "ADD" or "REMOVE".
     * <ul>
     *   <li><strong>ADD</strong>: Force-add student to group with capacity & conflict validation</li>
     *   <li><strong>REMOVE</strong>: Force-remove student from group with TEAM_LEADER blocker</li>
     * </ul>
     * Must be uppercase. Invalid values return HTTP 400 Bad Request.
     */
    private String action;
}
