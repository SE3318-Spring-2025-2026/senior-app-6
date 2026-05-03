# Sprint 4 — Issue Backlog
> Covers: Schedule Window CRUD, Advisor Capacity fixes, Coordinator Sprint Monitor UI,
> and all Process 7 (Rubric Grading & Final Grade Calculation) issues.

---

## Point Summary by Assignee

| Assignee | Role | Issues | Points |
|----------|------|--------|--------|
| Batıkan | Backend | #239, #246, #247, #248 | 4 |
| Egemen | Backend | #240, #243, #256 | 5 |
| Zeynep | Frontend | #241, #244 | 5 |
| Arda | Frontend | #242, #252 | 4 |
| Efecan | Fullstack | #250, #257 | 4 |
| Yağmur | Fullstack | #249, #251 | 4 |
| Batuhan | QA | #253, #255 | 3 |
| Bilge | QA | #254, #258 | 4 |
| Demir | QA + PR Review | #245 | 2 |
| **Total** | | | **35** |

> Demir carries all PR review overhead for the sprint (~4pt untracked) — formal story points intentionally lower.
> Blue Team's `blue_main_sprint4` branch (with P6 `DeliverableSubmission`) will be merged
> into our branch before starting #247/#249 — no external blockers.

---

## Priority Legend

| Badge | Meaning |
|-------|---------|
| 🔴 Critical | Blocks student/professor workflows; must ship Sprint 4 |
| 🟠 High | Core Sprint 4 deliverable |
| 🟡 Medium | Valuable; complete if time allows |

---

## S4-01 — [BUG] DB-level default for `advisor_capacity`

| Field | Value |
|-------|-------|
| **Assignee** | Batıkan |
| **Priority** | 🟠 High |
| **Story Points** | 1 |
| **Labels** | `bug`, `backend`, `data-layer` |

**Problem:**
`StaffUser.advisorCapacity` has a Java field default of `5` but the DB column is
`INT NOT NULL` with no `DEFAULT`. Raw SQL inserts in `data.sql` omit this column, causing
`Field 'advisor_capacity' doesn't have a default value` on fresh MySQL in strict mode.

**Fix:**

`StaffUser.java`:
```java
// Before
@Column(nullable = false)
private int advisorCapacity = 5;

// After
@Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 5")
private int advisorCapacity = 5;
```

`data.sql` — add `advisor_capacity, 5` to all three existing `staff_user` INSERT column lists
(admin seed, coordinator seed, professor seed).

**Acceptance Criteria:**
- [ ] Fresh DB creation results in `advisor_capacity INT NOT NULL DEFAULT 5` in DDL
- [ ] `data.sql` runs without error on an empty DB
- [ ] Existing tests unaffected

**Files:** `StaffUser.java`, `data.sql`

---

## S4-02 — [Backend] Advisor capacity — set at registration and update after

| Field | Value |
|-------|-------|
| **Assignee** | Egemen |
| **Priority** | 🟡 Medium |
| **Story Points** | 2 |
| **Labels** | `feature`, `backend`, `coordinator`, `p3` |

**Problem:**
`RegisterProfessorRequest` only accepts `mail`. There is no way to set a custom capacity at
registration time or update it after. The coordinator can see capacity in
`GET /api/coordinator/advisors` but cannot change it.

**Deliverables:**

1. `RegisterProfessorRequest.java` — add optional `capacity` field (`@Min(1)`, `@Max(20)`).
2. `StaffUserService.registerProfessor(String mail, Integer capacity)` — apply when not null.
3. `AdminController` — pass `request.getCapacity()` to service.
4. New `UpdateAdvisorCapacityRequest.java` (`@NotNull @Min(1) @Max(20) Integer capacity`).
5. New endpoint `PATCH /api/coordinator/advisors/{advisorId}/capacity` in `CoordinatorController`:
   - 404 if advisor not found.
   - 400 if target StaffUser is not `Role.Professor`.
   - 200 with updated `AdvisorCapacityResponse`.

**Acceptance Criteria:**
- [ ] `POST /api/admin/register-professor` with `capacity: 3` → stored as `advisorCapacity = 3`
- [ ] Without `capacity` → defaults to `5`
- [ ] `capacity: 0` → 400
- [ ] `PATCH /api/coordinator/advisors/{id}/capacity` updates immediately
- [ ] PATCH on non-Professor StaffUser UUID → 400
- [ ] Non-coordinator PATCH → 403

**Files:**
`RegisterProfessorRequest.java` (MOD), `StaffUserService.java` (MOD),
`AdminController.java` (MOD), `UpdateAdvisorCapacityRequest.java` (NEW),
`CoordinatorController.java` (MOD)

---

## S4-03 — [Frontend] Advisor capacity inputs in admin + coordinator panels

