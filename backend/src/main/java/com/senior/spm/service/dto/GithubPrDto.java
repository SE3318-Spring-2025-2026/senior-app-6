package com.senior.spm.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lightweight DTO for a GitHub Pull Request returned by
 * {@link com.senior.spm.service.GithubSprintService#findMergedPR}.
 *
 * <p>A PR is considered <em>merged</em> when {@code state == "closed"} AND
 * {@code merged_at != null}. The two fields are evaluated together by the
 * service; this record simply carries the result.
 *
 * <p>Issue: #150 — [Backend] GitHub Sprint Integration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubPrDto(

        /** GitHub PR number (e.g. 42). */
        @JsonProperty("number")
        Long prNumber,

        /**
         * {@code true} when the PR was merged ({@code state=closed} AND
         * {@code merged_at != null}); {@code false} otherwise.
         */
        boolean merged,

        /**
         * GitHub username of the PR author ({@code user.login} in the API response).
         * Used as the authoritative identity source for {@code assigneeGithubUsername}
         * in {@code SprintTrackingLog} — JIRA assignee email is not a reliable match.
         */
        String authorLogin
) {

    /**
     * Factory used by {@link com.senior.spm.service.GithubSprintService} to
     * derive the merged flag and author from the raw GitHub API response fields.
     *
     * @param prNumber    the PR number from GitHub's {@code "number"} field
     * @param state       the value of GitHub's {@code "state"} field
     * @param mergedAt    the value of GitHub's {@code "merged_at"} field (nullable)
     * @param authorLogin the value of GitHub's {@code "user.login"} field (nullable)
     * @return a {@code GithubPrDto} with {@code merged} and {@code authorLogin} set correctly
     */
    public static GithubPrDto of(Long prNumber, String state, String mergedAt, String authorLogin) {
        boolean merged = "closed".equalsIgnoreCase(state) && mergedAt != null;
        return new GithubPrDto(prNumber, merged, authorLogin);
    }
}
