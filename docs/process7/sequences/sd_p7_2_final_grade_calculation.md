# SD-P7-2 — Final Grade Calculation Engine (Sub-Process 7.2)

**Endpoint:** `GET /api/students/{studentId}/grade/calculate`  
**Path param:** `{studentId}` = **11-digit student number** (not a UUID)  
**Issues:** P7-01 (FinalGrade entity), P7-03 (GradeValueMapper), P7-05 (FinalGradeCalculationService)

---

## Full Calculation Flow

```mermaid
sequenceDiagram
    autonumber
    actor Caller as Caller (Coordinator / Advisor / Student)
    participant GC as GradingController
    participant FGS as FinalGradeCalculationService
    participant StudRepo as StudentRepository
    participant GMRepo as GroupMembershipRepository
    participant DelRepo as DeliverableRepository
    participant SDMRepo as SprintDeliverableMappingRepository
    participant SGRepo as ScrumGradeRepository
    participant DSRepo as DeliverableSubmissionRepository
    participant RGRepo as RubricGradeRepository
    participant GVM as GradeValueMapper
    participant STLRepo as SprintTrackingLogRepository
    participant SprintRepo as SprintRepository
    participant FGRepo as FinalGradeRepository
    participant DB as MySQL

    Caller->>GC: GET /api/students/{studentId11Digit}/grade/calculate<br/>Bearer: JWT

    Note over GC: Auth check (before delegating to service)
    GC->>GC: Extract caller role + UUID from JWT

    alt caller is ROLE_STUDENT
        GC->>StudRepo: findByStudentId(studentId11Digit)
        StudRepo-->>GC: Student entity (student.id = UUID)
        GC->>GC: Assert callerUUID == student.id (UUID from JWT "id" claim)<br/>If mismatch → 403
    else caller is ROLE_PROFESSOR
        GC->>StudRepo: findByStudentId(studentId11Digit) → Student
        GC->>GMRepo: findByStudentId(student.id) → GroupMembership → group.advisorId
        GC->>GC: Assert group.advisorId == callerUUID<br/>If mismatch → 403
    end
    Note over GC: ROLE_COORDINATOR → always allowed

    GC->>FGS: calculateFinalGrade(studentId11Digit)

    Note over FGS: Step 1 — Resolve Student
    FGS->>StudRepo: findByStudentId(studentId11Digit)
    StudRepo->>DB: SELECT * FROM student WHERE student_id = ?
    DB-->>StudRepo: Student { id (UUID), studentId, githubUsername }
    StudRepo-->>FGS: Student

    Note over FGS: Step 2 — Resolve Group
    FGS->>GMRepo: findByStudentId(student.id)
    GMRepo->>DB: SELECT * FROM group_membership WHERE student_id = ?
    DB-->>GMRepo: GroupMembership → group
    GMRepo-->>FGS: ProjectGroup

    Note over FGS: Step 3 — Iterate over all Deliverables in the term
    FGS->>DelRepo: findByTermId(activeTermId)
    DelRepo->>DB: SELECT * FROM deliverable WHERE term_id = ?
    DB-->>DelRepo: List<Deliverable>

    loop for each Deliverable
        Note over FGS: Step 3a — Find contributing sprints
        FGS->>SDMRepo: findAllByDeliverableId(deliverable.id)
        SDMRepo->>DB: SELECT * FROM sprintDeliverableMapping WHERE deliverable_id = ?
        DB-->>SDMRepo: List<SprintDeliverableMapping> (each has sprint + contributionPercentage)
        SDMRepo-->>FGS: contributingSprints[]

        Note over FGS: Step 3b — ScrumScalar = AVG(pointAGrade.toNumeric()) / 100<br/>Skip sprints with no ScrumGrade for this group
        loop for each contributing sprint
            FGS->>SGRepo: findByGroupIdAndSprintId(group.id, sprint.id)
            SGRepo->>DB: SELECT * FROM scrum_grade WHERE group_id=? AND sprint_id=?
            DB-->>SGRepo: Optional<ScrumGrade>

            alt ScrumGrade present
                FGS->>FGS: collect pointAGrade.toNumeric() and pointBGrade.toNumeric()
            else no ScrumGrade
                FGS->>FGS: skip sprint (not counted in average)
            end
        end
        FGS->>FGS: ScrumScalar = AVG(pointA numerics) / 100
        FGS->>FGS: ReviewScalar = AVG(pointB numerics) / 100

        Note over FGS: Step 3d — DS = (ScrumScalar + ReviewScalar) / 2.0
        FGS->>FGS: DS = (ScrumScalar + ReviewScalar) / 2.0

        Note over FGS: Step 3e — B (Base Deliverable Grade) from RubricGrades
        FGS->>DSRepo: findLatestByGroupIdAndDeliverableId(group.id, deliverable.id)
        DSRepo->>DB: SELECT * FROM deliverable_submission WHERE group_id=? AND deliverable_id=?
        DB-->>DSRepo: Optional<DeliverableSubmission>

        alt submission found
            FGS->>RGRepo: findBySubmissionId(submission.id)
            RGRepo->>DB: SELECT * FROM rubric_grade WHERE submission_id=?
            DB-->>RGRepo: List<RubricGrade> (all reviewer grades)

            Note over FGS: B = AVG_reviewers( SUM_criteria(numericGrade × weight) / SUM(weight) )
            loop for each grade
                FGS->>GVM: toNumeric(gradingType, selectedGrade)
                GVM-->>FGS: numeric score
            end
            FGS->>FGS: Compute B
        else no submission
            FGS->>FGS: B = 0.0  (edge case)
        end

        Note over FGS: Step 3f — ScaledGrade = B × DS
        FGS->>FGS: ScaledGrade = B × DS

        Note over FGS: Step 3g — Accumulate WeightedTotal
        FGS->>FGS: WeightedTotal += ScaledGrade × (deliverable.weight / 100)
        FGS->>FGS: Record DeliverableBreakdown { deliverableId, name, baseGrade, scrumScalar,<br/>reviewScalar, deliverableScalar, scaledGrade, weight, weightedContribution }
    end

    Note over FGS: Step 4 — C_i Individual Completion Ratio
    FGS->>STLRepo: findByGroupIdAndAssigneeGithubUsernameAndPrMergedTrue(group.id, student.githubUsername)
    STLRepo->>DB: SELECT * FROM sprint_tracking_log<br/>WHERE group_id=? AND assignee_github_username=? AND pr_merged=true
    DB-->>STLRepo: List<SprintTrackingLog>
    FGS->>FGS: completedSP = SUM(log.storyPoints ?? 0)

    FGS->>SprintRepo: findAllByTermId(activeTermId)
    SprintRepo->>DB: SELECT * FROM sprint WHERE term_id=?
    DB-->>SprintRepo: List<Sprint>
    FGS->>FGS: targetSP = SUM(sprint.storyPointTarget)

    alt targetSP == 0
        FGS->>FGS: C_i = 0  (avoid divide-by-zero)
    else
        FGS->>FGS: C_i = completedSP / targetSP  (NOT capped at 1.0)
    end

    Note over FGS: Step 5 — G_i = WeightedTotal × C_i
    FGS->>FGS: G_i = WeightedTotal × C_i

    Note over FGS: Step 6 — Upsert to FinalGrade table
    FGS->>FGRepo: findByStudent_IdAndTermId(student.id, activeTermId)
    FGRepo->>DB: SELECT * FROM final_grade WHERE student_id=? AND term_id=?
    DB-->>FGRepo: Optional<FinalGrade>

    alt not found
        FGS->>FGRepo: save(new FinalGrade { student, group, termId, weightedTotal, completionRatio=C_i, finalGrade=G_i, calculatedAt=now })
        FGRepo->>DB: INSERT INTO final_grade ...
    else found
        FGS->>FGRepo: save(existing with updated fields)
        FGRepo->>DB: UPDATE final_grade SET weighted_total=?, completion_ratio=?,<br/>final_grade=?, calculated_at=? WHERE id=?
    end

    FGS-->>GC: FinalGradeResponse { studentId, groupId, deliverableBreakdown[], weightedTotal, completionRatio, finalGrade, calculatedAt }

    GC-->>Caller: 200 FinalGradeResponse
```