| Field | Value |
|-------|-------|
| **Assignee** | Zeynep |
| **Priority** | 🟡 Medium |
| **Story Points** | 2 |
| **Labels** | `feature`, `frontend`, `coordinator`, `admin` |

**Depends on:** #240

**Deliverables:**

1. Admin professor registration form — add optional number input (`min=1`, `max=20`,
   placeholder `"5"`, label "Advisor Capacity (optional, default 5)"). Include `capacity`
   in POST body only when filled.
2. Coordinator advisor list — for each row add an inline "Edit" button that reveals a
   number input + "Save" → `PATCH /api/coordinator/advisors/{advisorId}/capacity`.
   Refresh the row on success.
3. `useApiClient.ts` — add `updateAdvisorCapacity(advisorId, capacity, token)`.

**Acceptance Criteria:**
- [ ] Registration with no capacity → API receives no `capacity` field (backend defaults to 5)
- [ ] Registration with `capacity: 3` → advisor stored with capacity 3
- [ ] Coordinator list "Edit" saves and updates displayed capacity without full reload
- [ ] Input rejects values outside 1–20 (HTML + API error shown)

**Files:** Admin registration form (MOD), coordinator advisor list page (MOD),
`useApiClient.ts` (MOD)

---

## S4-04 — [Frontend] Sprint monitoring UI — refresh pipeline + group overview

| Field | Value |
|-------|-------|
| **Assignee** | Arda |
| **Priority** | 🟠 High |
| **Story Points** | 2 |
| **Labels** | `feature`, `frontend`, `coordinator`, `p5` |

**Context:**
Both backend endpoints are already implemented and tested:
- `POST /api/coordinator/sprints/{sprintId}/refresh?force=true` — returns `SprintRefreshResult`
- `GET /api/coordinator/sprints/{sprintId}/overview` — returns `SprintOverviewResult`

Neither is wired to the frontend. No coordinator page or `useApiClient` function calls them.

**Deliverables:**

1. `types/sprint.ts` — add types:
   ```ts
   SprintRefreshResult { sprintId, groupsProcessed, issuesFetched, aiValidationsRun, triggeredAt }
   SprintGroupOverview { groupId, groupName, advisorEmail, totalIssues, mergedPRs,
                         aiPassCount, aiWarnCount, aiFailCount, aiPendingCount, aiSkippedCount,
                         gradeSubmitted, pointA_grade, pointB_grade }
   SprintOverviewResult { sprintId, groups: SprintGroupOverview[] }
   ScrumGradeValue = 'A' | 'B' | 'C' | 'D' | 'F'
   ```
2. `useApiClient.ts` — add `triggerSprintRefresh(sprintId, force, token)` and
   `fetchSprintOverview(sprintId, token)`.
3. New page `coordinator/sprint-monitor.vue`:
   - Sprint selector dropdown (reuse `fetchSprints`).
   - "Trigger Refresh" button → POST, shows `groupsProcessed / issuesFetched / aiValidationsRun` banner.
   - Overview table: one row per group — merged PRs, AI PASS/WARN/FAIL counts (colored),
     grade submitted badge (`A/B` if graded, `—` if not, `—` if no advisor).

**Acceptance Criteria:**
- [ ] Selecting a sprint auto-loads the overview table
- [ ] "Trigger Refresh" sends `?force=true`, shows result stats, re-fetches overview
- [ ] Button is disabled with spinner during POST
- [ ] AI counts shown with distinct colors (green/amber/red/gray)
- [ ] Grade column shows `A / B` when graded; `—` when not
- [ ] Page guarded for Coordinator role

**Files:** `types/sprint.ts` (MOD), `useApiClient.ts` (MOD),
`pages/coordinator/sprint-monitor.vue` (NEW)

---

## S4-05 — [Backend] Schedule window CRUD backend

| Field | Value |
|-------|-------|
| **Assignee** | Egemen |
| **Priority** | 🔴 Critical |
| **Story Points** | 2 |
| **Labels** | `feature`, `backend`, `coordinator`, `p2`, `p3`, `critical` |

**Problem:**
Without a schedule window, all P2 group creation/invitations and all P3 advisor association
requests throw `ScheduleWindowClosedException`. The only workaround is a direct MySQL insert.
Also: `AdvisorService` window check has a bug — it ignores `opensAt`, treating a window as
active before it officially starts.

**Deliverables:**

1. **Bug fix** in `AdvisorService` — add `opensAt` check:
   ```java
   if (window.getOpensAt().isAfter(now) || window.getClosesAt().isBefore(now)) {
       throw new ScheduleWindowClosedException("...");
   }
   ```
