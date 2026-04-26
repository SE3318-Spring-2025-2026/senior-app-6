package com.senior.spm.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.service.dto.GithubFileDiffDto;
import com.senior.spm.service.dto.GithubPrDto;

/**
 * PAT-authenticated GitHub API service for Sprint-time operations.
 *
 * <p>This service is completely separate from {@link GithubService} (which
 * handles OAuth flows). It builds a per-request {@link RestClient} for each
 * group using the decrypted PAT, so calls from different groups are isolated.
 *
 * <p>All public methods follow a strict never-throw contract: on any HTTP
 * error or network failure they log a warning and return an empty result
 * ({@code Optional.empty()} or {@code Collections.emptyList()}). Callers must
 * treat an empty result as "data unavailable" rather than an error.
 *
 * <p><b>Do NOT modify {@code GithubService.java}</b> — that class is
 * OAuth-only and must remain untouched.
 *
 * <p>Responses are parsed via {@link JsonNode} (same pattern as
 * {@link JiraSprintService}) to avoid Jackson visibility issues with inner
 * record types and to handle partial/unknown GitHub response fields gracefully.
 *
 * <p>References: {@code endpoints_p5.md} sub-process 5.2;
 * {@code docs/phase1_2.md} Steps 3–4; IR-3.
 * Issue: #150 — [Backend] GitHub Sprint Integration.
 */
@Service
public class GithubSprintService {

    private static final Logger log = LoggerFactory.getLogger(GithubSprintService.class);

    private static final String ACCEPT_HEADER = "application/vnd.github+json";
    private static final String API_VERSION   = "2022-11-28";

    private final RestClient     baseClient;
    private final EncryptionService encryptionService;

