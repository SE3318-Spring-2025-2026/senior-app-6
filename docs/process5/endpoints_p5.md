# Process 5 — API Endpoints (MERGED FINAL)
## Sprint Tracking, Scrum Grading & AI Validation (Sub-Processes 5.1–5.5)


---

## Conventions

| Item | Rule |
|------|------|
| Primary keys | UUID |
| Timestamps | ISO-8601 (`LocalDateTime`) |
| Student JWT | `sub="Student"`, claim `id` (UUID of Student entity) |
| Staff JWT | `sub="StaffUser"`, claim `id` (UUID), claim `role` |
| Error body | `{ "message": "Human-readable message" }` |
| Grade values | Soft grading only: `A`, `B`, `C`, `D`, `F` |
| AI result values | `PENDING`, `PASS`, `WARN`, `FAIL`, `SKIPPED` |
| Grade request field A | `pointA_grade` (spec Steps 8–9, JSON via `@JsonProperty`) |
| Grade request field B | `pointB_grade` (spec Steps 8–9, JSON via `@JsonProperty`) |

### HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | OK / updated |
| 201 | Created (new resource) |
| 400 | Business rule violation |
| 403 | Role / ownership mismatch |
| 404 | Resource not found |

---

## Controller Architecture

Rule: **endpoint prefix determines the controller** — no new class if a matching base-path controller already exists.

| Controller | Base Path | Action | P5 Endpoints |
|---|---|---|---|
| `AdvisorController` | `/api/advisor` | **Extend existing** | All 5 advisor sprint/grading endpoints |
| `GroupController` | `/api/groups` | **Extend existing** | `GET /api/groups/{groupId}/sprints/{sprintId}/tracking` |
| `CoordinatorController` | `/api/coordinator` | **Extend existing** | `POST .../refresh`, `GET .../overview` |
| `SprintController` | `/api/sprints` | **New class** (new prefix, no existing controller) | `GET /api/sprints/active` only |

> **Principal extraction:** `AdvisorController`, `GroupController`, and `SprintController` all use `SecurityUtils.extractPrincipalUUID()`.
> This helper lives in `com.senior.spm.util.SecurityUtils` and is shared across all controllers.

---

## Architecture Note — Sub-processes 5.1–5.4 (Automated, No REST)

Sub-processes 5.1 (JIRA fetch), 5.2 (GitHub branch/PR match), 5.3 (AI PR review validation),
and 5.4 (AI diff validation) are **scheduler-driven only**. No student or advisor endpoint triggers them.
The Coordinator gets one manual override endpoint (5.7 below) to force a re-fetch.

Data flows through D5 (`sprint_tracking_log` table). Each sub-process writes its output to D5.
No process-to-process calls.

**Self-proxy + `REQUIRES_NEW` pattern** (same as `SanitizationService.java`):
- Outer method (`runDailyTracking`) — no transaction; calls self-proxy
- Per-group method (`processGroup`) — `@Transactional(propagation = REQUIRES_NEW)` — one commit per group
- `OptimisticLockingFailureException` per group → skip + log → continue loop

---

## New Entities Required

### `SprintTrackingLog`

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID PK | `@GeneratedValue(strategy = GenerationType.UUID)` |
| `group` | `@ManyToOne ProjectGroup` | NOT NULL |
| `sprint` | `@ManyToOne Sprint` | NOT NULL |
| `issueKey` | String | JIRA issue key e.g. `SPM-42`, NOT NULL |
| `assigneeGithubUsername` | String | NULLABLE — from JIRA assignee field |
| `storyPoints` | Integer | NULLABLE — from JIRA story points field |
| `prNumber` | Long | GitHub PR number, NULLABLE |
| `prMerged` | Boolean | null=no branch found, false=open/unmerged, true=merged |
| `aiPrResult` | `AiValidationResult` enum | default `PENDING` |
| `aiDiffResult` | `AiValidationResult` enum | default `PENDING` |
| `fetchedAt` | LocalDateTime | NOT NULL |