---

## Auth Check Detail

```mermaid
sequenceDiagram
    autonumber
    actor Caller
    participant GC as GradingController
    participant StudRepo as StudentRepository
    participant GMRepo as GroupMembershipRepository

    Note over Caller,GMRepo: ROLE_STUDENT — can only fetch own grade
    Caller->>GC: GET /api/students/01234567890/calculate<br/>JWT: { sub="Student", id=<studentUUID> }
    GC->>StudRepo: findByStudentId("01234567890") → Student { id=targetUUID }
    GC->>GC: callerUUID (from JWT "id" claim) == targetUUID ?
    alt mismatch → different student
        GC-->>Caller: 403 { "message": "Caller is unauthorized to view this grade" }
    else match → own grade
        GC->>GC: proceed to FinalGradeCalculationService
    end

    Note over Caller,GMRepo: ROLE_PROFESSOR — allowed only if advisor of that group
    Caller->>GC: GET /api/students/01234567890/calculate<br/>JWT: { sub="StaffUser", id=<professorUUID>, role="Professor" }
    GC->>StudRepo: findByStudentId("01234567890") → Student
    GC->>GMRepo: findByStudentId(student.id) → GroupMembership → group
    GC->>GC: group.advisorId == callerUUID ?
    alt not advisor
        GC-->>Caller: 403 { "message": "Caller is unauthorized to view this grade" }
    else is advisor
        GC->>GC: proceed to FinalGradeCalculationService
    end

    Note over Caller,GMRepo: ROLE_COORDINATOR — always allowed (no check needed)
    Caller->>GC: GET /api/students/01234567890/calculate<br/>JWT: { role="Coordinator" }
    GC->>GC: proceed immediately
```

