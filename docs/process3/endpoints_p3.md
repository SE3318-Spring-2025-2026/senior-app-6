# Process 3 — API Endpoints
## Advisor Association & Sanitization (Sub-Processes 3.1–3.5)

> Sources: `dfd_level1_process3.md`, `process3_dfd_lvl1.drawio`
> All endpoints are under base path `/api/`. Auth via `Authorization: Bearer <JWT>`.
> SP scale: Fibonacci (1, 2, 3, 5, 8). Difficulty: Easy / Medium / Hard.
> Builds directly on P2 output — `ProjectGroup`, `GroupMembership`, `ScheduleWindow` entities must exist.

---

## Conventions

| Item | Rule |
|------|------|
| Primary keys | UUID |
| Timestamps | ISO-8601 (`LocalDateTime`) |
| Student JWT | `sub="Student"`, claim `studentId` |
| Staff JWT | `sub="StaffUser"`, claim `role` |
| Error body | `{ "error": "Human-readable message" }` |

### HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | OK |
| 201 | Resource created |
| 400 | Business rule violation |
| 403 | Role / ownership mismatch |
| 404 | Resource not found |
| 409 | Conflict (duplicate / already active request) |

---

## 3.1 — Browse Advisors & Send Request

### `GET /api/advisors` — SP: 2 | Difficulty: Easy
**Auth:** Student JWT | **Issue:** P3-API-01

Returns all `Professor` staff users who are **below capacity** for the active term.
`termId` is resolved server-side via `TermConfigService.getActiveTermId()` — never passed by the client.
Capacity is stored on `StaffUser.advisorCapacity` (default 5).

**Query params:** _(none)_

**Response 200:**
```json
[
  {
    "advisorId": "uuid",
    "name": "string",
    "mail": "string",
    "currentGroupCount": 2,
    "capacity": 5
  }
]
```

---

### `POST /api/groups/{groupId}/advisor-request` — SP: 5 | Difficulty: Hard
**Auth:** Student JWT (must be `TEAM_LEADER` of `groupId`) | **Issue:** P3-API-01

Checks schedule window → group status → existing PENDING request → advisor capacity → creates `AdvisorRequest`.

**Request:**
```json
{
  "advisorId": "uuid"
}
```

**Response 201:**
```json
{
  "requestId": "uuid",
  "groupId": "uuid",
  "advisorId": "uuid",
  "status": "PENDING",
  "sentAt": "ISO-8601"
}
```

**Errors:**
```
403  { "error": "Only the Team Leader can send advisor requests" }
400  { "error": "Advisor association window is not currently active" }
400  { "error": "Group must be in TOOLS_BOUND status to request an advisor" }
409  { "error": "Group already has an active pending advisor request" }
400  { "error": "Advisor has reached maximum group capacity for this term" }
404  { "error": "Advisor not found" }
```

---

### `GET /api/groups/{groupId}/advisor-request` — SP: 1 | Difficulty: Easy
**Auth:** Student JWT (must be member of `groupId`) | **Issue:** P3-API-01

Returns the most recent `AdvisorRequest` for the group (any status).

**Response 200:**
```json
{
  "requestId": "uuid",
  "advisorId": "uuid",
  "advisorName": "string",
  "status": "PENDING | ACCEPTED | REJECTED | AUTO_REJECTED | CANCELLED",
  "sentAt": "ISO-8601",
  "respondedAt": "ISO-8601 | null"
}
```

**Errors:**
```
404  { "error": "No advisor request found for this group" }
403  { "error": "You are not a member of this group" }
```

---

### `DELETE /api/groups/{groupId}/advisor-request` — SP: 2 | Difficulty: Easy
**Auth:** Student JWT (must be `TEAM_LEADER` of `groupId`) | **Issue:** P3-API-01

Cancels the active `PENDING` advisor request. Sets status to `CANCELLED`.

**Response 200:**
```json
{
  "requestId": "uuid",
  "status": "CANCELLED"
}
```

**Errors:**
```
403  { "error": "Only the Team Leader can cancel advisor requests" }
400  { "error": "No active pending request to cancel" }
404  { "error": "Group not found" }
404  { "error": "No advisor request found for this group" }
```

---

## 3.2 — Advisor Reviews Pending Requests

### `GET /api/advisor/requests` — SP: 2 | Difficulty: Easy
**Auth:** Staff JWT (Role = `Professor`) | **Issue:** P3-API-02

Returns all `PENDING` advisor requests addressed to the authenticated professor.

**Response 200:**
```json
[
  {
    "requestId": "uuid",
    "groupId": "uuid",
    "groupName": "string",
    "termId": "string",
    "memberCount": 4,
    "sentAt": "ISO-8601"
  }
]
```