2. `ScheduleWindowRequest.java` — `type (WindowType)`, `opensAt`, `closesAt` (all `@NotNull`).
3. `ScheduleWindowResponse.java` — `id`, `type`, `termId`, `opensAt`, `closesAt`, `isActive`.
4. Three new endpoints in `CoordinatorController` (inject `ScheduleWindowRepository`):
   - `GET /api/coordinator/schedule-windows` — returns exactly **2 entries** (one per WindowType),
     even if not configured yet (null `id`/dates, `isActive: false`). `termId` from
     `TermConfigService`.
   - `POST /api/coordinator/schedule-windows` — upsert: same `termId + type` updates existing
     window. `termId` resolved server-side. `closesAt <= opensAt` → 400.
   - `DELETE /api/coordinator/schedule-windows/{id}` — 204. 404 if not found.

**Acceptance Criteria:**
- [ ] `GET` always returns 2 entries regardless of what's in DB
- [ ] `POST` creates or updates (no duplicate-type error)
- [ ] `POST` with `closesAt <= opensAt` → 400
- [ ] `DELETE` → 204; subsequent student actions get `ScheduleWindowClosedException`
- [ ] `AdvisorService` throws if `now < window.opensAt` (bug fix)
- [ ] Non-coordinator calls → 403

**Files:**
`AdvisorService.java` (MOD — bug fix), `ScheduleWindowRequest.java` (NEW),
`ScheduleWindowResponse.java` (NEW), `CoordinatorController.java` (MOD)

---

## S4-06 — [Frontend] Schedule window management page

| Field | Value |
|-------|-------|
| **Assignee** | Zeynep |
| **Priority** | 🔴 Critical |
| **Story Points** | 3 |
| **Labels** | `feature`, `frontend`, `coordinator`, `p2`, `p3`, `critical` |

**Depends on:** #243

**Deliverables:**

1. `types/scheduleWindow.ts` (new file):
   ```ts
   WindowType = 'GROUP_CREATION' | 'ADVISOR_ASSOCIATION'
   ScheduleWindowItem { id, type, termId, opensAt, closesAt, isActive }
   ScheduleWindowPayload { type, opensAt, closesAt }
   ```
2. `useApiClient.ts` — add `fetchScheduleWindows()`, `upsertScheduleWindow(payload)`,
   `deleteScheduleWindow(id)`.
3. New page `pages/coordinator/schedule.vue`:
   - Two-row card table (GROUP_CREATION / ADVISOR_ASSOCIATION).
   - Badges: **OPEN** (green), **CLOSED** (amber), **NOT SET** (gray).
   - "Open Window" / "Edit Window" → datetime-picker modal → upsert.
   - "Close" button → confirm dialog → DELETE.
   - Auto-refresh every 60s (`setInterval` / `onUnmounted` cleanup).

**Acceptance Criteria:**
- [ ] Correct badge per window state (OPEN / CLOSED / NOT SET)
- [ ] "Close" shows confirm dialog before DELETE
- [ ] Modal accepts `opensAt` and `closesAt`; validates `closesAt > opensAt` before submit
- [ ] Page auto-refreshes every 60s
- [ ] Page guarded for Coordinator role

**Files:** `types/scheduleWindow.ts` (NEW), `useApiClient.ts` (MOD),
`pages/coordinator/schedule.vue` (NEW)

---

## S4-07 — [QA] Integration tests for schedule_window + advisor_capacity endpoints

| Field | Value |
|-------|-------|
| **Assignee** | Demir |
| **Priority** | 🟠 High |
| **Story Points** | 2 |
| **Labels** | `qa`, `testing`, `backend`, `integration` |

**Depends on:** #239, #240, #243

**Deliverables:**

1. `ScheduleWindowControllerTest` (Spring Boot integration test, real H2):
   - `GET` with no windows → 2 entries, both `isActive: false`, null `id`
   - `POST` GROUP_CREATION window → 201; second POST same type → 200 (upsert, not duplicate)
   - `POST` with `closesAt <= opensAt` → 400
   - `DELETE` existing → 204; second DELETE → 404
   - Non-coordinator → 403 on POST and DELETE
   - `AdvisorService` window check fires before `opensAt`: create window starting tomorrow,
     send advisor request → `ScheduleWindowClosedException` (423 or 400)

2. `AdvisorCapacityControllerTest` additions:
   - Register professor with `capacity: 3` → stored as `advisorCapacity = 3`
   - Register professor without `capacity` → `advisorCapacity = 5`
   - `PATCH /api/coordinator/advisors/{id}/capacity` with `8` → 200, stored
   - PATCH non-Professor UUID → 400
   - PATCH unknown UUID → 404
   - Fresh DB: `data.sql` runs without column error (verify `advisor_capacity = 5` in all seeds)

**Acceptance Criteria:**
- [ ] All test cases listed above pass
- [ ] Tests use isolated H2 schema (no shared state with other test classes)
- [ ] No test relies on fixed UUIDs from `data.sql` — use `@BeforeEach` setup

**Files:** `ScheduleWindowControllerTest.java` (NEW),
`AdvisorCapacityControllerTest.java` (NEW or MOD existing admin controller test)

---

## P7-01 — [Backend] FinalGrade Entity & Repository