---

## Math Summary

```
For each Deliverable:
  contributingSprints = SprintDeliverableMapping where deliverable_id = D

  ScrumScalar  = AVG(ScrumGrade.pointAGrade.toNumeric() for each sprint with a grade) / 100
  ReviewScalar = AVG(ScrumGrade.pointBGrade.toNumeric() for each sprint with a grade) / 100
  DS           = (ScrumScalar + ReviewScalar) / 2.0

  B            = AVG over reviewers of:
                   SUM(numericGrade(selectedGrade) × criterion.weight) / SUM(criterion.weight)

  ScaledGrade  = B × DS
  WeightedTotal += ScaledGrade × (deliverable.weight / 100)

C_i = SUM(storyPoints where assigneeGithubUsername=student.githubUsername AND prMerged=true)
      / SUM(sprint.storyPointTarget across all term sprints)
      [C_i = 0 if targetSP = 0; NOT capped at 1.0]

G_i = WeightedTotal × C_i
```

---

## Key Classes & Files

| Class | Role | Issue |
|-------|------|-------|
| `GradingController` | REST endpoint; auth dispatch before service call | P7-05 |
| `FinalGradeCalculationService` | Full formula implementation Steps 1–11 | P7-05 |
| `FinalGrade` | JPA entity; upserted at end of calculation | P7-01 |
| `FinalGradeRepository` | `findByStudent_IdAndTermId`, `findByStudent_StudentId` | P7-01 |
| `GradeValueMapper` | `toNumeric(gradingType, value)` used for B computation | P7-03 |
| `ScrumGrade.ScrumGradeValue` | `.toNumeric()` method for scalar computation | P7-03 |
| `DeliverableSubmissionRepository` | Lookup latest submission per group+deliverable | Blue P6 |
| `SprintTrackingLogRepository` | C_i filter: `assigneeGithubUsername` + `prMerged=true` | P5 |

> **D1 (Design decision):** `SprintDeliverableMapping.contributionPercentage` identifies
> which sprints contribute but is NOT used to weight the scalar — plain average is used.
> A `// TODO: consider weighted average` comment must appear in the service.

> **D3 (Design decision):** Endpoint is `GET` per the OpenAPI contract but upserts to
> `FinalGrade` as a documented side effect. A comment in the controller must explain this.
