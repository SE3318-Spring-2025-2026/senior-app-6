# Process 7 — API Endpoints (ver2 — Corrected)
## Grading, Scalar & Final Grade Calculation (Sub-Processes 7.1–7.2)

> **Sources (authoritative):**
> - `docs/phase1_2.md` — Process 7 Steps 1–11 (primary spec)
> - `docs/openapi.yaml` — P7 schema (lines 2780–3170)
> - `red_notes/dfd/dfd_lvl0.drawio`

---

## Conventions

| Item | Rule |
|------|------|
| Primary keys | UUID (`BINARY(16)` in MySQL) |
| Timestamps | ISO-8601 (`LocalDateTime`) |
| Student JWT | `sub="Student"`, claim `id` = **UUID of Student entity**, claim `githubUsername`. **No `studentId` claim.** |
| Staff JWT | `sub="StaffUser"`, claim `id` = UUID of StaffUser, claim `role` (Admin/Coordinator/Professor) |
| Principal UUID | Extracted via `SecurityUtils.extractPrincipalUUID(auth)` |
| Error body | `{ "message": "Human-readable message" }` |
| ScrumGradeValue → numeric | `A`=100, `B`=80, `C`=60, `D`=50, `F`=0 (per FR-2 in `phase1_2.md`) |
| Binary grade valid values | `S` (100), `F` (0) |
| Soft grade valid values | `A` (100), `B` (80), `C` (60), `D` (50), `F` (0) |

> **Correction vs ver1:** JWT does NOT carry `studentId` (11-digit number) as a claim.
> The `id` claim is the UUID primary key of the `Student` JPA entity. The 11-digit
> `studentId` is a separate DB column looked up via `StudentRepository.findByStudentId()`.

---

## New Entities Required

### `FinalGrade` — NO external dependencies; build immediately

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID PK | `@GeneratedValue(strategy = GenerationType.UUID)` |
| `student` | `@ManyToOne Student` | NOT NULL |
| `group` | `@ManyToOne ProjectGroup` | NOT NULL (term/group scope) |
| `weightedTotal` | BigDecimal | Sum(ScaledGrade × DeliverableWeight) |
| `completionRatio` | BigDecimal | C_i = completedStoryPoints / targetStoryPoints |
| `finalGrade` | BigDecimal | G_i = WeightedTotal × C_i |
| `calculatedAt` | LocalDateTime | NOT NULL |

Unique constraint: `uq_fg_student (student_id)` — one row per student.

> **Correction vs ver1:** `deliverableId` field removed. It was nullable "if representing total"
> — this was misleading. Per-deliverable breakdowns are computed on-the-fly and returned in the
> response but NOT persisted. Only `weightedTotal`, `completionRatio`, `finalGrade` are stored.

---

### `RubricGrade` — BLOCKED on P6 `DeliverableSubmission` entity

> **BLOCKER:** This entity cannot be implemented until Blue Team's P6 `DeliverableSubmission`
> JPA entity exists in the codebase. The `submission` FK references that entity.

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID PK | `@GeneratedValue(strategy = GenerationType.UUID)` |
| `submission` | `@ManyToOne DeliverableSubmission` | NOT NULL — depends on P6 |
| `criterion` | `@ManyToOne RubricCriterion` | NOT NULL |
| `reviewer` | `@ManyToOne StaffUser` | NOT NULL (Committee Member) |
| `selectedGrade` | String | Binary: must be `"S"` or `"F"`. Soft: must be `"A"`,`"B"`,`"C"`,`"D"`,`"F"`. |
| `gradedAt` | LocalDateTime | NOT NULL |

Unique constraint: `uq_rg_submission_criterion_reviewer (submission_id, criterion_id, reviewer_id)`.

---

## 7.1 — Rubric Grading

### `POST /api/submissions/{submissionId}/grade`

**Auth:** Staff JWT where `role = Professor` AND caller is a committee member for the
submission's deliverable+group combination.

**Corrected Committee Membership Check:**
Committee is deliverable-scoped (`Committee.deliverable` FK). The check must be:

1. Resolve `submissionId` → `DeliverableSubmission` → get `deliverableId` and `groupId`.
2. Query: find `Committee WHERE committee.deliverable.id = deliverableId AND groupId IN committee.groups AND callerStaffUserId IN committee.professors`.
3. Requires new repository method: `CommitteeRepository.findByDeliverableIdAndGroupIdAndProfessorId(deliverableId, groupId, professorId)`.
4. If no committee found → 403.