---

### `GET /api/advisor/requests/{requestId}` — SP: 2 | Difficulty: Easy
**Auth:** Staff JWT (Role = `Professor`, must be the target advisor) | **Issue:** P3-API-02

Returns full request detail including group members and group's Proposal submission summary (read-only context for the advisor to decide).

**Response 200:**
```json
{
  "requestId": "uuid",
  "group": {
    "id": "uuid",
    "groupName": "string",
    "termId": "string",
    "status": "TOOLS_BOUND",
    "members": [
      { "studentId": "string", "role": "TEAM_LEADER | MEMBER", "joinedAt": "ISO-8601" }
    ]
  },
  "sentAt": "ISO-8601"
}
```

**Errors:**
```
403  { "error": "This request is not addressed to you" }
404  { "error": "Request not found" }
```

---

## 3.3 — Process Association Response

### `PATCH /api/advisor/requests/{requestId}/respond` — SP: 5 | Difficulty: Hard
**Auth:** Staff JWT (Role = `Professor`, must be the target advisor) | **Issue:** P3-API-03

On `ACCEPTED`:
- Sets `group.advisorId`
- Sets `group.status` → `ADVISOR_ASSIGNED`
- All other `PENDING` requests for the same group → `AUTO_REJECTED`
- All atomic in one transaction

On `REJECTED`:
- Only this request's status → `REJECTED`
- Group status remains `TOOLS_BOUND`
- Group can send a new request

**Request:**
```json
{
  "accept": true
}
```

**Response 200 (accepted):**
```json
{
  "requestId": "uuid",
  "status": "ACCEPTED",
  "groupId": "uuid",
  "groupStatus": "ADVISOR_ASSIGNED"
}
```

**Response 200 (rejected):**
```json
{
  "requestId": "uuid",
  "status": "REJECTED"
}
```

**Errors:**
```
403  { "error": "This request is not addressed to you" }
400  { "error": "Request is no longer pending" }
404  { "error": "Request not found" }
```

---

## 3.4 — Auto-Sanitization

### `POST /api/coordinator/sanitize` — SP: 3 | Difficulty: Medium
**Auth:** Staff JWT (Role = `Coordinator`) | **Issue:** P3-API-04

Manually triggers the sanitization job. Normally runs automatically when the `ADVISOR_ASSOCIATION` window `closesAt` passes.

Disbands all groups **without** an assigned advisor (status `FORMING`, `TOOLS_PENDING`, `TOOLS_BOUND`).
Hard-deletes all `GroupMembership` rows for each disbanded group (freeing students to rejoin next term).
All `PENDING` advisor requests for disbanded groups → `AUTO_REJECTED`.

**Request (optional body):**
```json
{ "force": true }
```
Omit body or send `{}` for normal trigger (window must be closed). Include `{ "force": true }` to trigger early while window is still active.

**Response 200:**
```json
{
  "disbandedCount": 3,
  "autoRejectedRequestCount": 5,
  "triggeredAt": "ISO-8601"
}
```

**Errors:**
```
400  { "error": "Advisor association window is still active — cannot sanitize early without confirmation" }
```

---

## 3.5 — Coordinator Advisor Override

### `GET /api/coordinator/advisors` — SP: 1 | Difficulty: Easy
**Auth:** Staff JWT (Role = `Coordinator`) | **Issue:** P3-API-05

Lists all professors with their current group assignment count and capacity for the active term. Includes advisors at capacity (unlike the student-facing endpoint).
`termId` resolved server-side via `TermConfigService.getActiveTermId()`.

**Query params:** _(none)_

**Response 200:**
```json
[
  {
    "advisorId": "uuid",
    "name": "string",
    "mail": "string",
    "currentGroupCount": 5,
    "capacity": 5,
    "atCapacity": true
  }
]
```

---

### `PATCH /api/coordinator/groups/{groupId}/advisor` — SP: 3 | Difficulty: Medium
**Auth:** Staff JWT (Role = `Coordinator`) | **Issue:** P3-API-05

Bypasses the request flow and window check entirely.

- `ASSIGN`: sets `group.advisorId`, status → `ADVISOR_ASSIGNED`. Capacity constraint **not** enforced. Auto-rejects any `PENDING` advisor requests for the group.
- `REMOVE`: clears `group.advisorId`, status → `TOOLS_BOUND`.

**Request:**
```json
{
  "action": "ASSIGN | REMOVE",
  "advisorId": "uuid | null"
}
```

> `advisorId` is **required** when `action = "ASSIGN"` (return 400 if absent), **optional/nullable** when `action = "REMOVE"`. DTO field should be `@Nullable` — do not use `@NotNull`.