    public GithubSprintService(RestClient.Builder restClientBuilder,
                               EncryptionService encryptionService) {
        // Build the base client once — carries all auto-configured converters.
        // Per-request authentication is added via mutate() in buildClient().
        this.baseClient = restClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, ACCEPT_HEADER)
                .defaultHeader("X-GitHub-Api-Version", API_VERSION)
                .defaultHeader(HttpHeaders.USER_AGENT, "SPMApp")
                .build();
        this.encryptionService = encryptionService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Looks up a branch whose name contains the given Jira {@code issueKey}.
     *
     * <p>Calls {@code GET /repos/{org}/{repo}/branches?per_page=100} and
     * returns the first branch name that contains {@code issueKey}
     * (case-sensitive). This handles common naming conventions such as
     * {@code feature/SPM-42-fix} where the issue key is embedded in the
     * branch name but is not the first segment.
     *
     * @param group    the project group whose GitHub credentials are used
     * @param issueKey the Jira issue key to search for (e.g. {@code "SPM-42"})
     * @return the branch name, or {@code Optional.empty()} if not found or on error
     */
    public Optional<String> findBranchByIssueKey(ProjectGroup group, String issueKey) {
        if (!hasGithubCredentials(group)) {
            log.warn("GitHub credentials missing for group {}", group.getId());
            return Optional.empty();
        }
        try {
            String org  = group.getGithubOrgName();
            String repo = group.getGithubRepoName();
            String url  = githubApiBase() + "/repos/" + org + "/" + repo + "/branches?per_page=100";

            JsonNode response = buildClient(group).get()
                    .uri(url)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.isArray()) {
                return Optional.empty();
            }

            for (JsonNode branch : response) {
                String name = branch.path("name").asText(null);
                if (name != null && (name.contains(issueKey + "-") || name.contains(issueKey + "/") || name.endsWith(issueKey) || name.equals(issueKey))) {
                    return Optional.of(name);
                }
            }
            return Optional.empty();

        } catch (RestClientException ex) {
            log.warn("GitHub API error finding branch for group {} issueKey {}: {}",
                     group.getId(), issueKey, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Unexpected error finding branch for group {} issueKey {}",
                     group.getId(), issueKey, ex);
            return Optional.empty();
        }
    }

    /**
     * Finds a closed (merged or unmerged) Pull Request for the given branch.
     *
     * <p>Calls {@code GET /repos/{org}/{repo}/pulls?head={org}:{branchName}&state=closed}.
     * A PR is considered <em>merged</em> when {@code state == "closed"} AND
     * {@code merged_at != null}.
     *
     * @param group      the project group whose GitHub credentials are used
     * @param branchName the fully-qualified branch name (e.g. {@code "feature/SPM-42-fix"})
     * @return a {@link GithubPrDto} with the merged flag set, or {@code Optional.empty()}
     *         when no closed PR exists for this branch or on error
     */
    public Optional<GithubPrDto> findMergedPR(ProjectGroup group, String branchName) {
        if (!hasGithubCredentials(group)) {
            log.warn("GitHub credentials missing for group {}", group.getId());
            return Optional.empty();
        }
        try {
            String org  = group.getGithubOrgName();
            String repo = group.getGithubRepoName();
            String url  = githubApiBase() + "/repos/" + org + "/" + repo
                    + "/pulls?head=" + org + ":" + branchName + "&base=main&state=closed";

            JsonNode response = buildClient(group).get()
                    .uri(url)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.isArray() || response.isEmpty()) {
                return Optional.empty();
            }

            JsonNode first   = response.get(0);
            Long prNumber    = first.path("number").asLong();
            String state     = first.path("state").asText(null);
            String mergedAt  = first.hasNonNull("merged_at")
                    ? first.path("merged_at").asText(null)
                    : null;
            String authorLogin = first.path("user").path("login").asText(null);

            return Optional.of(GithubPrDto.of(prNumber, state, mergedAt, authorLogin));

        } catch (RestClientException ex) {
            log.warn("GitHub API error finding merged PR for group {} branch {}: {}",
                     group.getId(), branchName, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Unexpected error finding merged PR for group {} branch {}",
                     group.getId(), branchName, ex);
            return Optional.empty();
        }
    }

    /**
     * Fetches the review comment bodies for a given Pull Request.
     *
     * <p>Calls {@code GET /repos/{org}/{repo}/pulls/{prNumber}/reviews}.
     * Blank or null bodies are excluded from the result.
     *
     * @param group    the project group whose GitHub credentials are used
     * @param prNumber the GitHub PR number
     * @return list of non-blank review comment bodies, or an empty list on error
     */
    public List<String> fetchPRReviewComments(ProjectGroup group, long prNumber) {
        if (!hasGithubCredentials(group)) {
            log.warn("GitHub credentials missing for group {}", group.getId());
            return Collections.emptyList();
        }
        try {
            String org  = group.getGithubOrgName();
            String repo = group.getGithubRepoName();
            String url  = githubApiBase() + "/repos/" + org + "/" + repo
                    + "/pulls/" + prNumber + "/comments?per_page=100";

            JsonNode response = buildClient(group).get()
                    .uri(url)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.isArray()) {
                return Collections.emptyList();
            }

            List<String> bodies = new ArrayList<>();
            for (JsonNode review : response) {
                String body = review.path("body").asText(null);
                if (body != null && !body.isBlank()) {
                    bodies.add(body);
                }
            }
            return Collections.unmodifiableList(bodies);

        } catch (RestClientException ex) {
            log.warn("GitHub API error fetching PR reviews for group {} PR {}: {}",
                     group.getId(), prNumber, ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.warn("Unexpected error fetching PR reviews for group {} PR {}",
                     group.getId(), prNumber, ex);
            return Collections.emptyList();
        }
    }

    /**
     * Retrieves the list of changed files (with diff patches) for a Pull Request.
     *
     * <p>Calls {@code GET /repos/{org}/{repo}/pulls/{prNumber}/files}.
     * The {@code patch} field of each {@link GithubFileDiffDto} may be {@code null}
     * for binary files or when the diff exceeds GitHub's size limit.
     *
     * @param group    the project group whose GitHub credentials are used
     * @param prNumber the GitHub PR number
     * @return list of file diffs, or an empty list on error
     */
    public List<GithubFileDiffDto> fetchFileDiffs(ProjectGroup group, long prNumber) {
        if (!hasGithubCredentials(group)) {
            log.warn("GitHub credentials missing for group {}", group.getId());
            return Collections.emptyList();
        }
        try {
            String org  = group.getGithubOrgName();
            String repo = group.getGithubRepoName();
            String url  = githubApiBase() + "/repos/" + org + "/" + repo
                    + "/pulls/" + prNumber + "/files?per_page=100";

            JsonNode response = buildClient(group).get()
                    .uri(url)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.isArray()) {
                return Collections.emptyList();
            }

            List<GithubFileDiffDto> diffs = new ArrayList<>();
            for (JsonNode file : response) {
                String filename = file.path("filename").asText(null);
                String patch    = file.hasNonNull("patch")
                        ? file.path("patch").asText(null)
                        : null;
                if (filename != null) {
                    diffs.add(new GithubFileDiffDto(filename, patch));
                }
            }
            return Collections.unmodifiableList(diffs);

        } catch (RestClientException ex) {
            log.warn("GitHub API error fetching file diffs for group {} PR {}: {}",
                     group.getId(), prNumber, ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.warn("Unexpected error fetching file diffs for group {} PR {}",
                     group.getId(), prNumber, ex);
            return Collections.emptyList();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Overrideable base-URL hook — allows tests to point at WireMock
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the base URL for the GitHub REST API.
     *
     * <p>Declared {@code protected} so that test subclasses can override it
     * to redirect calls to a local WireMock server without any Spring context
     * re-wiring.
     *
     * @return the GitHub API base URL (default: {@code https://api.github.com})
     */
    protected String githubApiBase() {
        return "https://api.github.com";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns a {@link RestClient} derived from the shared {@link #baseClient}
     * with the group's decrypted PAT injected as the {@code Authorization} header.
     */
    private RestClient buildClient(ProjectGroup group) {
        String decryptedPat = encryptionService.decrypt(group.getEncryptedGithubPat());
        return baseClient.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + decryptedPat)
                .build();
    }

    /**
     * Guard: returns {@code false} when the minimum required GitHub fields are absent.
     */
    private boolean hasGithubCredentials(ProjectGroup group) {
        return group.getGithubOrgName() != null
                && group.getGithubRepoName() != null
                && group.getEncryptedGithubPat() != null;
    }
}
