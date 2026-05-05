# SD-P7-3 — Frontend Flows (Sub-Processes 7.1 & 7.2)

Covers the two frontend features for P7:
- **P7-06** — Committee Review & Grading Panel (`pages/committee/submissions/[submissionId]/grade.vue`)
- **P7-07** — Final Grade Dashboard (`pages/coordinator/grades.vue`, `pages/student/group/grade.vue`)

---

## P7-06 — Committee Grading Panel

```mermaid
sequenceDiagram
    autonumber
    actor Prof as Professor (Committee Member)
    participant Page as grade.vue<br/>[submissionId]
    participant Mid as auth.ts middleware
    participant API as useApiClient.ts
    participant BE as Backend API

    Prof->>Page: Navigate to /committee/submissions/:submissionId/grade

    Note over Page,Mid: Auth guard
    Page->>Mid: Check JWT role
    alt not ROLE_PROFESSOR
        Mid-->>Prof: Redirect to login
    else ROLE_PROFESSOR
        Mid-->>Page: Allow
    end

    Note over Page,BE: Load submission + rubric criteria
    Page->>API: fetchSubmission(submissionId, token)
    API->>BE: GET /api/submissions/{submissionId}
    BE-->>API: { markdownContent, deliverableId, groupId }
    API-->>Page: submission data

    Page->>API: fetchRubricCriteria(deliverableId, token)
    API->>BE: GET /api/coordinator/deliverables/{deliverableId}/rubric
    BE-->>API: [{ criterionId, criterionName, gradingType, weight }]
    API-->>Page: criteria[]

    Note over Page: Render split layout:<br/>Left: markdown (read-only)<br/>Right: rubric form with dropdowns

    loop for each criterion
        alt gradingType == "Binary"
            Page->>Prof: Dropdown: [S, F] only
        else gradingType == "Soft"
            Page->>Prof: Dropdown: [A, B, C, D, F]
        end
    end

    Note over Page: "Submit Grades" button disabled until all criteria selected

    Prof->>Page: Selects grade for each criterion
    Prof->>Page: Clicks "Submit Grades"

    Note over Page: Frontend validates all criteria have a selection
    Page->>API: submitRubricGrade(submissionId, grades[], token)
    API->>BE: POST /api/submissions/{submissionId}/grade<br/>{ grades: [{ criterionId, selectedGrade }] }

    alt 201 Created (first grade)
        BE-->>API: { submissionId, reviewerId, baseDeliverableGrade: 85.5 }
        API-->>Page: success response
        Page-->>Prof: Show "Base Deliverable Grade: 85.5" banner
    else 200 OK (updated grade)
        BE-->>API: { submissionId, reviewerId, baseDeliverableGrade: 87.0 }
        API-->>Page: success response
        Page-->>Prof: Show updated grade banner
    else 403 Forbidden
        BE-->>API: { "message": "You are not a committee member for this submission" }
        API-->>Page: error
        Page-->>Prof: Redirect away (not a committee member)
    else 400 Bad Request
        BE-->>API: { "message": "Invalid grade 'X' for Binary criterion <id>" }
        API-->>Page: error
        Page-->>Prof: Show validation error toast
    end
```

---

## P7-07 — Final Grade Dashboard (Coordinator / Advisor View)

