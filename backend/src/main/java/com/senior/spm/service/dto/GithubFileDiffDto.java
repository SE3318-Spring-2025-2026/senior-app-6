package com.senior.spm.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents one changed file returned by the GitHub Pulls Files API
 * ({@code GET /repos/{org}/{repo}/pulls/{prNumber}/files}).
 *
 * <p>The {@code patch} field is {@code null} for binary files or files
 * whose diff exceeds GitHub's size limit — callers must handle this.
 *
 * <p>Issue: #150 — [Backend] GitHub Sprint Integration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubFileDiffDto(

        /** Relative path of the changed file within the repository. */
        @JsonProperty("filename")
        String filename,

        /**
         * Unified diff patch string for this file, or {@code null} when
         * the diff is unavailable (binary files, oversized diffs, etc.).
         */
        @JsonProperty("patch")
        String patch
) {}