Unique constraint: `uq_stl_group_sprint_issue (group_id, sprint_id, issue_key)`.

> **Per-student aggregate** (spec Step 7: `studentId, issueKey, completedPoints, aiValidationResult, sprintId`):
> Computed at read time via repository GROUP BY projection on `assigneeGithubUsername`.
> No separate entity needed — service layer groups `SprintTrackingLog` rows by assignee.

### `ScrumGrade`

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID PK | |
| `group` | `@ManyToOne ProjectGroup` | NOT NULL |
| `sprint` | `@ManyToOne Sprint` | NOT NULL |
| `advisor` | `@ManyToOne StaffUser` | NOT NULL |
| `pointAGrade` | `ScrumGradeValue` enum | NOT NULL — `@JsonProperty("pointA_grade")` |
| `pointBGrade` | `ScrumGradeValue` enum | NOT NULL — `@JsonProperty("pointB_grade")` |
| `gradedAt` | LocalDateTime | NOT NULL |
| `updatedAt` | LocalDateTime | NULLABLE — set on update only |

Unique constraint: `uq_sg_group_sprint (group_id, sprint_id)`.

### New Enums

```java
// AiValidationResult
PENDING   // not yet processed by scheduler — default value
PASS      // AI confirmed valid
WARN      // AI found minor issues / superficial content
FAIL      // AI found substantive problems / empty review / unrelated diff
SKIPPED   // no branch / no merged PR found — AI check not applicable (no fault)

// ScrumGradeValue (Soft Grading per FR-2)
A   // 100
B   // 80
C   // 60
D   // 50
F   // 0
```



### New Repository Methods

**`SprintTrackingLogRepository`**

| Method | Used by |
|--------|---------|
| `findByGroupIdAndSprintId(UUID, UUID)` | tracking reads, grading ownership check |
| `findBySprintId(UUID)` | coordinator overview |
| `deleteBySprintId(UUID)` | coordinator refresh — deletes ALL rows for a sprint across all groups before re-run |
| `deleteByGroupIdAndSprintId(UUID, UUID)` | reserved for per-group re-fetch (future use) |

**Additions to existing repositories (no new classes):**

| Repository | Method to add | Used by |
|---|---|---|
| `ProjectGroupRepository` | `findByTermIdAndStatusIn(String, List<GroupStatus>)` | orchestrator group filter (5.0), coordinator overview (5.7) |
| `ProjectGroupRepository` | `findByAdvisor_IdAndTermId(UUID, String)` | advisor group list (5.5a) — **does not exist yet, add in #154** |
| `GroupMembershipRepository` | `existsByGroupIdAndStudentId(UUID, UUID)` | student membership guard (5.6) |
| `SprintRepository` | `findByEndDate(LocalDate)` | daily orchestrator trigger (5.0) |
| `SprintRepository` | `findByTermIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(String, LocalDate, LocalDate)` | active sprint lookup (5.5a, 5.6) |

**`ScrumGradeRepository`**

| Method | Used by |
|--------|---------|
| `findByGroupIdAndSprintId(UUID, UUID)` | advisor grade read / upsert check |
| `findByAdvisorIdAndSprintId(UUID, UUID)` | advisor dashboard (grade status per group) |
| `existsByGroupIdAndSprintId(UUID, UUID)` | upsert guard |

---

## 5.1 / 5.2 / 5.3 / 5.4 — Automated Sprint Tracking Pipeline

No REST endpoints. Implemented as `SprintTrackingOrchestrator` `@Scheduled` job.

**Trigger:** `@Scheduled(cron = "0 0 1 * * *")` — daily at 01:00 UTC (NFR-2).
Checks if any `Sprint.endDate == LocalDate.now().minusDays(1)`.

**Group filter (spec Step 1):** `TOOLS_BOUND` + `ADVISOR_ASSIGNED` groups for that sprint's term.
Groups with no advisor still get tracked — data is ready when advisor is assigned later.