```mermaid
sequenceDiagram
    autonumber
    actor Coord as Coordinator or Advisor
    participant Page as grades.vue
    participant Mid as auth.ts middleware
    participant API as useApiClient.ts
    participant BE as Backend API

    Coord->>Page: Navigate to /coordinator/grades (or /professor/grades)

    Page->>Mid: Check JWT role
    alt not Coordinator or Professor
        Mid-->>Coord: Redirect to login
    end

    Note over Page,BE: Load student list for the term
    Page->>API: fetchGroupStudents(groupId, token)
    API->>BE: GET /api/coordinator/groups or /api/advisor/groups
    BE-->>API: [{ studentId (11-digit), name, ... }]
    API-->>Page: students[]

    Page-->>Coord: Render table (studentId | name | weightedTotal | completionRatio | finalGrade | action)

    Note over Page: Initial load — grades may be null (not yet calculated)
    Note over Page: "—" shown for null grade fields

    Coord->>Page: Clicks "Calculate" button for a student row
    Page->>Page: Disable button, show spinner

    Page->>API: calculateStudentGrade(studentId11Digit, token)
    API->>BE: GET /api/students/{studentId11Digit}/grade/calculate

    alt 200 OK
        BE-->>API: FinalGradeResponse { studentId, groupId,<br/>deliverableBreakdown[], weightedTotal,<br/>completionRatio, finalGrade, calculatedAt }
        API-->>Page: grade data
        Page-->>Coord: Update row: weightedTotal | completionRatio | finalGrade
        Page-->>Coord: Show expandable breakdown panel per deliverable
    else 403 Forbidden (professor not advisor)
        BE-->>API: { "message": "Caller is unauthorized to view this grade" }
        API-->>Page: error
        Page-->>Coord: Show error toast
    else 404 Not Found
        BE-->>API: { "message": "Student not found" }
        API-->>Page: error
        Page-->>Coord: Show error toast
    end
```

---

## P7-07 — Final Grade Dashboard (Student Self-View)

```mermaid
sequenceDiagram
    autonumber
    actor Stud as Student
    participant Page as student/group/grade.vue
    participant Mid as auth.ts middleware
    participant API as useApiClient.ts
    participant BE as Backend API

    Stud->>Page: Navigate to /student/group/grade

    Page->>Mid: Check JWT role
    alt not ROLE_STUDENT
        Mid-->>Stud: Redirect to login
    end

    Note over Page,BE: Student can only fetch their own grade
    Page->>Page: Extract own studentId (11-digit) from Pinia store or JWT

    Page->>API: calculateStudentGrade(ownStudentId, token)
    API->>BE: GET /api/students/{ownStudentId}/grade/calculate<br/>Bearer: studentJWT

    alt 200 OK
        BE-->>API: FinalGradeResponse { deliverableBreakdown[], weightedTotal,<br/>completionRatio, finalGrade, calculatedAt }
        API-->>Page: grade data
        Page-->>Stud: Show read-only breakdown:<br/>- Per deliverable: baseGrade, scrumScalar, reviewScalar, scaledGrade, weight, contribution<br/>- Overall: weightedTotal, completionRatio, finalGrade
    else 403 Forbidden (called for another student)
        BE-->>API: 403 error
        API-->>Page: error
        Page-->>Stud: Redirect (middleware blocks this path)
    else Grade not yet calculated (null fields in response)
        BE-->>API: { weightedTotal: null, finalGrade: null, ... }
        API-->>Page: null fields
        Page-->>Stud: Show "Grade not yet calculated" placeholder
    end
```

---

## useApiClient.ts — New Methods for P7

| Method | HTTP | Path | Called by |
|--------|------|------|-----------|
| `submitRubricGrade(submissionId, grades, token)` | POST | `/api/submissions/{submissionId}/grade` | P7-06 grade.vue |
| `calculateStudentGrade(studentId11Digit, token)` | GET | `/api/students/{studentId}/grade/calculate` | P7-07 grades.vue, grade.vue |
| `fetchRubricCriteria(deliverableId, token)` | GET | `/api/coordinator/deliverables/{id}/rubric` | P7-06 grade.vue |

---

## Key UI Rules

| Rule | Where enforced |
|------|----------------|
| Binary criterion dropdown shows **only** `S` / `F` | grade.vue — dropdown options |
| Soft criterion dropdown shows `A`, `B`, `C`, `D`, `F` | grade.vue — dropdown options |
| "Submit Grades" blocked until all criteria have a selection | grade.vue — `:disabled` binding |
| `baseDeliverableGrade` shown on success | grade.vue — success banner |
| Non-committee professors redirected away | auth guard or on-403 redirect |
| Student view is **read-only**; student can only see own data | student/group/grade.vue |
| `studentId` passed to API is the **11-digit number**, not UUID | useApiClient.ts |
| Null grade fields show "—" (before calculation runs) | grades.vue — conditional render |