> **Correction vs ver1:** Original said "verify caller has role in the group's committee."
> That is ambiguous — Committee is scoped to a specific Deliverable, not a group in general.
> A Professor on the Proposal committee is NOT authorized to grade the SoW submission.

**Grade Validation (per criterion):**
- Fetch `RubricCriterion.gradingType` for each `criterionId` in the payload.
- If `gradingType = Binary`: `selectedGrade` must be `"S"` or `"F"`. Else → 400.
- If `gradingType = Soft`: `selectedGrade` must be `"A"`, `"B"`, `"C"`, `"D"`, or `"F"`. Else → 400.

**Base Deliverable Grade (B) computation:**
```
For each reviewer who has graded this submission:
  reviewerScore = SUM(numericGrade(selectedGrade) × criterion.weight) / SUM(criterion.weight)

B = AVG(reviewerScore across all reviewers)
```
Note: `criterion.weight` values for a deliverable should sum to 100 (coordinator-configured).

**Request:**
```json
{
  "grades": [
    {
      "criterionId": "uuid",
      "selectedGrade": "A"
    }
  ]
}
```

**Response 201 (first grade) / 200 (update):**
```json
{
  "submissionId": "uuid",
  "reviewerId": "uuid",
  "baseDeliverableGrade": 85.5
}
```

**Errors:**

| HTTP | Body | Condition |
|------|------|-----------|
| 400 | `{ "message": "Invalid grade 'A' for Binary criterion <criterionId>" }` | Wrong grade value for criterion type |
| 400 | `{ "message": "Missing grade for criterion <criterionId>" }` | Criterion in rubric not present in payload |
| 403 | `{ "message": "You are not a committee member for this submission" }` | Not in committee for deliverable+group |
| 404 | `{ "message": "Submission not found" }` | `submissionId` doesn't exist |

---

## 7.2 — Scalar & Final Grade Calculation

### `GET /api/students/{studentId}/grade/calculate`

**Path parameter:** `{studentId}` is the **11-digit student number** (pattern `^[0-9]{11}$`),
resolved via `StudentRepository.findByStudentId(String studentId)`.

> **Correction vs ver1:** The description said "Token `studentId` claim must exactly match."
> The JWT carries no `studentId` claim. For student callers: extract the UUID from the `id`
> claim → resolve to `Student` entity → compare `student.studentId` to the path param.

**Auth:**

| Caller role | Allowed condition |
|-------------|------------------|
| `ROLE_COORDINATOR` | Always allowed |
| `ROLE_PROFESSOR` | Allowed if caller is the advisor of the student's group (`group.getAdvisorId().equals(callerUUID)`) |
| `ROLE_STUDENT` | Allowed only if `student.studentId` (resolved from JWT `id` UUID) matches path `{studentId}` |

**Architecture Note (GET with side effects):** This endpoint computes on-the-fly and
**upserts** to the `FinalGrade` table before returning. This keeps the OpenAPI contract
(`GET`) while satisfying `phase1_2.md` Step 11 ("kaydedilir").

**Calculation Steps (entity field references):**

1. **Resolve student:** `StudentRepository.findByStudentId(studentId)` → `Student` entity → `student.githubUsername`.
2. **Resolve group:** `GroupMembershipRepository.findByStudentId(student.id)` → `GroupMembership.group` → `ProjectGroup`.
3. **For each `Deliverable` in the term:**

   a. **Contributing sprints:** `SprintDeliverableMappingRepository.findAllByDeliverableId(deliverable.id)` → list of `SprintDeliverableMapping` (each has `sprint` and `contributionPercentage`).

   b. **ScrumScalar (Step 4):** For each contributing sprint, `ScrumGradeRepository.findByGroupIdAndSprintId(group.id, sprint.id)` → `ScrumGrade.pointAGrade` → convert to numeric (A=100, B=80, C=60, D=50, F=0).
   ```
   ScrumScalar = AVG(numericPointA values) / 100
   ```
   > **Design decision on `contributionPercentage`:** Use plain average (Option A, spec-literal).
   > `contributionPercentage` identifies which sprints contribute, but does not weight the scalar.
   > Add `// TODO: consider weighted average if product owner requires it` in code.

   c. **ReviewScalar (Step 5):** Same as above using `pointBGrade`.
   ```
   ReviewScalar = AVG(numericPointB values) / 100
   ```

   d. **DS — Deliverable Scalar (Step 6):**
   ```
   DS = (ScrumScalar + ReviewScalar) / 2.0
   ```

   e. **B — Base Deliverable Grade:** Query `RubricGrade` for this deliverable's submission+group.
   Compute as in 7.1 (average of all reviewer scores).

   f. **ScaledGrade (Step 7):**
   ```
   ScaledGrade = B × DS
   ```

   g. **Accumulate (Step 8):**
   ```
   WeightedTotal += ScaledGrade × (deliverable.weight / 100)
   ```

