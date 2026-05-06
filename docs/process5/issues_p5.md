# P5 Issues — Sprint 3 (MERGED FINAL)
> Process: Sprint Tracking, Scrum Grading & AI Validation (P5)
> Sprint: Sprint 3 | Estimation date: 2026-04-17
> SP scale: Fibonacci (1, 2, 3, 5, 8). Issue numbers are placeholders — file on GitHub before sprint start.
>
> **Merge basis:** Ver1 + Ver2 + `docs/phase1_2.md` (authoritative spec) + codebase verification.
> Key spec corrections applied: `pointA_grade`/`pointB_grade` field names, `TOOLS_BOUND`+
> pipeline trigger, `{ "message": "..." }` error body, `SymmetricEncryptionService` usage.

---

## Issue Distribution

```
[Backend]   Sprint Tracking & Grading Domain Model            #P5-01   --> Yağmur    2 SP
[Backend]   JIRA Sprint Integration — Story Fetch             #P5-02   --> Efecan    3 SP
[Backend]   GitHub Sprint Integration — Branch & PR           #P5-03   --> Efecan    2 SP
[Backend]   AI Code Review Validator — PR Quality (5.3)       #P5-04a  --> Batu      3 SP
[Backend]   AI Diff Semantics Validator — Issue Match (5.4)   #P5-04b  --> Batıkan   3 SP
[Backend]   End-of-Sprint Tracking Orchestrator               #P5-05   --> Egemen    2 SP
[Backend]   Scrum Grading API — Point A/B Submission          #P5-06   --> Yağmur    2 SP
[Backend]   Coordinator Sprint Control & Overview API         #P5-07   --> Egemen    2 SP
[Frontend]  Advisor Sprint Live Panel & Grade Form            #P5-08   --> Arda      3 SP
[Frontend]  Student Sprint Monitor & AI Results View          #P5-09   --> Zeynep    3 SP
[QA]        Sprint Pipeline E2E — WireMock JIRA/GH/LLM        #P5-10   --> Bilge     3 SP
[QA]        Scrum Grading Role Guard & Upsert Tests           #P5-11   --> Demir     1 SP
[QA]        P5 Postman Collection — Role-Gated Chains         #P5-12   --> Demir     2 SP
```

```
            | P5  | Role
Yağmur:     |  4  | [Backend]         (#P5-01 + #P5-06)
Batıkan:    |  3  | [Backend]         (#P5-04b)
Efecan:     |  5  | [Backend]         (#P5-02 + #P5-03)
Batu:       |  3  | [Backend]         (#P5-04a)
Egemen:     |  4  | [Backend]         (#P5-05 + #P5-07)
Arda:       |  3  | [Frontend]        (#P5-08)
Zeynep:     |  3  | [Frontend]        (#P5-09)
Bilge:      |  3  | [QA]              (#P5-10)
Demir:      |  3  | [QA]              (#P5-11 + #P5-12)

P5 Total: 31 SP
```

---

## #P5-01 — [Backend] Sprint Tracking & Grading Domain Model — Entities, Enums & Repository Layer
**Estimate:** 2 Points | **Assignee:** Yağmur

**Problem Summary:** Define the two new JPA entities for P5 data persistence and their repositories.
Establishes D5 (Sprint Tracking Log + Scrum Grade) in the database schema. Every other P5 issue
depends on this being merged and stable on Day 1.
**Scope:** Backend — Data Layer.

**Deliverables:**
1. `SprintTrackingLog` entity (`sprint_tracking_log` table):
   - `id` UUID PK (`@GeneratedValue(strategy = GenerationType.UUID)`)
   - `group` `@ManyToOne ProjectGroup` NOT NULL
   - `sprint` `@ManyToOne Sprint` NOT NULL
   - `issueKey` String NOT NULL (e.g. `"SPM-42"`)
   - `assigneeGithubUsername` String NULLABLE
   - `storyPoints` Integer NULLABLE
   - `prNumber` Long NULLABLE (GitHub PR number)
   - `prMerged` Boolean NULLABLE (null = no branch found, false = open, true = merged)
   - `aiPrResult` `AiValidationResult` enum, default `PENDING`
   - `aiDiffResult` `AiValidationResult` enum, default `PENDING`
   - `fetchedAt` LocalDateTime NOT NULL
   - Unique constraint: `uq_stl_group_sprint_issue (group_id, sprint_id, issue_key)`
2. `ScrumGrade` entity (`scrum_grade` table):
   - `id` UUID PK
   - `group` `@ManyToOne ProjectGroup` NOT NULL
   - `sprint` `@ManyToOne Sprint` NOT NULL
   - `advisor` `@ManyToOne StaffUser` NOT NULL
   - `pointAGrade` `ScrumGradeValue` enum NOT NULL (`@JsonProperty("pointA_grade")` on DTO)
   - `pointBGrade` `ScrumGradeValue` enum NOT NULL (`@JsonProperty("pointB_grade")` on DTO)
   - `gradedAt` LocalDateTime NOT NULL
   - `updatedAt` LocalDateTime NULLABLE
   - Unique constraint: `uq_sg_group_sprint (group_id, sprint_id)`
3. `AiValidationResult` enum: `PENDING`, `PASS`, `WARN`, `FAIL`, `SKIPPED`.
4. `ScrumGradeValue` enum: `A`, `B`, `C`, `D`, `F`.
5. `SprintTrackingLogRepository`:
   - `findByGroupIdAndSprintId(UUID groupId, UUID sprintId)` → `List<SprintTrackingLog>`
   - `findBySprintId(UUID sprintId)` → `List<SprintTrackingLog>`
   - `deleteBySprintId(UUID sprintId)` — coordinator refresh: deletes ALL rows for a sprint across all groups
   - `deleteByGroupIdAndSprintId(UUID groupId, UUID sprintId)` — reserved for per-group re-fetch
