# Audit Log QA Issues — Summary

## QA-1: End-to-End DB Verification for PR #285 Audit Call Sites

### Problem

PR #285 added 23 audit call sites across 5 services (`AuthService`, `GroupService`,
`InvitationService`, `AdvisorService`, `ScrumGradingService`).
No manual or automated end-to-end DB verification was performed at merge time.

### Deliverable

Execute TC-AUDIT-01 through TC-AUDIT-15 (spec: `audit-log-query-qa.md` Part 1).
Record results in a test results table.

### Key Risks

| Risk | Call Sites |
|---|---|
| FAILURE rows may not commit if `REQUIRES_NEW` is missing or broken | `GroupService:205`, `GroupService:302` |
| Coordinator `userId` null due to empty `SecurityContextHolder` | `GroupService:508/558/617`, `AdvisorService:620/664` |
| `occurredAt` never set | all 23 call sites |
| `userType` swapped (STAFF stored as STUDENT) | all login call sites |

### Known Gap (resolved)

TC-AUDIT-11/12/13/14 verify the FIX-2 fix from PR #297:
coordinator `userId` was null before `#297` merged because `currentUserId()` relied on
a `SecurityContextHolder` principal that was empty in certain coordinator flows.

### NFR Coverage

- NFR-6 (audit trail completeness)
- NFR-8 (transaction isolation — `REQUIRES_NEW`)

### Dependencies

- PR #285 merged ✓
- PR #297 (FIX-2) merged ✓
