package com.senior.spm.service.dto;

/**
 * Internal result returned by {@code SprintTrackingOrchestrator.triggerForSprint()}.
 * The controller maps this to the public {@code SprintRefreshResponse} DTO.
 */
public record SprintRefreshStats(int groupsProcessed, int issuesFetched, int aiValidationsRun) {}