6. `ScrumGradeRepository`:
   - `findByGroupIdAndSprintId(UUID groupId, UUID sprintId)` → `Optional<ScrumGrade>`
   - `findByAdvisorIdAndSprintId(UUID advisorId, UUID sprintId)` → `List<ScrumGrade>`
   - `existsByGroupIdAndSprintId(UUID groupId, UUID sprintId)` → `boolean`
7. Add to `SprintRepository` (Blue Team — coordinate or add directly if allowed): `findByEndDate(LocalDate date)` → `List<Sprint>`.

**References:** `endpoints_p5.md` — New Entities Required section; `docs/phase1_2.md` Step 7.

**Acceptance Criteria:**
- [ ] Tables `sprint_tracking_log` and `scrum_grade` auto-created by Hibernate on startup.
- [ ] `uq_stl_group_sprint_issue` prevents duplicate issue rows on re-fetch.
- [ ] `uq_sg_group_sprint` prevents duplicate scrum grades.
- [ ] `aiPrResult` and `aiDiffResult` default to `PENDING` on row creation.
- [ ] `@DataJpaTest` passes for all 6 repository methods.

**Related Issues:**
- **Depends on (hard):** `entity/Sprint.java` (Blue Team), `entity/ProjectGroup.java` (P2, merged #125), `entity/StaffUser.java` (Blue Team)
- **Blocks:** ALL other P5 issues — nothing starts until entities exist and are merged
- **Codebase refs:** `entity/Sprint.java`, `entity/ProjectGroup.java`, `entity/StaffUser.java`, `entity/SystemConfig.java`, `repository/SprintRepository.java`

---

## #P5-02 — [Backend] JIRA Sprint Integration — Active Story & Assignee Retrieval Service
**Estimate:** 3 Points | **Assignee:** Efecan

**Problem Summary:** Implement a service that authenticates with JIRA using each group's stored PAT
and fetches all active stories for a sprint: issue key, assignee, story points, description.
**Scope:** Backend — External Integration Service.

**Deliverables:**
1. `JiraSprintService.fetchSprintStories(ProjectGroup group)` — **no `jiraSprintId` parameter**:
   Active JIRA sprint resolved internally in 3 steps:
   - `GET /rest/agile/1.0/board?projectKeyOrId={group.jiraProjectKey}` → extract `boardId` (first result).
   - `GET /rest/agile/1.0/board/{boardId}/sprint?state=active` → extract `jiraSprintId`.
   - `GET /rest/agile/1.0/sprint/{jiraSprintId}/issue` → fetch issues.
   If board not found or no active sprint → log WARN + return empty list (skip group).
   Extracts per issue: `issueKey`, `assignee.name` (GitHub username), `story_points` (custom field), `description`.
   Returns `List<JiraIssueDto>`.
2. Decrypt `group.encryptedJiraToken` via `SymmetricEncryptionService.decrypt()` before each call.
   Construct `Authorization: Basic <base64(jiraEmail:token)>` using `group.jiraEmail`.
3. `JiraIssueDto` record: `String issueKey`, `String assigneeGithubUsername`, `Integer storyPoints`, `String description`.
4. Error handling: HTTP 401 → log WARN + return empty list. HTTP 404 → log WARN + return empty list. Network error → log + return empty list. **Never throw from this method** — orchestrator continues to next group.
5. Use `RestClient.Builder` (Spring injection, follow `GithubService.java` pattern exactly).

**References:** `endpoints_p5.md` sub-process 5.1; `docs/phase1_2.md` Step 2; IR-2.

**Acceptance Criteria:**
- [ ] Returns correct issue list from WireMocked JIRA response.
- [ ] `group.encryptedJiraToken` decrypted via `SymmetricEncryptionService.decrypt()` — never sent in plaintext.
- [ ] `group.jiraEmail` used in Basic auth header (not hardcoded).
- [ ] HTTP 401 from JIRA → empty list, no exception propagated.
- [ ] `storyPoints` is `null` (not 0) when JIRA field absent.
- [ ] Board not found (empty `values: []`) → empty list, no exception.
- [ ] No active sprint (`state=active` returns empty) → empty list, no exception.
- [ ] WireMock stubs required: board list endpoint + active sprint endpoint + issue endpoint (3 stubs per test).

**Related Issues:**
- **Depends on (hard):** #P5-01 (`JiraIssueDto` is saved into `SprintTrackingLog`)
- **Depends on (hard):** `service/SymmetricEncryptionService.java` (on main)
- **Depends on (hard):** `entity/ProjectGroup.java` fields: `encryptedJiraToken`, `jiraProjectKey`, `jiraEmail` (P2, merged #125)
- **Depends on (hard):** `RestClient.Builder` bean (Blue Team, on main)
- **Blocks:** #P5-05 (orchestrator calls `fetchSprintStories`)
- **Codebase refs:** `service/SymmetricEncryptionService.java`, `entity/ProjectGroup.java`, `service/GithubService.java` (RestClient.Builder pattern to follow)

---

## #P5-03 — [Backend] GitHub Sprint Integration — Branch Discovery, PR Verification & Diff Retrieval
**Estimate:** 2 Points | **Assignee:** Efecan

**Problem Summary:** Sprint-time GitHub operations: branch lookup by JIRA issue key prefix, PR merge
check, PR review comment fetch, and file diff retrieval. PAT-authenticated per-group calls.
**Do NOT extend `GithubService`** (OAuth-only). Create a completely separate `GithubSprintService`.
**Scope:** Backend — External Integration Service.

**Deliverables:**
1. `GithubSprintService` — inject `RestClient.Builder`, build per-request client with `Authorization: Bearer <decryptedPat>` using `SymmetricEncryptionService.decrypt(group.encryptedGithubPat)`.
2. `findBranchByIssueKey(ProjectGroup group, String issueKey)` — `GET /repos/{org}/{repo}/branches`, filter by name starting with `issueKey`. Returns `Optional<String>`. `org` = `group.githubOrgName`.
3. `findMergedPR(ProjectGroup group, String branchName)` → `Optional<GithubPrDto>` with `prNumber` (Long), `merged` (boolean). Merged = `state=closed` AND `merged_at != null`.
4. `fetchPRReviewComments(ProjectGroup group, long prNumber)` — `GET /repos/{org}/{repo}/pulls/{prNumber}/reviews`. Returns `List<String>` (comment bodies).
5. `fetchFileDiffs(ProjectGroup group, long prNumber)` — `GET /repos/{org}/{repo}/pulls/{prNumber}/files`. Returns `List<GithubFileDiffDto>` with `String filename`, `String patch`.
6. All methods: on HTTP error → log + return empty / `Optional.empty()`. Never throw.

**References:** `endpoints_p5.md` sub-process 5.2; `docs/phase1_2.md` Steps 3–4; IR-3.

**Acceptance Criteria:**
- [ ] `findBranchByIssueKey("SPM-42")` returns `"feature/SPM-42-fix"` from WireMock stub.
- [ ] Returns `Optional.empty()` when no matching branch — no exception.
- [ ] `findMergedPR` returns `merged=true` only when `state=closed` AND `merged_at != null`.
- [ ] `fetchFileDiffs` includes `patch` string per changed file.
- [ ] `GithubService.java` is NOT modified — `GithubSprintService` is a completely separate class.
- [ ] WireMock used for all GitHub API stubs.

**Related Issues:**
- **Depends on (hard):** #P5-01 (`GithubFileDiffDto`, `GithubPrDto` types defined here; stored in tracking log)
- **Depends on (hard):** `service/SymmetricEncryptionService.java` (on main)
- **Depends on (hard):** `entity/ProjectGroup.java` fields: `encryptedGithubPat`, `githubOrgName` (P2, #125)
- **Blocks:** #P5-04b (needs `GithubFileDiffDto`), #P5-05 (orchestrator calls all 4 methods)
- **Codebase refs:** `service/GithubService.java` (RestClient.Builder pattern — follow, do NOT extend), `entity/ProjectGroup.java`, `service/SymmetricEncryptionService.java`

---

## #P5-04a — [Backend] AI Code Review Validator — LLM-Powered PR Review Quality Analysis (5.3)
**Estimate:** 3 Points | **Assignee:** Batu

**Problem Summary:** First half of AI validation — validates PR review quality by sending review comments
to an LLM. Also establishes the shared `AiValidationService` infrastructure (LLM key retrieval,
HTTP client, response parser, timeout/error handling) that #P5-04b extends.
**Scope:** Backend — AI Integration Service (shared foundation + 5.3 validation).

**Deliverables:**
1. `AiValidationService` class — shared infrastructure:
   - LLM key: `systemConfigRepository.findById("llm_api_key")` → `SymmetricEncryptionService.decrypt(configValue)`. Throw `TermConfigNotFoundException` (reuse existing) if key missing.
   - `RestClient` with 10-second read timeout (use `RestClient.Builder` injection).
   - Response parser: extract first token from LLM response body. Map `"PASS"/"WARN"/"FAIL"` → `AiValidationResult`. Default to `WARN` on any parse failure.
   - Error handler: HTTP 5xx → `WARN`, timeout → `WARN`, any exception → `WARN`. Never throw.
2. `validatePRReview(List<String> reviewComments)` → `AiValidationResult`:
   - Empty `reviewComments` → immediately return `SKIPPED` (no LLM call).
   - Prompt: *"Review the following PR review comments and respond with only one word: PASS if the review is substantive, WARN if it is minimal or superficial, FAIL if it is empty or purely cosmetic."*
3. Unit tests covering all result branches (PASS, WARN, FAIL, empty→SKIPPED, timeout→WARN, 5xx→WARN).

**References:** `endpoints_p5.md` sub-process 5.3; `docs/phase1_2.md` FR-19, IR-4.

**Acceptance Criteria:**
- [ ] LLM key read from `system_config` via `SystemConfigRepository` and decrypted via `SymmetricEncryptionService` — never hardcoded.
- [ ] Empty `reviewComments` → `SKIPPED`, zero HTTP calls made (verified by mock assertion).
- [ ] Timeout (10s) → `WARN`, no exception propagated to caller.
- [ ] HTTP 5xx → `WARN`.
- [ ] Unrecognised LLM output (not PASS/WARN/FAIL) → `WARN`.
- [ ] Startup warning logged if `llm_api_key` missing from `system_config` (dev convenience).

**Related Issues:**
- **Depends on (hard):** #P5-01 (`AiValidationResult` enum)
- **Depends on (hard):** `entity/SystemConfig.java` + `SystemConfigRepository` (Blue Team, on main)
- **Depends on (hard):** `service/SymmetricEncryptionService.java` (on main)
- **Depends on (hard):** `exception/TermConfigNotFoundException.java` (P2, merged) — reuse for missing `llm_api_key`
- **Blocks:** #P5-04b (adds `validateIssueDiff` to this same class — must be merged first), #P5-05 (calls `validatePRReview`)
- **Codebase refs:** `entity/SystemConfig.java`, `service/SymmetricEncryptionService.java`, `exception/TermConfigNotFoundException.java`, `config/GlobalExceptionHandler.java`

---

## #P5-04b — [Backend] AI Diff Semantics Validator — LLM-Powered Issue-to-Code Alignment Check (5.4)
**Estimate:** 3 Points | **Assignee:** Batıkan

**Problem Summary:** Second half of AI validation — validates code changes against the JIRA issue
description. Adds `validateIssueDiff` to the `AiValidationService` created in #P5-04a.
Zero infrastructure duplication — reuses HTTP client, parser, error handler.
**Scope:** Backend — extend `AiValidationService` (same class as #P5-04a).

**Deliverables:**
1. `validateIssueDiff(String issueDescription, List<GithubFileDiffDto> fileDiffs)` → `AiValidationResult`:
   - Empty `fileDiffs` OR blank `issueDescription` → immediately return `SKIPPED`.
   - Prompt: *"Compare the following code diff to the issue description and respond with only one word: PASS if the changes clearly implement the issue, WARN if the match is partial or unclear, FAIL if the changes are unrelated to the issue."*
   - Concatenate all `fileDiff.patch` strings and issue description into a single LLM request.
   - Reuse HTTP client, response parser, error handler from #P5-04a — no copy-paste.

**References:** `endpoints_p5.md` sub-process 5.4; `docs/phase1_2.md` FR-20, IR-4.

**Acceptance Criteria:**
- [ ] Empty `fileDiffs` → `SKIPPED`, no HTTP call.
- [ ] Blank `issueDescription` → `SKIPPED`, no HTTP call.
- [ ] Reuses `AiValidationService` HTTP client and error handler — no duplicate timeout/error logic.
- [ ] `DIFF_VALIDATION_PROMPT` is a separate constant from `PR_REVIEW_PROMPT`.
- [ ] Unit tests: PASS, WARN, FAIL, empty diffs→SKIPPED, blank description→SKIPPED.

**Related Issues:**
- **Depends on (hard):** #P5-04a (must be merged — `validateIssueDiff` is added to `AiValidationService` class)
- **Depends on (hard):** #P5-03 (`GithubFileDiffDto` type definition)
- **Depends on (hard):** #P5-01 (`AiValidationResult` enum)
- **Blocks:** #P5-05 (orchestrator calls `validateIssueDiff`)
- **Codebase refs:** `service/AiValidationService.java` (created in #P5-04a)

---

## #P5-05 — [Backend] End-of-Sprint Tracking Orchestrator — Automated JIRA → GitHub → AI Pipeline
**Estimate:** 2 Points | **Assignee:** Egemen

**Problem Summary:** Wire sub-processes 5.1–5.4 into one `@Scheduled` daily job that fires at sprint
end, iterates all eligible groups, and persists results to `sprint_tracking_log`. Exact structural
pattern as `SanitizationService.java` (self-proxy + `REQUIRES_NEW` per group).
**Scope:** Backend — Scheduler / Orchestration.

**Deliverables:**
1. `SprintTrackingOrchestrator.runDailyTracking()` — `@Scheduled(cron = "0 0 1 * * *")`:
   - Find sprints where `endDate == LocalDate.now().minusDays(1)` via `SprintRepository.findByEndDate(yesterday)`.
   - **Group filter (spec Step 1):** `ProjectGroupRepository.findByTermIdAndStatusIn(termId, List.of(TOOLS_BOUND, ADVISOR_ASSIGNED))` — both statuses, not just ADVISOR_ASSIGNED.
   - Note: groups with no advisor (`group.getAdvisor() == null`) still get tracked — grading skipped for them by grading endpoints, not here.
2. Per-group atomic flow — wrap each group in its own try-catch (self-proxy + `REQUIRES_NEW`, identical to `SanitizationService`):
   ```
   JiraSprintService.fetchSprintStories(group)
     → save SprintTrackingLog rows (one per issue)
   for each issue where prMerged=true:
     GithubSprintService.fetchPRReviewComments + fetchFileDiffs
     AiValidationService.validatePRReview(comments)
       → update aiPrResult
     AiValidationService.validateIssueDiff(description, diffs)
       → update aiDiffResult
   for each issue where prMerged=false or null:
     → set aiPrResult=SKIPPED, aiDiffResult=SKIPPED
   ```
3. `triggerForSprint(UUID sprintId, boolean force)` — public entry point for coordinator:
   - `force=false`: throw `BusinessRuleException` if `sprint.endDate >= LocalDate.now()`.
   - Deletes existing `SprintTrackingLog` rows before re-fetching (idempotent).
   - Returns `SprintRefreshStats { int groupsProcessed; int issuesFetched; int aiValidationsRun; }`.
4. `AiValidationService` can be stubbed returning `PENDING` during development — inject via constructor.

**References:** `endpoints_p5.md` pipeline; `docs/phase1_2.md` Steps 1–7; NFR-2.

**Acceptance Criteria:**
- [ ] Scheduler fires only when a sprint's `endDate == LocalDate.now().minusDays(1)`.
- [ ] Groups at `TOOLS_BOUND` AND `ADVISOR_ASSIGNED` both included — not just ADVISOR_ASSIGNED.
- [ ] Group that throws during JIRA fetch does NOT block other groups (per-group try-catch verified in test).
- [ ] `group.getAdvisor() != null` used — not `group.getAdvisorId()` (no such field).
- [ ] `triggerForSprint(id, force=false)` throws `BusinessRuleException` when sprint not ended.
- [ ] Manual trigger deletes + recreates rows — idempotent (run twice = same result).

**Related Issues:**
- **Depends on (hard):** #P5-01 (`SprintTrackingLogRepository`, entities)
- **Depends on (hard):** #P5-02 (`JiraSprintService.fetchSprintStories`)
- **Depends on (hard):** #P5-03 (`GithubSprintService` — all 4 methods)
- **Depends on (soft):** #P5-04a + #P5-04b (stub as returning `PENDING` during dev)
- **Depends on (hard):** `ProjectGroupRepository.java` (P2, #125) — needs `findByTermIdAndStatusIn`
- **Depends on (hard):** `SprintRepository.java` — needs `findByEndDate(LocalDate)` (from #P5-01 deliverable 7)
- **Depends on (hard):** `exception/BusinessRuleException.java` (P2, merged)
- **Blocks:** #P5-07 (coordinator controller calls `triggerForSprint`), #P5-10 (QA tests pipeline)
- **Codebase refs:** `service/SanitizationService.java` (exact scheduler + self-proxy pattern), `repository/ProjectGroupRepository.java`, `entity/ProjectGroup.java` (`GroupStatus.TOOLS_BOUND`, `GroupStatus.ADVISOR_ASSIGNED`, `group.getAdvisor()`)

---

## #P5-06 — [Backend] Scrum Grading API — Advisor Point A/B Submission & Live Grade Retrieval
**Estimate:** 2 Points | **Assignee:** Yağmur

**Problem Summary:** Advisor-facing grading submission and read endpoints (DFD 5.5, Steps 8–10).
Upsert semantics. Advisor ownership enforced via `group.getAdvisor().getId()`.
Field names use spec-mandated `pointA_grade`/`pointB_grade` serialization.
**Scope:** Backend — Service + Controller Layer.

**Deliverables:**
1. `ScrumGradingService.submitGrade(UUID advisorId, UUID groupId, UUID sprintId, ScrumGradeValue pointAGrade, ScrumGradeValue pointBGrade)`:
   - Load group via `ProjectGroupRepository.findById(groupId)` → throw `GroupNotFoundException` if absent.
   - Ownership: `group.getAdvisor() == null || !group.getAdvisor().getId().equals(advisorId)` → throw `ForbiddenException`.
   - Upsert: `existsByGroupIdAndSprintId` → if exists update + set `updatedAt`; else create + set `gradedAt`.
2. `ScrumGradingService.getGrade(UUID advisorId, UUID groupId, UUID sprintId)`:
   - Same ownership check. Returns `Optional<ScrumGrade>`.
3. `ScrumGradingService.getActiveSprint()` — resolves active sprint for both advisor and student `GET /api/sprints/active` and `GET /api/advisor/sprints/active` endpoints.
4. **`AdvisorController`** (extend existing, `@RequestMapping("/api/advisor")`) — add all 5 advisor sprint/grading endpoints. Prefix matches — do NOT create a new `AdvisorSprintController`.
5. **`GroupController`** (extend existing, `@RequestMapping("/api/groups")`) — add `GET /{groupId}/sprints/{sprintId}/tracking`. Prefix matches.
6. **`StudentSprintController`** (new class, `@RequestMapping("/api/sprints")`) — add only `GET /api/sprints/active`. No existing controller owns the `/api/sprints` prefix.
7. **`CoordinatorSprintController`** handles coordinator endpoints — handled by #P5-07, do not add here.
8. Request DTO: `ScrumGradeRequest { @NotNull ScrumGradeValue pointAGrade; @NotNull ScrumGradeValue pointBGrade; }` — Jackson serializes as `pointA_grade`/`pointB_grade` via `@JsonProperty`.
   - Invalid enum deserialization → `HttpMessageNotReadableException` → `GlobalExceptionHandler` → 400. No custom exception needed.
9. Response DTOs: Lombok `@Data`, `@JsonProperty("pointA_grade")` / `@JsonProperty("pointB_grade")` on both entity fields and response DTOs.
10. `SecurityUtils.extractPrincipalUUID(Authentication auth)` — static helper in `util/SecurityUtils.java`. Called by `AdvisorController` and `GroupController` to extract the authenticated principal UUID. Removes extraction logic duplicated across both controllers. `getGroupTracking` signature: `SprintService.getGroupTracking(UUID studentId, UUID groupId, UUID sprintId)`.
11. `ProjectGroupRepository.findByAdvisor_IdAndTermId(UUID advisorId, String termId)` → `List<ProjectGroup>` — new derived query needed by `ScrumGradingService.getAdvisorGroupSummaries()`. Add to `ProjectGroupRepository.java` as part of this issue.

> **TODO — P4:** When committee assignment is built, tighten ownership check to verify `ADVISOR` role in committee, not `JURY`. Currently: `group.getAdvisor()` check is correct for the P3 codebase model.

**References:** `endpoints_p5.md` endpoints 1–7; `docs/phase1_2.md` Steps 8–10; FR-6, FR-7.

**Acceptance Criteria:**
- [ ] `POST .../grade` returns 201 on first submission, 200 on update (with non-null `updatedAt`).
- [ ] Response fields are `pointA_grade`/`pointB_grade` in JSON — verified in test with `jsonPath("$.pointA_grade")`.
- [ ] Returns 403 when `group.getAdvisor() == null` or ID mismatch — NOT via `group.getAdvisorId()` (no such field).
- [ ] `GET .../grade` returns 404 (not 200 empty) when no `ScrumGrade` row exists.
- [ ] Invalid grade value in request body → 400 via `HttpMessageNotReadableException`.
- [ ] `GET /api/sprints/active` returns 404 (not 500) when no active sprint found.

**Related Issues:**
- **Depends on (hard):** #P5-01 (`ScrumGrade` entity, `ScrumGradeRepository`, `ScrumGradeValue` enum)
- **Depends on (hard):** `ProjectGroupRepository.java` (P2, #125) — `findById`
- **Depends on (hard):** `SprintRepository.java` (Blue Team) — `findById` + active sprint query
- **Depends on (hard):** `ForbiddenException.java`, `GroupNotFoundException.java` (P2, merged)
- **Depends on (hard):** `GlobalExceptionHandler.java` (on main)
- **Blocks:** #P5-08 (advisor dashboard calls grading endpoints), #P5-09 (student tracking), #P5-11 (QA grading tests)
- **Codebase refs:** `entity/ProjectGroup.java` (field: `advisor` → `@ManyToOne StaffUser`), `exception/ForbiddenException.java`, `exception/GroupNotFoundException.java`, `config/GlobalExceptionHandler.java`

---

## #P5-07 — [Backend] Coordinator Sprint Control — Manual Pipeline Trigger & Sprint Overview API
**Estimate:** 2 Points | **Assignee:** Egemen

**Problem Summary:** Two coordinator-facing sprint management endpoints: manual pipeline trigger and
sprint overview. Follows the exact `SanitizationController` + `SanitizationTriggerRequest` pattern.
**Scope:** Backend — Controller Layer (new `CoordinatorSprintController` at `@RequestMapping("/api/coordinator")` — do NOT extend `CoordinatorController`).

**Deliverables:**
1. `POST /api/coordinator/sprints/{sprintId}/refresh`:
   - Optional `?force=true` query param (same pattern as `SanitizationController`).
   - Calls `SprintTrackingOrchestrator.triggerForSprint(sprintId, force)`.
   - Returns `SprintRefreshResponse { UUID sprintId; int groupsProcessed; int issuesFetched; int aiValidationsRun; LocalDateTime triggeredAt; }`.
2. `GET /api/coordinator/sprints/{sprintId}/overview`:
   - Aggregates `SprintTrackingLogRepository.findBySprintId(sprintId)` + `ScrumGradeRepository`.
   - One entry per group. Groups result by `groupId`. Includes `advisorName` (null for TOOLS_BOUND groups).
   - Returns `List<SprintGroupOverview>`: `groupId`, `groupName`, `advisorName`, `totalIssues`, `mergedPRs`, AI counts, `gradeSubmitted`, `pointA_grade`/`pointB_grade` (null if not graded).
3. DTOs with Lombok `@Data`. `@JsonProperty("pointA_grade")` / `@JsonProperty("pointB_grade")` on grade fields.

**References:** `endpoints_p5.md` endpoints 8–9; `docs/phase1_2.md` NFR-2.

**Acceptance Criteria:**
- [ ] `POST .../refresh` without `?force=true` returns 400 if sprint not ended (delegated to orchestrator `BusinessRuleException`).
- [ ] `POST .../refresh?force=true` triggers regardless of sprint end date.
- [ ] `GET .../overview` returns ALL `TOOLS_BOUND`+ groups — not filtered to any advisor.
- [ ] `gradeSubmitted=false` and null grade fields when no `ScrumGrade` row.
- [ ] Both endpoints return 403 for non-Coordinator JWT (enforced by `SecurityConfig` `/api/coordinator/**` guard).

**Related Issues:**
- **Depends on (hard):** #P5-05 (`SprintTrackingOrchestrator.triggerForSprint`)
- **Depends on (hard):** #P5-01 (`SprintTrackingLogRepository`, `ScrumGradeRepository`)
- **Depends on (hard):** `ProjectGroupRepository.java` (P2) — group names in overview
- **Blocks:** #P5-10 (QA tests manual refresh), #P5-12 (Postman coordinator folder)
- **Codebase refs:** `controller/SanitizationController.java` (exact POST trigger pattern), `controller/request/SanitizationTriggerRequest.java` (DTO pattern), `controller/response/SanitizationReport.java` (response DTO pattern)

---

## #P5-08 — [Frontend] Advisor Sprint Live Panel — Story Tracking, AI Badges & Grade Submission
**Estimate:** 3 Points | **Assignee:** Arda

**Problem Summary:** Advisor sprint dashboard — live group tracking data, AI result badges, and grade
submission form. FR-6 (live grades on advisor panel). Creates `AiResultBadge.vue` shared component
used by #P5-09.
**Scope:** Frontend — `pages/professor/sprint.vue` + `components/AiResultBadge.vue`.

**Deliverables:**
1. `components/AiResultBadge.vue` — standalone. Props: `result: 'PENDING'|'PASS'|'WARN'|'FAIL'|'SKIPPED'`.
   Colors: PASS=green, WARN=yellow, FAIL=red, PENDING=grey, SKIPPED=neutral-grey.
   **Define and commit this component by Day 2 so Zeynep can import it in #P5-09.**
2. `pages/professor/sprint.vue`:
   - Load: `GET /api/advisor/sprints/active` → sprint header.
   - Load: `GET /api/advisor/sprints/{sprintId}/groups` → group summary cards with `perStudentSummary`.
3. Per-group expandable card → lazy-load `GET .../groups/{groupId}/tracking` → per-issue table with `AiResultBadge` for both AI fields.
4. Grade form per group: dropdowns for `pointA_grade` + `pointB_grade` (values A/B/C/D/F).
   `POST .../grade`. On success: update inline via `ref` — no page reload.
5. Empty state: "Tracking data not yet available" when issues array empty.

**References:** `endpoints_p5.md` endpoints 1–5; `docs/phase1_2.md` FR-6, Steps 8–10.

**Acceptance Criteria:**
- [ ] `AiResultBadge.vue` in `components/` — not page-local. Single `result` prop. Committed before #P5-09 opens PR.
- [ ] Dashboard loads without crash when no tracking data (empty state, no console errors).
- [ ] Grade submission updates displayed grade inline.
- [ ] `pointA_grade`/`pointB_grade` used in POST body — not `pointA`/`pointB`.
- [ ] Page guarded: no Professor JWT → redirect `/auth/login`.

**Related Issues:**
- **Depends on (hard):** #P5-06 (grading endpoints), endpoints 1–3 live
- **Depends on (hard):** `composables/useApiClient.ts` (existing, established patterns)
- **Blocks:** #P5-09 (`AiResultBadge.vue` must exist and be committed before Zeynep imports it)
- **Codebase refs:** `pages/professor/` (existing layout), `composables/useApiClient.ts`, `pages/student/group/invitations.vue` (role-guarded page pattern)

---

## #P5-09 — [Frontend] Student Sprint Monitor — JIRA Story Completion & AI Validation Results View
**Estimate:** 3 Points | **Assignee:** Zeynep

**Problem Summary:** Student-facing sprint tracking page. Shows story completions and AI results.
Reuses `AiResultBadge.vue` from #P5-08.
**Scope:** Frontend — `pages/student/group/sprint.vue`.

**Deliverables:**
1. `pages/student/group/sprint.vue`:
   - `GET /api/sprints/active` → sprint header (dates, target SP, days remaining).
   - `groupId` from `GET /api/groups/my` (existing composable/store — NOT from URL params).
   - `GET /api/groups/{groupId}/sprints/{sprintId}/tracking` → issue table.
2. Issue table: Issue Key, Assignee (GitHub username), Story Points, PR Merged badge, AI PR Review (`AiResultBadge`), AI Diff Match (`AiResultBadge`).
3. Import `AiResultBadge.vue` from `components/` — **no redefinition**.
4. Empty state: "Tracking data will be available after the sprint ends."
5. No active sprint state: "No active sprint found" on 404.
6. Nav link from `pages/student/group/index.vue` to this page.

**References:** `endpoints_p5.md` endpoints 6–7; `docs/phase1_2.md` FR-7.

**Acceptance Criteria:**
- [ ] Page redirects to login if student JWT absent.
- [ ] "No active sprint" gracefully shown on 404 — no crash.
- [ ] Uses `components/AiResultBadge.vue` from #P5-08 — colors consistent.
- [ ] `groupId` from `GET /api/groups/my` — NOT from URL params.
- [ ] Empty state shown (not blank) when `issues` is empty.

**Related Issues:**
- **Depends on (hard):** #P5-08 (`AiResultBadge.vue` must be merged and available)
- **Depends on (hard):** endpoints 6–7 live (#P5-06 service + #P5-05 data)
- **Depends on (hard):** `pages/student/group/index.vue` (add nav link here)
- **Depends on (hard):** `composables/useApiClient.ts`
- **Codebase refs:** `pages/student/group/index.vue`, `pages/student/group/invitations.vue`, `composables/useApiClient.ts`

---

## #P5-10 — [QA] Sprint Pipeline Integration Tests — WireMock JIRA, GitHub & LLM End-to-End
**Estimate:** 3 Points | **Assignee:** Bilge

**Problem Summary:** End-to-end integration tests for the automated 5.1–5.4 pipeline. WireMock mocks
JIRA, GitHub, and LLM APIs. Verifies DB state after orchestrator run. Fault isolation is the
key test scenario — one failing group must not block others.
**Scope:** QA — `@SpringBootTest` integration tests.

**Deliverables:**
1. Test: sprint `endDate = yesterday` → `SprintTrackingLog` rows in DB for all `TOOLS_BOUND`+ groups.
2. Test: JIRA 401 → that group skipped; **other groups still have rows** (isolation verified via JDBC row count).
3. Test: no matching GitHub branch → `prMerged=null`, `aiPrResult=SKIPPED`, `aiDiffResult=SKIPPED`.
4. Test: PR found but not merged → `prMerged=false`, `aiPrResult=SKIPPED`.
5. Test: LLM API timeout (WireMock delay > 10s) → `aiPrResult=WARN`, orchestrator proceeds.
6. Test: coordinator manual refresh (`triggerForSprint(id, force=true)`) → deletes old rows, creates new (idempotent — JDBC row count verified both times).

**References:** `endpoints_p5.md` sub-process 5.1–5.4.

**Acceptance Criteria:**
- [ ] All 6 scenarios have dedicated test methods with JDBC/repository assertions on `SprintTrackingLog` rows.
- [ ] WireMock stubs: JIRA `/rest/agile/1.0/sprint/{id}/issue`, GitHub `/repos/...`, LLM endpoint.
- [ ] `@Transactional` rollback between tests — no state pollution.
- [ ] Orchestrator tested via `triggerForSprint` — do NOT rely on `@Scheduled` timing.
- [ ] Test 2: verifies OTHER groups have rows when one JIRA call fails — row count checked per group.
- [ ] Test 6 (idempotency): DB row count after second trigger equals row count after first trigger.

**Related Issues:**
- **Depends on (hard):** #P5-01 through #P5-07 (full pipeline wired)
- **Depends on (soft):** #P5-04a, #P5-04b (can use `PENDING` stub if AI not yet merged)
- **Codebase refs:** `service/SanitizationService.java` (scheduler pattern), existing WireMock test classes (merged PRs #128, #137)

---

## #P5-11 — [QA] Scrum Grading Role Guard, Upsert & Schema Tests
**Estimate:** 1 Point | **Assignee:** Demir

**Problem Summary:** Integration tests for scrum grading REST layer: ownership checks, upsert
correctness, and JSON schema field name verification. No external API mocking needed.
**Scope:** QA — `@SpringBootTest` + `MockMvc`.

**Deliverables:**
1. Test: advisor submits grade → 201 + `ScrumGrade` DB row with `gradedAt` set.
2. Test: advisor updates grade → 200 + `updatedAt` non-null in response.
3. Test: wrong professor (not group's advisor) → 403.
4. Test: invalid `ScrumGradeValue` (e.g. `"G"`) → 400.
5. Test: `GET .../grade` when no grade exists → 404.
6. Test: coordinator `GET .../overview` returns all groups; Student JWT on same endpoint → 403.

**Acceptance Criteria:**
- [ ] All 6 scenarios covered.
- [ ] Response JSON verified with `jsonPath("$.pointA_grade")` (not `$.pointA`) — spec field name confirmed.
- [ ] Role guard tests: Student JWT + Admin JWT on advisor endpoints — both get 403.
- [ ] Upsert test verifies `updatedAt != null` AND DB row count stays 1 after second POST.

**Related Issues:**
- **Depends on (hard):** #P5-01 (`ScrumGrade` entity, enums), #P5-06 (service + controller), #P5-07 (overview endpoint)
- **Codebase refs:** existing `@SpringBootTest` integration test base class, `AdvisorServiceTest.java` (P3 test pattern)

---

## #P5-12 — [QA] P5 API Postman Collection — Role-Gated Request Chains & Response Assertions
**Estimate:** 2 Points | **Assignee:** Demir

**Problem Summary:** Postman collection covering all 9 P5 REST endpoints with environment variables,
auth scripts, and automated response assertions. Primary manual QA and sprint demo tool.
**Scope:** QA — `postman`.

**Deliverables:**
1. `P5_Sprint_Tracking.postman_collection.json` in `red_notes/process5/postman/`.
2. `P5_Dev.postman_environment.json` — vars: `baseUrl`, `advisorToken`, `studentToken`, `coordinatorToken`, `sprintId`, `groupId`.
3. **Folders:**
   - `Advisor — Sprint Read` (endpoints 1–2)
   - `Advisor — Sprint Tracking Detail` (endpoint 3)
   - `Advisor — Scrum Grading` (endpoints 4–5; chained: POST → capture `gradeId` via `pm.environment.set`)
   - `Student — Sprint Tracking` (endpoints 6–7)
   - `Coordinator — Sprint Management` (endpoints 8–9)
4. Per-request test scripts: status code, required field presence, type checks.
   - Verify `pointA_grade` / `pointB_grade` field names (not `pointA`/`pointB`) on grade responses.
5. Each folder has a `403 Role Guard` request with wrong-role JWT asserting 403.

**Acceptance Criteria:**
- [ ] Collection imports into Postman without errors.
- [ ] All 9 endpoints have happy-path requests with passing test scripts.
- [ ] Each folder has a 403 role guard request.
- [ ] Running against seeded local backend produces 0 test failures on happy paths.
- [ ] Grade response assertions check `pointA_grade` field name explicitly.

**Related Issues:**
- **Depends on (hard):** ALL backend P5 issues (#P5-01 through #P5-07) deployed to `http://localhost:8080`
- **Codebase refs:** `red_notes/14.04.2026_api-docs.json` (API snapshot), `red_notes/open-api-p2-p3.yaml` (format ref)

---

## Dependency Graph

```
#P5-01 (Yağmur — Entities)
  ├── #P5-02 (Efecan — JIRA) ───────────┐
  ├── #P5-03 (Efecan — GitHub) ─────────┼──► #P5-05 (Egemen — Orchestrator) ──► #P5-07 (Egemen — Coord)
  ├── #P5-04a (Batu — AI PR Review) ────┘                                      ──► #P5-10 (Bilge — QA)
  ├── #P5-04b (Batıkan — AI Diff) ────── depends on #P5-04a + #P5-03
  ├── #P5-06 (Yağmur — Grading API) ────────────────────────────────────────► #P5-11 (Demir — QA)
  │     ├── #P5-08 (Arda — Advisor Frontend)
  │     │     └── #P5-09 (Zeynep — Student Frontend) [AiResultBadge.vue]
  │     └── #P5-09 (Zeynep — tracking endpoints)
  └── #P5-07 ──► #P5-12 (Demir — Postman)
```

## Critical Path

```
Day 1: #P5-01 (Yağmur) → unblocks everything. Chase Blue Team: Sprint.termId + SprintRepository.
Day 1: #P5-04a spike (Batu) — confirm LLM key read + decrypt + HTTP parsing works end-to-end.
Day 2: #P5-02 (Efecan), #P5-03 (Efecan), #P5-04a (Batu) in parallel.
        Arda commits AiResultBadge.vue standalone component today.
Day 3: #P5-04b (Batıkan, after #P5-04a + #P5-03). #P5-05 (Egemen, stub AI as PENDING).
        #P5-06 (Yağmur, only needs #P5-01). #P5-08 starts (Arda, mock API responses).
Day 4: #P5-07 (Egemen, after #P5-05). #P5-09 (Zeynep, after AiResultBadge.vue from #P5-08 merged).
Day 5: #P5-10, #P5-11, #P5-12 QA pass.
```

## Risk Notes

| Risk | Mitigation |
|------|------------|
| `Sprint.termId` missing (Blue Team) | Chase Day 1. Column **must be `nullable = true`** — Hibernate ALTER TABLE fails on existing rows if NOT NULL. Workaround until merged: `findAll()` + date filter. |
| `SprintRepository` bare | Add `findByEndDate(LocalDate)` in #P5-01 deliverable 7. |
| JIRA board/sprint resolution (no stored `jiraSprintId`) | `fetchSprintStories(group)` resolves internally: board lookup → active sprint → issues. Requires 3 WireMock stubs per test. |
| `GithubService` confusion | New `GithubSprintService`. Do NOT extend `GithubService`. |
| Field names: `encryptedJiraToken` not `jiraApiToken` | Documented in #P5-02 deliverable 2. |
| Field names: `encryptedGithubPat` not `githubPAT` | Documented in #P5-03 deliverable 1. |
| `group.advisor` is `@ManyToOne` not UUID | Use `group.getAdvisor().getId()` — documented in #P5-05, #P5-06. |
| `pointA_grade`/`pointB_grade` vs `pointA`/`pointB` | Spec-mandated names used throughout. QA verifies with jsonPath. |
| TOOLS_BOUND groups with no advisor in pipeline | Pipeline runs; grade endpoints enforce `group.getAdvisor() != null`. No NPE. |
| `AiResultBadge.vue` interface mismatch | Arda defines + commits Day 2, before Zeynep opens PR. |
| `llm_api_key` missing from `system_config` in dev | #P5-04a logs startup warning + returns `PENDING` in stub mode. |
| LLM response not one word | Prompt instructs "respond with only one word". Parser defaults to `WARN`. |
| #P5-04b blocks on #P5-04a merge | Batıkan (#P5-04b) waits; Efecan continues #P5-03 (GitHub service) in parallel while Batu finishes #P5-04a. |
| P4 committee check future conflict | TODO comment added to #P5-06. No action now — guard added in P4 sprint. |