| Field | Value |
|-------|-------|
| **Assignee** | Batıkan |
| **Priority** | 🟠 High |
| **Story Points** | 1 |
| **Labels** | `feature`, `backend`, `data-layer`, `p7` |

**Problem:** No persistence layer for calculated final grades. Required by #250.

**Deliverables:**
1. `FinalGrade` entity: `id` (UUID PK), `student` (@OneToOne, NOT NULL),
   `group` (@ManyToOne ProjectGroup, NOT NULL), `termId` (String, NOT NULL),
   `weightedTotal` (BigDecimal), `completionRatio` (BigDecimal),
   `finalGrade` (BigDecimal), `calculatedAt` (LocalDateTime).
   Unique constraint: `uq_fg_student_term (student_id, term_id)`. **No `deliverableId` field.**
2. `FinalGradeRepository`:
   - `Optional<FinalGrade> findByStudent_IdAndTermId(UUID studentEntityUUID, String termId)`
   - `Optional<FinalGrade> findByStudent_StudentId(String studentId11Digit)`

**Acceptance Criteria:**
- [ ] Table created by Hibernate with `uq_fg_student_term` unique constraint on `(student_id, term_id)`
- [ ] No `deliverable_id` column exists
- [ ] Both repository methods work

**Files:** `FinalGrade.java` (NEW), `FinalGradeRepository.java` (NEW)

---

## P7-02 — [Backend] RubricGrade Entity & Repository

| Field | Value |
|-------|-------|
| **Assignee** | Batıkan |
| **Priority** | 🟠 High |
| **Story Points** | 1 |
| **Labels** | `feature`, `backend`, `data-layer`, `p7` |

**Deliverables:**
1. `RubricGrade` entity: `id` (UUID PK), `submission` (@ManyToOne DeliverableSubmission, NOT NULL),
   `criterion` (@ManyToOne RubricCriterion, NOT NULL), `reviewer` (@ManyToOne StaffUser, NOT NULL),
   `selectedGrade` (String, NOT NULL), `gradedAt` (LocalDateTime, NOT NULL).
   Unique constraint: `uq_rg_submission_criterion_reviewer (submission_id, criterion_id, reviewer_id)`.
2. `RubricGradeRepository`:
   - `List<RubricGrade> findBySubmissionId(UUID submissionId)`
   - `Optional<RubricGrade> findBySubmissionIdAndCriterionIdAndReviewerId(...)`

**Acceptance Criteria:**
- [ ] FK to `deliverable_submission` table resolves correctly
- [ ] Unique constraint prevents same-reviewer duplicate per criterion

**Files:** `RubricGrade.java` (NEW), `RubricGradeRepository.java` (NEW)

---

## P7-03 — [Backend] GradeValueMapper Utility

| Field | Value |
|-------|-------|
| **Assignee** | Batıkan |
| **Priority** | 🟠 High |
| **Story Points** | 1 |
| **Labels** | `feature`, `backend`, `utility`, `p7` |

**Problem:** No numeric mapping for `ScrumGradeValue` or rubric grades exists anywhere.
Required by #249 and #250 before any grade calculation can work.

**Deliverables:**
1. Extend `ScrumGradeValue` enum — add `toNumeric()` instance method:
   `A`→100, `B`→80, `C`→60, `D`→50, `F`→0 (per FR-2 in `docs/phase1_2.md`).
2. `GradeValueMapper` static utility class:
   - `validateGrade(GradingType type, String value) → boolean`
     Binary: only `"S"`, `"F"` valid. Soft: only `"A"`, `"B"`, `"C"`, `"D"`, `"F"`.
   - `toNumeric(GradingType type, String value) → int`
     Binary: S→100, F→0. Soft: same as ScrumGradeValue.
3. Unit tests: all valid combinations pass; each invalid combination returns false.

**Acceptance Criteria:**
- [ ] `ScrumGradeValue.B.toNumeric()` == 80
- [ ] `GradeValueMapper.validateGrade(Binary, "A")` == false
- [ ] `GradeValueMapper.toNumeric(Soft, "C")` == 60

**Files:** `ScrumGradeValue.java` (MOD), `GradeValueMapper.java` (NEW),
`GradeValueMapperTest.java` (NEW)

---

## P7-04 — [Backend] Rubric Grading Service & Controller (7.1)

| Field | Value |
|-------|-------|
| **Assignee** | Yağmur |
| **Priority** | 🟠 High |
| **Story Points** | 2 |
| **Labels** | `feature`, `backend`, `service`, `p7` |

**Depends on:** #247 (RubricGrade entity)