**Response 200:**
```json
{
  "groupId": "uuid",
  "status": "ADVISOR_ASSIGNED | TOOLS_BOUND",
  "advisorId": "uuid | null"
}
```

**Errors:**
```
400  { "error": "advisorId is required for ASSIGN action" }
400  { "error": "Group already has this advisor assigned" }
400  { "error": "Group has no advisor to remove" }
404  { "error": "Group not found" }
404  { "error": "Advisor not found" }
```

---

## Endpoint Summary Table

| # | Method | Path | Auth | Sub-process | Issue | SP | Difficulty |
|---|--------|------|------|-------------|-------|----|------------|
| 1 | GET | `/api/advisors` | Student | 3.1 | P3-API-01 | 2 | Easy |
| 2 | POST | `/api/groups/{groupId}/advisor-request` | Student (TEAM_LEADER) | 3.1 | P3-API-01 | 5 | Hard |
| 3 | GET | `/api/groups/{groupId}/advisor-request` | Student (member) | 3.1 | P3-API-01 | 1 | Easy |
| 4 | DELETE | `/api/groups/{groupId}/advisor-request` | Student (TEAM_LEADER) | 3.1 | P3-API-01 | 2 | Easy |
| 5 | GET | `/api/advisor/requests` | Staff (Professor) | 3.2 | P3-API-02 | 2 | Easy |
| 6 | GET | `/api/advisor/requests/{requestId}` | Staff (Professor) | 3.2 | P3-API-02 | 2 | Easy |
| 7 | PATCH | `/api/advisor/requests/{requestId}/respond` | Staff (Professor) | 3.3 | P3-API-03 | 5 | Hard |
| 8 | POST | `/api/coordinator/sanitize` | Staff (Coordinator) | 3.4 | P3-API-04 | 3 | Medium |
| 9 | GET | `/api/coordinator/advisors` | Staff (Coordinator) | 3.5 | P3-API-05 | 1 | Easy |
| 10 | PATCH | `/api/coordinator/groups/{groupId}/advisor` | Staff (Coordinator) | 3.5 | P3-API-05 | 3 | Medium |

**Total SP: 26**

---

## Issue Breakdown

### P3-API-01 — Advisor Browse & Request (DFD 3.1)
**SP: 10** | Depends on: P2 INFRA-01 (entities), P2 API-01 (ProjectGroup exists), B-07 (Student JWT)

Endpoints 1–4. The `POST` is the hardest — five precondition checks before any write.

**Acceptance criteria:**
- [ ] `GET /api/advisors` only returns advisors where `currentGroupCount < capacity`
- [ ] `POST` returns 400 if `ADVISOR_ASSOCIATION` window is not active for the group's `termId`
- [ ] `POST` returns 400 if group status is not `TOOLS_BOUND`
- [ ] `POST` returns 409 if group already has a `PENDING` request (regardless of target advisor)
- [ ] `POST` returns 400 if target advisor is at capacity
- [ ] `DELETE` returns 400 if no `PENDING` request exists

---

### P3-API-02 — Advisor Request Review (DFD 3.2)
**SP: 4** | Depends on: P3-API-01

Endpoints 5–6. Pure reads. Detail endpoint includes group member list.

**Acceptance criteria:**
- [ ] `GET /api/advisor/requests` returns empty list (not 404) when no pending requests exist
- [ ] `GET /api/advisor/requests/{id}` returns 403 if the authenticated professor is not the target advisor
- [ ] Neither endpoint modifies any state

---

### P3-API-03 — Advisor Association Response (DFD 3.3)
**SP: 5** | Depends on: P3-API-02

Endpoint 7. Hardest endpoint in P3.

**Acceptance criteria:**
- [ ] On `accept: true` — `group.advisorId` is set and `group.status` = `ADVISOR_ASSIGNED` in DB
- [ ] On `accept: true` — all other `PENDING` requests for the same group are `AUTO_REJECTED` in the same transaction
- [ ] On `accept: false` — only the target request is updated; group status unchanged; no other requests affected
- [ ] Returns 400 if request is not `PENDING`
- [ ] Returns 403 if authenticated professor is not the target advisor

---

### P3-API-04 — Auto-Sanitization (DFD 3.4)
**SP: 3** | Depends on: P3-API-01, `@Scheduled` job setup

Endpoint 8 + scheduled job.

**Acceptance criteria:**
- [ ] Scheduled job fires automatically when `ADVISOR_ASSOCIATION` window `closesAt` passes
- [ ] Manual trigger via `POST /api/coordinator/sanitize` produces identical result
- [ ] Only groups without `advisorId` are disbanded — `ADVISOR_ASSIGNED` groups are untouched
- [ ] All `PENDING` advisor requests for disbanded groups are `AUTO_REJECTED` atomically
- [ ] `SanitizationReport` response includes correct counts
- [ ] `force: true` body allows early trigger while window is still active

