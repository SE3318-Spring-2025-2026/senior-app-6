# Process 7 — Sequence Diagrams

> These diagrams are the primary developer reference for implementing P7.
> All diagrams use Mermaid and render on GitHub.

| File | What it covers | Issues |
|------|---------------|--------|
| [sd_p7_1_rubric_grading.md](sd_p7_1_rubric_grading.md) | `POST /api/submissions/{submissionId}/grade` — committee auth, grade validation, RubricGrade upsert, B computation | P7-02, P7-03, P7-04 |
| [sd_p7_2_final_grade_calculation.md](sd_p7_2_final_grade_calculation.md) | `GET /api/students/{studentId}/grade/calculate` — full Steps 1–11 (ScrumScalar, ReviewScalar, DS, B, ScaledGrade, WeightedTotal, C_i, G_i, upsert) + auth detail | P7-01, P7-03, P7-05 |
| [sd_p7_3_frontend_flows.md](sd_p7_3_frontend_flows.md) | Committee grading panel (P7-06) + Final grade dashboard coordinator/student views (P7-07) | P7-06, P7-07 |
| [sd_p7_4_gradevaluemapper.md](sd_p7_4_gradevaluemapper.md) | GradeValueMapper static utility + ScrumGradeValue.toNumeric() with implementation sketch | P7-03 |

## Quick Reference — Class Responsibilities

```
POST /submissions/{id}/grade
  GradingController
    └─ RubricGradingService
         ├─ DeliverableSubmissionRepository   (resolve submission → deliverableId, groupId)
         ├─ CommitteeRepository               (deliverable-scoped committee auth check)
         ├─ RubricCriterionRepository         (fetch gradingType per criterion)
         ├─ GradeValueMapper                  (validateGrade, toNumeric)
         └─ RubricGradeRepository             (upsert per reviewer+criterion+submission)

GET /students/{studentId}/grade/calculate
  GradingController (auth pre-check)
    └─ FinalGradeCalculationService
         ├─ StudentRepository                 (findByStudentId → student.githubUsername)
         ├─ GroupMembershipRepository         (resolve student → group)
         ├─ DeliverableRepository             (all deliverables in term)
         ├─ SprintDeliverableMappingRepository(contributing sprints per deliverable)
         ├─ ScrumGradeRepository              (pointA/B grades per group+sprint)
         ├─ DeliverableSubmissionRepository   (latest submission per group+deliverable)
         ├─ RubricGradeRepository             (all reviewer grades for B computation)
         ├─ GradeValueMapper                  (toNumeric for B computation)
         ├─ SprintTrackingLogRepository       (C_i: filter by githubUsername + prMerged)
         ├─ SprintRepository                  (targetSP sum across all term sprints)
         └─ FinalGradeRepository              (upsert final result)
```

## Formula Cheat Sheet

```
ScrumScalar  = AVG(pointAGrade.toNumeric()) / 100       [skip sprints with no ScrumGrade]
ReviewScalar = AVG(pointBGrade.toNumeric()) / 100
DS           = (ScrumScalar + ReviewScalar) / 2.0

B            = AVG_reviewers( Σ(numericGrade × weight) / Σ(weight) )

ScaledGrade  = B × DS
WeightedTotal += ScaledGrade × (deliverable.weight / 100)

C_i = completedSP / targetSP   [prMerged=true, github username match; NOT capped at 1.0]
G_i = WeightedTotal × C_i      ← persisted to FinalGrade table
```