**Per-group flow:**
```
5.1  JIRA API  → fetch all sprint stories → write SprintTrackingLog rows (one per issue)
     D5 read   → get issueKeys for this group/sprint
5.2  GitHub    → for each issueKey: search branches by prefix → fetch PR → check merged → update prNumber, prMerged in D5
     D5 read   → get prNumber, prMerged, issueKey rows where prMerged=true
5.3  AI API    → send PR review comments → get PASS/WARN/FAIL → update aiPrResult in D5
               → prMerged=false or null: set aiPrResult=SKIPPED (no comments to validate)
5.4  AI API    → send file diff + issue description → get PASS/WARN/FAIL → update aiDiffResult in D5
               → prMerged=false or null: set aiDiffResult=SKIPPED (no diff available)
```

**JIRA sprint resolution (no stored jiraSprintId):** `JiraSprintService.fetchSprintStories(group)` resolves the active sprint internally:
1. `GET /rest/agile/1.0/board?projectKeyOrId={group.jiraProjectKey}` → extract `boardId`
2. `GET /rest/agile/1.0/board/{boardId}/sprint?state=active` → extract `jiraSprintId`
3. `GET /rest/agile/1.0/sprint/{jiraSprintId}/issue` → fetch issues
If board not found or no active sprint → log WARN + return empty list (skip this group's JIRA fetch).

**JIRA credentials:** `group.jiraProjectKey` + `group.jiraEmail` + `SymmetricEncryptionService.decrypt(group.encryptedJiraToken)`.
**GitHub credentials:** `group.githubOrgName` + `SymmetricEncryptionService.decrypt(group.encryptedGithubPat)`.
**AI credentials:** `SystemConfigRepository.findById("llm_api_key")` + `SymmetricEncryptionService.decrypt(configValue)`.

> **Important:** `group.getAdvisor()` — advisor is `@ManyToOne StaffUser` (not a UUID field). Check `group.getAdvisor() != null` before accessing advisor properties.

---

## 5.5a — Advisor Reads Sprint Data

### `GET /api/advisor/sprints/active` — SP: 1
**Auth:** Staff JWT (Role = `Professor`) | **Issue:** #P5-06 service scope

Returns the currently active sprint. `termId` resolved server-side via `TermConfigService.getActiveTermId()`.
Returns 404 if no sprint is currently active for the term.

**Response 200:**
```json
{
  "sprintId": "uuid",
  "startDate": "2026-05-01",
  "endDate": "2026-05-14",
  "storyPointTarget": 20,
  "daysRemaining": 5
}
```

**Errors:**
```json
{ "message": "No active sprint found for the current term" }
```
HTTP 404

---

### `GET /api/advisor/sprints/{sprintId}/groups` — SP: 2
**Auth:** Staff JWT (Role = `Professor`) | **Issue:** #P5-06 service scope

Returns all groups the authenticated advisor is assigned to for the given sprint.
Includes aggregate tracking stats and current grade if submitted. Per-student summary computed via GROUP BY.

**Response 200:**
```json
[
  {
    "groupId": "uuid",
    "groupName": "string",
    "totalIssues": 8,
    "mergedPRs": 6,
    "aiPassCount": 5,
    "aiWarnCount": 1,
    "aiFailCount": 0,
    "aiPendingCount": 2,
    "aiSkippedCount": 0,
    "gradeSubmitted": true,
    "pointA_grade": "A",
    "pointB_grade": "B",
    "perStudentSummary": [
      {
        "assigneeGithubUsername": "student-gh",
        "completedPoints": 8,
        "aiValidationStatus": "PASS"
      }
    ]
  }
]
```

> `aiValidationStatus` per student = worst of `aiPrResult` and `aiDiffResult` for that student's issues.
> Priority: FAIL > WARN > SKIPPED > PASS > PENDING.

**Errors:**
```json
{ "message": "Sprint not found" }
```
HTTP 404

---

### `GET /api/advisor/sprints/{sprintId}/groups/{groupId}/tracking` — SP: 2
**Auth:** Staff JWT (Role = `Professor`, must be advisor for `groupId`) | **Issue:** #P5-06 service scope

Returns per-issue tracking detail for a specific group in the sprint.
Advisor uses this to review individual JIRA story completions and AI results before grading.

**Response 200:**
```json
{
  "groupId": "uuid",
  "sprintId": "uuid",
  "fetchedAt": "ISO-8601",
  "issues": [
    {
      "issueKey": "SPM-42",
      "assigneeGithubUsername": "student-gh",
      "storyPoints": 3,
      "prNumber": 12,
      "prMerged": true,
      "aiPrResult": "PASS",
      "aiDiffResult": "WARN"
    }
  ],
  "perStudentSummary": [
    {
      "assigneeGithubUsername": "student-gh",
      "completedPoints": 8,
      "aiValidationStatus": "WARN"
    }
  ]
}
```

**Errors:**
```json
{ "message": "You are not the advisor for this group" }
{ "message": "Sprint not found" }
{ "message": "Group not found" }
{ "message": "No tracking data found — sprint may not have been processed yet" }
```
HTTP 403 / 404 / 404 / 404

---

## 5.5b — Advisor Scrum Grading

### `POST /api/advisor/sprints/{sprintId}/groups/{groupId}/grade` — SP: 3
**Auth:** Staff JWT (Role = `Professor`, must be advisor for `groupId`) | **Issue:** #P5-06 controller scope

Submits or updates Point A (Scrum performance) and Point B (Work/Code Review quality).
**Upsert semantics:** creates on first call (201), updates on subsequent calls (200 + `updatedAt`).

> **TODO — P4 tightening:** Once committee assignment (P4) is built, this endpoint must verify the caller has the `ADVISOR` role within the committee, not just any `PROFESSOR`. For now, `group.getAdvisor().getId().equals(advisorId)` is the correct check.

**Request:**
```json
{
  "pointA_grade": "A",
  "pointB_grade": "B"
}
```

**Response 201 (new grade):**
```json
{
  "gradeId": "uuid",
  "groupId": "uuid",
  "sprintId": "uuid",
  "pointA_grade": "A",
  "pointB_grade": "B",
  "advisorId": "uuid",
  "gradedAt": "ISO-8601"
}
```

**Response 200 (updated):**
```json
{
  "gradeId": "uuid",
  "groupId": "uuid",
  "sprintId": "uuid",
  "pointA_grade": "A",
  "pointB_grade": "B",
  "advisorId": "uuid",
  "gradedAt": "ISO-8601",
  "updatedAt": "ISO-8601"
}
```

**Errors:**
```json
{ "message": "pointA_grade must be one of: A, B, C, D, F" }
{ "message": "pointB_grade must be one of: A, B, C, D, F" }
{ "message": "You are not the advisor for this group" }
{ "message": "Sprint not found" }
{ "message": "Group not found" }
```
HTTP 400 / 400 / 403 / 404 / 404

> Invalid enum value deserialization → `HttpMessageNotReadableException` → already handled by `GlobalExceptionHandler` → 400. No custom exception needed.

---

### `GET /api/advisor/sprints/{sprintId}/groups/{groupId}/grade` — SP: 1
**Auth:** Staff JWT (Role = `Professor`, must be advisor for `groupId`) | **Issue:** #P5-06 controller scope

Returns current submitted grade for the group/sprint pair.
Used by live dashboard to display grades immediately after submission (FR-6, Step 10).

**Response 200:**
```json
{
  "gradeId": "uuid",
  "pointA_grade": "A",
  "pointB_grade": "B",
  "gradedAt": "ISO-8601",
  "updatedAt": "ISO-8601 | null"
}
```

**Errors:**
```json
{ "message": "You are not the advisor for this group" }
{ "message": "No grade submitted yet for this group and sprint" }
{ "message": "Sprint not found" }
```
HTTP 403 / 404 / 404

---

## 5.6 — Student Sprint Tracking View

### `GET /api/sprints/active` — SP: 1
**Auth:** Student JWT | **Issue:** #P5-06 service scope (shared with advisor endpoint 1)

Returns the active sprint for the current term. Students use this to know their sprint context (FR-7).

**Response 200:**
```json
{
  "sprintId": "uuid",
  "startDate": "2026-05-01",
  "endDate": "2026-05-14",
  "storyPointTarget": 20,
  "daysRemaining": 5
}
```

**Errors:**
```json
{ "message": "No active sprint found for the current term" }
```
HTTP 404

---

### `GET /api/groups/{groupId}/sprints/{sprintId}/tracking` — SP: 2
**Auth:** Student JWT (must be member of `groupId`) | **Issue:** #P5-06 controller scope

Returns sprint tracking data for the student's group. AI validation results visible to students
per spec (Step 7 — no restriction on visibility). Returns empty `issues: []` (not 404) when
sprint not yet processed — student sees "data pending" state.

**Response 200:**
```json
{
  "groupId": "uuid",
  "sprintId": "uuid",
  "fetchedAt": "ISO-8601 | null",
  "issues": [
    {
      "issueKey": "SPM-42",
      "assigneeGithubUsername": "student-gh",
      "storyPoints": 3,
      "prNumber": 12,
      "prMerged": true,
      "aiPrResult": "PASS",
      "aiDiffResult": "WARN"
    }
  ]
}
```

> `fetchedAt` = `max(fetchedAt)` across all `SprintTrackingLog` rows for this group+sprint; `null` when `issues` is empty.
> Student view intentionally omits `perStudentSummary` — individual detail per-issue is shown directly.

**Errors:**
```json
{ "message": "You are not a member of this group" }
{ "message": "Sprint not found" }
```
HTTP 403 / 404

> Non-existent `groupId` also returns 403 (membership check returns false) — intentional, avoids leaking group existence.

> Empty `issues: []` with HTTP 200 — NOT 404 — when sprint not yet processed.

---

## 5.7 — Coordinator Sprint Override

### `POST /api/coordinator/sprints/{sprintId}/refresh` — SP: 2
**Auth:** Staff JWT (Role = `Coordinator`) | **Issue:** #P5-07

Manually triggers the full sprint tracking pipeline (5.1→5.4) for all `TOOLS_BOUND`+
`ADVISOR_ASSIGNED` groups. Deletes existing `SprintTrackingLog` rows and re-fetches from scratch.
Runs **synchronously** — returns after all groups processed.

`?force=true` bypasses the sprint-end-date guard (for mid-sprint coordinator override).

**Request:** _(no body)_

**Response 200:**
```json
{
  "sprintId": "uuid",
  "groupsProcessed": 12,
  "issuesFetched": 87,
  "aiValidationsRun": 74,
  "triggeredAt": "ISO-8601"
}
```

**Errors:**
```json
{ "message": "Sprint not found" }
{ "message": "Sprint has not ended yet — use ?force=true to refresh early" }
```
HTTP 404 / 400

> Pattern follows `SanitizationController.java` exactly (`force` flag, delegates to service method).

---

### `GET /api/coordinator/sprints/{sprintId}/overview` — SP: 2
**Auth:** Staff JWT (Role = `Coordinator`) | **Issue:** #P5-07

Returns high-level summary of ALL groups' sprint tracking and grading status.
Coordinator sees every group — not filtered to any advisor's groups.

**Response 200:**
```json
{
  "sprintId": "uuid",
  "groups": [
    {
      "groupId": "uuid",
      "groupName": "string",
      "advisorName": "string | null",
      "totalIssues": 8,
      "mergedPRs": 6,
      "aiPassCount": 5,
      "aiWarnCount": 0,
      "aiFailCount": 1,
      "aiPendingCount": 2,
      "aiSkippedCount": 0,
      "gradeSubmitted": false,
      "pointA_grade": null,
      "pointB_grade": null
    }
  ]
}
```

> `advisorName` null for TOOLS_BOUND groups (no advisor yet). `gradeSubmitted=false` / null grades if no `ScrumGrade` row.

**Errors:**
```json
{ "message": "Sprint not found" }
```
HTTP 404

---

## Endpoint Summary Table

| # | Method | Path | Auth | Sub-process | Issue | SP |
|---|--------|------|------|-------------|-------|----|
| 1 | GET | `/api/advisor/sprints/active` | Staff (Professor) | 5.5 | #P5-06 | 1 |
| 2 | GET | `/api/advisor/sprints/{sprintId}/groups` | Staff (Professor) | 5.5 | #P5-06 | 2 |
| 3 | GET | `/api/advisor/sprints/{sprintId}/groups/{groupId}/tracking` | Staff (Professor) | 5.5 | #P5-06 | 2 |
| 4 | POST | `/api/advisor/sprints/{sprintId}/groups/{groupId}/grade` | Staff (Professor) | 5.5 | #P5-06 | 3 |
| 5 | GET | `/api/advisor/sprints/{sprintId}/groups/{groupId}/grade` | Staff (Professor) | 5.5 | #P5-06 | 1 |
| 6 | GET | `/api/sprints/active` | Student | 5.6 | #P5-06 | 1 |
| 7 | GET | `/api/groups/{groupId}/sprints/{sprintId}/tracking` | Student (member) | 5.6 | #P5-06 | 2 |
| 8 | POST | `/api/coordinator/sprints/{sprintId}/refresh` | Staff (Coordinator) | 5.7 | #P5-07 | 2 |
| 9 | GET | `/api/coordinator/sprints/{sprintId}/overview` | Staff (Coordinator) | 5.7 | #P5-07 | 2 |

**REST Total SP: 16 | Scheduler / Services SP: 15 | Grand Total: ~31 SP** _(see estimated_issues_p5.md)_

---

## Security Notes

| Rule | Detail |
|------|--------|
| `/api/advisor/sprints/**` | Staff JWT with `role=PROFESSOR` only — already wired in `SecurityConfig` (`/api/advisor/**` → PROFESSOR) |
| `/api/coordinator/sprints/**` | Staff JWT with `role=COORDINATOR` only — already wired (`/api/coordinator/**` → COORDINATOR) |
| `/api/sprints/active` | Student JWT only |
| `/api/groups/{groupId}/sprints/**` | Student JWT; ownership check: student must be member of `groupId` |
| AI validation results | Visible to students per spec (Step 7). AI never called on student request — scheduler/coordinator only |
| LLM API key | Stored in `system_config` as `"llm_api_key"` — AES-256 encrypted via `SymmetricEncryptionService`. Never exposed in any response. |
| Advisor ownership check | `group.getAdvisor() == null \|\| !group.getAdvisor().getId().equals(advisorId)` → 403 |
| P4 future | When committee (P4) is built: tighten to ADVISOR committee role only, reject JURY role submitting grades |

---

## Codebase Sync Notes

| Pattern | Correct reference |
|---------|-------------------|
| Encryption service | `SymmetricEncryptionService.decrypt(String cipherTextWithIvBase64)` — NOT `EncryptionService` |
| GitHub HTTP client | `RestClient.Builder` (Spring injection) — same pattern as `GithubService.java`; do NOT extend that class |
| JIRA token field | `group.encryptedJiraToken` (NOT `jiraApiToken`) |
| GitHub PAT field | `group.encryptedGithubPat` (NOT `githubPAT`) |
| GitHub org field | `group.githubOrgName` |
| Advisor field | `group.getAdvisor()` → `@ManyToOne StaffUser` (NOT a UUID field; access `.getId()`) |
| Scheduler pattern | `SanitizationService.java` — self-proxy + `REQUIRES_NEW` per group |
| Coordinator trigger | `SanitizationController.java` — `@PostMapping`, optional body, `force` flag |
| Principal extraction | `SecurityUtils.extractPrincipalUUID()` (new shared util) — do NOT copy-paste the private helper from `AdvisorController`/`GroupController` |
| Path variable naming | P5 coordinator endpoints use `{sprintId}`; existing `CoordinatorController` uses `{id}` — both are valid, `{sprintId}` is preferred for new code |