---

### P3-API-05 — Coordinator Advisor Override (DFD 3.5)
**SP: 4** | Depends on: P3-API-01

Endpoints 9–10.

**Acceptance criteria:**
- [ ] `ASSIGN` works even if advisor is at capacity (capacity not enforced for coordinator)
- [ ] `ASSIGN` works even if `ADVISOR_ASSOCIATION` window is closed (window not checked for coordinator)
- [ ] `ASSIGN` auto-rejects any `PENDING` advisor requests for the group
- [ ] `REMOVE` sets `group.status` back to `TOOLS_BOUND` and clears `group.advisorId`
- [ ] Both actions return 403 for Staff JWT with role `Professor` or `Admin`

---

## New Entities Required

### `AdvisorRequest`

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID PK | |
| `group` | `@ManyToOne ProjectGroup` | FK, NOT NULL |
| `advisor` | `@ManyToOne StaffUser` | FK, NOT NULL |
| `status` | `AdvisorRequestStatus` enum | default `PENDING` |
| `sentAt` | LocalDateTime | NOT NULL |
| `respondedAt` | LocalDateTime | NULLABLE |

One active `PENDING` request per group enforced at service layer (409), not DB constraint.

### `AdvisorRequestRepository` (required methods)

| Method | Used by |
|--------|---------|
| `findByGroupIdAndStatus(UUID groupId, AdvisorRequestStatus status)` | 3.1 (duplicate check, cancel) |
| `findByAdvisorIdAndStatus(UUID advisorId, AdvisorRequestStatus status)` | 3.2 (list pending for professor) |
| `findById(UUID id)` | 3.3, 3.2 detail |
| `findTopByGroupIdOrderBySentAtDesc(UUID groupId)` | 3.1 GET request status |
| `@Modifying @Query bulkUpdateStatus(UUID groupId, AdvisorRequestStatus oldStatus, AdvisorRequestStatus newStatus, UUID excludeId)` | 3.3 ACCEPT (auto-reject other requests for same group) |
| `@Modifying @Query bulkUpdateStatusByGroupId(UUID groupId, AdvisorRequestStatus oldStatus, AdvisorRequestStatus newStatus)` | 3.4 sanitization, 3.5 ASSIGN |

### `ProjectGroup` additions (P3 extends P2 entity)

| Field | Type | Notes |
|-------|------|-------|
| `advisorId` | UUID (FK to `StaffUser`) | NULLABLE — set on `ADVISOR_ASSIGNED` |

### `StaffUser` additions

| Field | Type | Notes |
|-------|------|-------|
| `advisorCapacity` | int | Default 5 — max groups per term for Professor role |

---

## GroupStatus State Machine (full — P2 + P3)

```
FORMING ──(first tool bound)──────────────► TOOLS_PENDING
FORMING ──(both tools bound simultaneously)► TOOLS_BOUND
TOOLS_PENDING ──(second tool bound)────────► TOOLS_BOUND
TOOLS_BOUND ──(advisor accepts)────────────► ADVISOR_ASSIGNED
FORMING       ──(sanitization: window closes)► DISBANDED
TOOLS_PENDING ──(sanitization: window closes)► DISBANDED
TOOLS_BOUND   ──(sanitization: window closes)► DISBANDED
ADVISOR_ASSIGNED ──(coordinator removes)───► TOOLS_BOUND
Any state ──(coordinator disbands)─────────► DISBANDED
```

---

## AdvisorRequestStatus Enum

```
PENDING       — sent by TEAM_LEADER, awaiting advisor response
ACCEPTED      — advisor accepted; group.advisorId is now set
REJECTED      — advisor declined; group may send a new request
AUTO_REJECTED — rejected when group's other request was accepted, or group was disbanded
CANCELLED     — TEAM_LEADER withdrew the request before advisor responded
```

---

## Security Notes

| Rule | Detail |
|------|--------|
| Student endpoints | Require `sub="Student"` JWT |
| Professor endpoints (`/api/advisor/**`) | Require `role=PROFESSOR` Staff JWT — `SecurityConfig` must add `hasRole("PROFESSOR")` for `/api/advisor/**` (Blue Team action item from sequence 3.2 note) |
| Coordinator endpoints (`/api/coordinator/**`) | Require `role=COORDINATOR` Staff JWT — already enforced by `SecurityConfig` |
| Admin role | `ADMIN` role never has `COORDINATOR` or `PROFESSOR` — it is blocked from all P2/P3 endpoints automatically by `SecurityConfig`. No extra manual check needed. |
| `advisorId` in responses | Always returned as UUID — no sensitive data exposed |