4. **C_i — Individual Completion Ratio (Step 9):**
   - **Completed:** Query all `SprintTrackingLog` rows for the student's `group` across all sprints.
     Filter: `assigneeGithubUsername == student.githubUsername AND prMerged == true`.
     Sum `storyPoints` (treat `null` as 0) → `completedStoryPoints`.
   - **Target:** Sum `Sprint.storyPointTarget` across all sprints in the term → `targetStoryPoints`.
   - `C_i = completedStoryPoints / targetStoryPoints`
   - If `targetStoryPoints == 0` → `C_i = 0` (avoid division by zero).
   - Do **NOT** cap at 1.0: over-delivery should benefit the student.

   > **Correction vs ver1:** C_i identification was undocumented. Must use
   > `assigneeGithubUsername` (from `SprintTrackingLog`) matched against `student.githubUsername`
   > (from `Student` entity). "Completed" = `prMerged == true`. This matches the existing pattern
   > in `ScrumGradingService.buildPerStudentSummary()` (lines 302–305).

5. **G_i — Individual Final Grade (Step 10):**
   ```
   G_i = WeightedTotal × C_i
   ```

6. **Persist (Step 11):** Upsert to `FinalGrade` table (`student`, `group`, `weightedTotal`, `completionRatio`, `finalGrade`, `calculatedAt`).

**Response 200:**
```json
{
  "studentId": "01234567890",
  "groupId": "uuid",
  "deliverableBreakdown": [
    {
      "deliverableId": "uuid",
      "deliverableName": "Proposal",
      "baseGrade": 88.0,
      "scrumScalar": 0.85,
      "reviewScalar": 0.80,
      "deliverableScalar": 0.825,
      "scaledGrade": 72.6,
      "weight": 30,
      "weightedContribution": 21.78
    }
  ],
  "weightedTotal": 76.5,
  "completionRatio": 0.95,
  "finalGrade": 72.675,
  "calculatedAt": "2026-05-01T14:30:00"
}
```

**Errors:**

| HTTP | Body | Condition |
|------|------|-----------|
| 403 | `{ "message": "Caller is unauthorized to view this grade" }` | Student calls for another student; professor not advisor |
| 404 | `{ "message": "Student not found" }` | No student with this 11-digit ID |

---

## Endpoint Summary Table

| # | Method | Path | Auth | Sub-process |
|---|--------|------|------|-------------|
| 1 | POST | `/api/submissions/{submissionId}/grade` | Staff (Committee Member for that deliverable+group) | 7.1 |
| 2 | GET | `/api/students/{studentId}/grade/calculate` | Coordinator / Advisor / Student (self only) | 7.2 |

---

## Design Decision Notes

### D1 — `contributionPercentage` in ScrumScalar/ReviewScalar
`SprintDeliverableMapping.contributionPercentage` identifies which sprints contribute to a
deliverable but is **not used to weight** the scalar in the initial implementation (Option A,
plain average). This matches the literal spec wording "average." A `// TODO` comment must be
added for future weighted implementation (Option B).

### D2 — C_i cap
C_i is intentionally **not capped at 1.0**. A student who completes more story points than the
target should receive a proportionally higher grade. If the product owner changes this, update
`FinalGradeCalculationService` and add the cap in one place.

### D3 — GET with side effects
The endpoint is `GET` to match the existing OpenAPI contract. The upsert to `FinalGrade` is a
documented side effect, not a violation of REST semantics in this context. A comment in the
controller must explain this.

### D4 — Edge cases requiring explicit handling
| Edge case | Behavior |
|-----------|----------|
| Sprint with no `ScrumGrade` for the group | Skip that sprint from scalar calculation |
| Deliverable with no submission | `B = 0` |
| No `SprintTrackingLog` rows for student | `completedStoryPoints = 0`, `C_i = 0` |
| `targetStoryPoints == 0` | `C_i = 0` |