**Deliverables:**
1. `RubricGradingService.submitGrades(submissionId, reviewerStaffUserId, grades)`:
   - Resolve `DeliverableSubmission` → get `deliverableId` + `groupId` (404 if missing).
   - Committee auth check: `CommitteeRepository.findByDeliverableIdAndGroupIdAndProfessorId(...)`.
     New repository method. If empty → 403. Committee is **deliverable-scoped** — a professor
     on the Proposal committee cannot grade the SoW submission.
   - Grade validation per criterion (`GradeValueMapper.validateGrade`): Binary → {S,F},
     Soft → {A,B,C,D,F}. 400 with criterion ID on mismatch.
   - Upsert `RubricGrade` rows (unique on submission+criterion+reviewer).
   - Compute `baseDeliverableGrade (B)`:
     `B = AVG_reviewers(SUM_criteria(toNumeric(grade) × criterion.weight) / SUM(weight))`
2. `GradingController` exposing `POST /api/submissions/{submissionId}/grade`.
   Returns 201 on first grade, 200 on update.

**Acceptance Criteria:**
- [ ] 403 if professor is in Proposal committee but tries to grade SoW submission
- [ ] 400 if Binary criterion receives `"A"`
- [ ] Repeated grading upserts (no duplicate rows)
- [ ] `baseDeliverableGrade` is mathematically correct with multiple reviewers
- [ ] 404 if `submissionId` not found

**Files:** `RubricGradingService.java` (NEW), `GradingController.java` (NEW),
`CommitteeRepository.java` (MOD — new method)

---

## P7-05 — [Backend] Final Grade Calculation Engine (7.2)

| Field | Value |
|-------|-------|
| **Assignee** | Efecan |
| **Priority** | 🟠 High |
| **Story Points** | 3 |
| **Labels** | `feature`, `backend`, `service`, `p7` |

**Depends on:** #246, #248. (#249 dependency can be stubbed: `B = 0` until 7.1 is ready)

**Deliverables:**
`FinalGradeCalculationService.calculateFinalGrade(String studentId11Digit)`:

