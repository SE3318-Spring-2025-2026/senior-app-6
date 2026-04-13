package com.senior.spm.controller.request;

import lombok.Data;

/**
 * Optional request body for {@code POST /api/coordinator/sanitize}.
 *
 * <p>Omitting the body entirely (or sending {@code {}}) performs a normal trigger: the sanitization
 * runs only if the {@code ADVISOR_ASSOCIATION} window is already closed. Setting {@code force = true}
 * allows the coordinator to trigger early while the window is still active.
 */
@Data
public class SanitizationTriggerRequest {

    /**
     * When {@code true}, the sanitization job runs immediately regardless of whether the
     * ADVISOR_ASSOCIATION window is still open. Defaults to {@code false}.
     */
    private boolean force = false;
}