1. Resolve student via `StudentRepository.findByStudentId()` → get `student.githubUsername`.
2. Resolve group via `GroupMembershipRepository.findByStudentId(student.id)`.
3. For each deliverable in the term:
   - Contributing sprints: `SprintDeliverableMappingRepository.findAllByDeliverableId()`
   - **ScrumScalar** = `AVG(ScrumGrade.pointAGrade.toNumeric()) / 100` across contributing sprints.
     Skip sprints with no `ScrumGrade`. Use plain average (not weighted by `contributionPercentage`
     — add `// TODO: weighted option` comment).
   - **ReviewScalar** = same with `pointBGrade`.
   - **DS** = `(ScrumScalar + ReviewScalar) / 2.0`
   - **B** = computed from `RubricGrade` data (stub as 0.0 until #249 ships).
   - **ScaledGrade** = `B × DS`
   - **WeightedTotal** += `ScaledGrade × (deliverable.weight / 100)`
4. **C_i**: filter `SprintTrackingLog` for group by `assigneeGithubUsername == student.githubUsername`
   AND `prMerged == true`. Sum `storyPoints` (null → 0). Divide by sum of
   `Sprint.storyPointTarget` across all term sprints. If target = 0 → C_i = 0. **Do not cap at 1.0.**
5. **G_i** = `WeightedTotal × C_i`. Upsert to `FinalGrade` table.
6. `GradingController` — `GET /api/students/{studentId}/grade/calculate`:
   - `{studentId}` is the **11-digit student number** (not a UUID). Pattern `^[0-9]{11}$`.
   - Auth: Coordinator → allowed. Professor → allowed if `group.advisorId == callerUUID`.
     Student → resolve caller UUID from JWT `id` claim → compare `student.studentId` to path param.
   - Returns `FinalGradeResponse` with `deliverableBreakdown[]` (on-the-fly, not persisted).

**Edge cases (must handle):**
- Sprint with no `ScrumGrade` → skip from scalar
- Deliverable with no submission → `B = 0`
- No tracking logs → `C_i = 0`
- `targetStoryPoints = 0` → `C_i = 0`

**Acceptance Criteria:**
- [ ] Formulas match `phase1_2.md` Steps 3–11 exactly
- [ ] C_i uses `assigneeGithubUsername`, not student ID
- [ ] Student A calling for Student B → 403
- [ ] Professor not advisor → 403
- [ ] GET endpoint upserts to `FinalGrade` table (verified by DB assertion)
- [ ] `deliverableBreakdown` in response includes all intermediate values

**Files:** `FinalGradeCalculationService.java` (NEW), `GradingController.java` (MOD or NEW),
response DTOs: `FinalGradeResponse.java`, `DeliverableBreakdown.java` (NEW)

---

## P7-06 — [Frontend] Committee Review & Grading Panel

| Field | Value |
|-------|-------|
| **Assignee** | Yağmur |
| **Priority** | 🟡 Medium |
| **Story Points** | 2 |
| **Labels** | `feature`, `frontend`, `professor`, `p7` |

**Depends on:** #249 (backend 7.1) — can build with mock API during #249 development.

**Deliverables:**
1. Page `/committee/submissions/[submissionId]/grade`:
   - Split layout: left pane = Markdown submission (read-only render), right pane = rubric form.
   - Per criterion: label + dropdown.
     - **Binary** criteria: dropdown with `S` (Satisfactory) and `F` (Fail) only.
     - **Soft** criteria: dropdown with `A`, `B`, `C`, `D`, `F`.
   - "Submit Grades" button → `POST /api/submissions/{submissionId}/grade`.
   - Show `baseDeliverableGrade` on success.
2. Auth guard: redirect non-committee-member professors away.

**Acceptance Criteria:**
- [ ] Binary dropdown shows exactly `S` / `F`; Soft shows `A` / `B` / `C` / `D` / `F`
- [ ] Form blocked until all criteria have a selection
- [ ] `baseDeliverableGrade` displayed on success
- [ ] Non-committee professors are redirected

**Files:** `pages/committee/submissions/[submissionId]/grade.vue` (NEW),
`useApiClient.ts` (MOD — add `submitRubricGrade(submissionId, grades, token)`)

---

## P7-07 — [Frontend] Final Grade Dashboard

| Field | Value |
|-------|-------|
| **Assignee** | Arda |
| **Priority** | 🟡 Medium |
| **Story Points** | 2 |
| **Labels** | `feature`, `frontend`, `coordinator`, `professor`, `student`, `p7` |

**Depends on:** #252 (backend 7.2)

**Deliverables:**
1. **Advisor/Coordinator view:** Table of students with `weightedTotal`, `completionRatio`,
   `finalGrade`. "Calculate" button per row → `GET /api/students/{studentId}/grade/calculate`
   where `{studentId}` is the **11-digit student number** (not UUID).
2. **Student view:** Dashboard widget showing own grade breakdown — `deliverableBreakdown[]`
   array, scalars, completion ratio, final grade. Read-only.
3. `useApiClient.ts` — add `calculateStudentGrade(studentId11Digit, token)`.

**Acceptance Criteria:**
- [ ] `studentId` passed to API is the 11-digit number (not a UUID)
- [ ] `deliverableBreakdown` array rendered with intermediate values visible
- [ ] Student view is gated — students can only see their own data
- [ ] Response parsing handles `null` grade fields (before any calculation has run)

**Files:** `pages/coordinator/grades.vue` or similar (NEW),
`pages/student/group/grade.vue` (NEW or MOD), `useApiClient.ts` (MOD)

---

## P7-08 — [QA] Grading & Math Engine Tests

### P7-08a — [QA] Unit tests + math engine assertions

| Field | Value |
|-------|-------|
| **Assignee** | Batuhan | --> demir yaptım
| **Priority** | 🟠 High |
| **Story Points** | 2 |
| **Labels** | `qa`, `testing`, `backend`, `unit-test`, `p7` |

**Depends on:** #248 (GradeValueMapper), P7-05 (Calculation Engine)

**Deliverables:**
1. `GradeValueMapperTest` — all valid/invalid grade combinations.
2. `FinalGradeCalculationServiceTest` — unit tests with known inputs and expected outputs:
   ```
   Input:  2 sprints, pointA=[A(100), B(80)], pointB=[B(80), C(60)]
           ScrumScalar = AVG(100,80)/100 = 0.90
           ReviewScalar = AVG(80,60)/100 = 0.70
           DS = (0.90+0.70)/2 = 0.80
           B = 85.0 (mocked), ScaledGrade = 68.0, weight=30
           WeightedContribution = 68.0 × 0.30 = 20.4
           completedSP=15, targetSP=20 → C_i=0.75
           G_i = 20.4 × 0.75 = 15.3  ← assert exactly
   ```
3. Edge-case tests: sprint with no ScrumGrade (skipped), `targetSP=0` (C_i=0), B=0.

**Acceptance Criteria:**
- [ ] Exact numeric assertions using BigDecimal comparisons
- [ ] All edge cases pass
- [ ] `GradeValueMapper` tests cover every valid + invalid combination

---

### P7-08b — [QA] Integration tests (auth + persistence + CI + system-config)

| Field | Value |
|-------|-------|
| **Assignee** | Bilge |
| **Priority** | 🟠 High |
| **Story Points** | 3 |
| **Labels** | `qa`, `testing`, `backend`, `integration-test`, `p7` |

**Depends on:** #249, #250 (backend services)

**Deliverables:**
1. **Auth integration tests:**
   - Student A JWT → `/calculate` for Student B → 403
   - Student A JWT → `/calculate` for Student A → 200
   - Professor NOT advisor → 403
   - Professor IS advisor → 200
   - Coordinator → 200 for any student
   - Professor not in committee → `POST /grade` → 403
   - Professor in Proposal committee → `POST /grade` for SoW → 403
2. **Grade validation tests:**
   - Binary criterion + `"A"` → 400
   - Binary criterion + `"S"` → 200/201
   - Soft criterion + `"X"` → 400
3. **C_i identification tests:**
   - Matching `assigneeGithubUsername` + `prMerged=true` → counted
   - Matching username + `prMerged=false` → NOT counted
   - Non-matching username + `prMerged=true` → NOT counted
4. **Persistence test:** GET `/calculate` → assert row upserted via JDBC; second call →
   assert `calculated_at` updated, no duplicate row.

**Additional deliverable (S4-09 backend coverage):**

5. **System config endpoint tests** (`SystemConfigControllerTest`):
   - `PATCH /api/coordinator/system-config` with valid payload → 200, values persisted (H2 assertion)
   - Non-coordinator caller → 403
   - `maxTeamSize: 0` → 400

**Acceptance Criteria:**
- [ ] All 403 auth edge cases covered
- [ ] C_i tests cover all three log filter combinations
- [ ] JDBC assertion confirms `final_grade` table write on GET
- [ ] System config PATCH persists both fields; non-coordinator rejected
- [ ] All tests use isolated H2 schema

---

## S4-08 — [Backend] Trace logging for user events

| Field | Value |
|-------|-------|
| **Assignee** | Batuhan |
| **Priority** | 🟡 Medium |
| **Story Points** | 2 |
| **Labels** | `feature`, `backend`, `observability` |

**Problem:**
The project spec requires trace-level logging of user-triggered events (Difficulty Level 1).
No structured user event logging exists in the codebase. Currently there is no way to audit
who did what without querying the DB directly.

**Scope:** Backend — service layer `log.trace()` calls across all controllers/services.

**Deliverables:**

Add `@Slf4j` (Lombok) and `log.trace(...)` statements at the following service methods.
Log format: `"[EVENT] userId={} action={} entityId={} detail={}"`.

Key events to cover:

| Action | Class | Log |
|--------|-------|-----|
| Login (staff) | `AuthService` | `userId, "STAFF_LOGIN"` |
| Login (student OAuth) | `AuthService` | `userId, "STUDENT_LOGIN"` |
| Group created | `GroupService` | `userId, "GROUP_CREATED", groupId` |
| Invitation sent | `GroupService` | `userId, "INVITATION_SENT", targetStudentId` |
| Invitation accepted/declined | `GroupService` | `userId, "INVITATION_RESPONDED", invitationId, status` |
| Advisor request sent | `AdvisorService` | `userId, "ADVISOR_REQUEST_SENT", advisorId` |
| Advisor request accepted/rejected | `AdvisorService` | `userId, "ADVISOR_REQUEST_RESPONDED", groupId, status` |
| Grade submitted (scrum) | `ScrumGradingService` | `userId, "SCRUM_GRADE_SUBMITTED", groupId+sprintId` |
| Rubric grade submitted | `RubricGradingService` | `userId, "RUBRIC_GRADE_SUBMITTED", submissionId` |
| Final grade calculated | `FinalGradeCalculationService` | `userId, "FINAL_GRADE_CALCULATED", studentId` |

**Note on SQL triggers:** If the team prefers DB-level audit logging, MySQL `AFTER INSERT`/`AFTER UPDATE` triggers on key tables (`project_group`, `group_invitation`, `advisor_request`, `scrum_grade`) writing to a `user_event_log` table are also valid. However, application-level trace logging is simpler and sufficient for Difficulty Level 1.

**To enable trace output:** set `logging.level.com.senior.spm=TRACE` in `application.properties` (dev only — do not set in prod).

**Acceptance Criteria:**
- [ ] All events listed above emit a `log.trace(...)` line when triggered
- [ ] Log includes at minimum: calling user's ID, action name, primary entity ID
- [ ] `logging.level.com.senior.spm=TRACE` in dev properties activates the output
- [ ] No trace logging in production properties (keep at `INFO`)

**Related Issues:** None — cross-cutting, no dependencies.

**Estimate:** 1 point

---

## S4-09a — [Backend] System config update endpoint

| Field | Value |
|-------|-------|
| **Assignee** | Egemen |
| **Priority** | 🟡 Medium |
| **Story Points** | 1 |
| **Labels** | `feature`, `backend`, `coordinator`, `setup` |

**Problem:** `system_config` table (`active_term_id`, `max_team_size`) has no write API — coordinator cannot change the active term programmatically.

**Deliverables:**
1. `SystemConfigService.updateConfig(String activeTermId, Integer maxTeamSize)` — update only provided fields. `TermConfigService` stays read-only.
2. `PATCH /api/coordinator/system-config` in `CoordinatorController`:
   - Request body: `{ "activeTermId": "string", "maxTeamSize": int }` — both optional.
   - 400 if `maxTeamSize < 1`.
   - 403 if caller is not Coordinator.
   - 200 on success.

**Acceptance Criteria:**
- [ ] `PATCH` with `activeTermId` only → updates only that field
- [ ] `maxTeamSize: 0` → 400
- [ ] Non-coordinator → 403

**Files:** `SystemConfigService.java` (NEW), `CoordinatorController.java` (MOD)

---

## S4-09b — [Frontend] Coordinator settings page (system config + student upload)

| Field | Value |
|-------|-------|
| **Assignee** | Efecan |
| **Priority** | 🟡 Medium |
| **Story Points** | 1 |
| **Labels** | `feature`, `frontend`, `coordinator`, `setup` |

**Depends on:** #256

**Problem:** `POST /api/coordinator/students/upload` (CSV) exists in backend but there is no frontend page. System config PATCH (#256) also needs a UI.

**Deliverables:**
1. `pages/coordinator/settings.vue`:
   - "Active Term ID" text input + "Max Team Size" number input → "Save" → `PATCH /api/coordinator/system-config`.
   - "Upload Students" CSV file picker → `POST /api/coordinator/students/upload` (multipart).
   - Success/error toast per operation.
2. `useApiClient.ts` — add `updateSystemConfig(config, token)` and `uploadStudents(file, token)`.

**Acceptance Criteria:**
- [ ] System config save updates both fields via PATCH
- [ ] CSV upload triggers `POST /api/coordinator/students/upload`
- [ ] Error responses shown as toasts
- [ ] Page guarded for Coordinator role

**Files:** `pages/coordinator/settings.vue` (NEW), `useApiClient.ts` (MOD)

---

## S4-10 — [QA] Manual frontend smoke testing for Sprint 4 features

| Field | Value |
|-------|-------|
| **Assignee** | Bilge |
| **Priority** | 🟠 High |
| **Story Points** | 1 |
| **Labels** | `qa`, `testing`, `frontend`, `manual` |

**Depends on:** all Sprint 4 frontend work

**Problem:** Integration tests verify API contracts but don't catch UI rendering bugs,
missing error toasts, broken guards, or layout issues. Manual smoke testing on the running
app is needed before the May 9 demo.

**Deliverables:**

Test each new frontend feature with a real dev server + populated DB. Record pass/fail
per checklist item. Open a bug issue for any failure.

| Feature | What to verify |
|---------|---------------|
| S4-03 — Advisor capacity inputs | Registration with custom capacity; coordinator inline edit saves and reflects immediately |
| S4-04 — Sprint monitor | Sprint selector loads; Trigger Refresh shows stats; AI counts colored; grade badge correct |
| S4-06 — Schedule window | OPEN / CLOSED / NOT SET badges correct; modal date picker works; Close confirm dialog fires; auto-refresh |
| P7-06 — Committee grading panel | Binary dropdown shows only S/F; Soft shows A–F; Submit disabled until all criteria filled; `baseDeliverableGrade` shown on success |
| P7-07 — Final grade dashboard | Coordinator table loads; Calculate button calls API; student view shows own breakdown; 403 redirected |
| S4-09b — Settings page | System config save calls PATCH; CSV upload sends multipart; toasts appear on error |

**Acceptance Criteria:**
- [ ] All 6 features tested on dev server with Coordinator + Professor + Student JWT
- [ ] Each failing item has a linked bug issue
- [ ] No demo-blocking UI regressions found (or bugs filed and assigned)

---

## Internal Dependencies Summary

| Issue | Depends on | Notes |
|-------|-----------|-------|
| P7-02 | `blue_main_sprint4` merge | Merge our branch from Blue's branch before starting; `DeliverableSubmission` entity already exists there |
| P7-04 | P7-02 | Can start as soon as P7-02 entity is merged |
| P7-06 | P7-04 | Build with mock API while P7-04 is in progress |
| S4-03 | S4-02 | Frontend capacity UI requires backend endpoint |
| S4-06 | S4-05 | Frontend schedule page requires backend CRUD |
| S4-07 | S4-01, S4-02, S4-05 | QA integration tests |
| S4-09b | S4-09a | Frontend settings page requires PATCH endpoint |
| P7-08a | P7-03, P7-05 | Unit tests |
| P7-08b | P7-04, P7-05, S4-09a | Integration tests (includes S4-09a coverage) |


---

## Final Point Summary

| Assignee | Issues | Points |
|----------|--------|--------|
| Batıkan | S4-01, P7-01, P7-02, P7-03 | 4 |
| Egemen | S4-02, S4-05, S4-09a | 5 |
| Zeynep | S4-03, S4-06 | 5 |
| Arda | S4-04, P7-07 | 4 |
| Efecan | P7-05, S4-09b | 4 |
| Yağmur | P7-04, P7-06 | 4 |
| Batuhan | P7-08a, S4-08 | 4 |
| Bilge | P7-08b, S4-10 | 4 |
| Demir | S4-07 | 3 |
| **Total** | | **36** |

