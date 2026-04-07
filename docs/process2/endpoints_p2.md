# Process 2 — API Endpoints
## Group Creation & Tool Integration (Sub-Processes 2.1–2.6)

> Sources: `process2_dfd_lvl1.drawio`, `dfd_level1_process2.md`
> All endpoints are under base path `/api/`. Auth via `Authorization: Bearer <JWT>`.
> SP scale: Fibonacci (1, 2, 3, 5, 8). Difficulty: Easy / Medium / Hard.

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
| 409 | Conflict (duplicate) |
| 422 | External service validation failure |

---

## 2.1 — Validate Schedule & Create Group

### `POST /api/groups` — SP: 3 | Difficulty: Medium
**Auth:** Student JWT | **Issue:** API-01

Checks `D1: Term Config` for an active `GROUP_CREATION` schedule window before creating the group. `termId` is resolved server-side via `TermConfigService.getActiveTermId()` — never passed by the client. The requesting student is automatically added as `TEAM_LEADER`.

**Request:**
```json
{
  "groupName": "string"
}
```

**Response 201:**
```json
{
  "id": "uuid",
  "groupName": "string",
  "termId": "string",
  "status": "FORMING",
  "createdAt": "ISO-8601",
  "members": [
    { "studentId": "string", "role": "TEAM_LEADER", "joinedAt": "ISO-8601" }
  ]
}
```

**Errors:**
```
400  { "error": "Group creation window is not currently active" }
400  { "error": "You are already a member of a group" }
409  { "error": "A group named '{name}' already exists for this term" }
```

---

### `GET /api/groups/my` — SP: 1 | Difficulty: Easy
**Auth:** Student JWT | **Issue:** API-01

Returns the full `GroupDetailResponse` for the student's current group.

**Response 200:**
```json
{
  "id": "uuid",
  "groupName": "string",
  "termId": "string",
  "status": "FORMING | TOOLS_PENDING | TOOLS_BOUND | ADVISOR_ASSIGNED | DISBANDED",
  "createdAt": "ISO-8601",
  "jiraSpaceUrl": "string | null",
  "jiraProjectKey": "string | null",
  "jiraBound": false,
  "githubOrgName": "string | null",
  "githubBound": false,
  "members": [
    { "studentId": "string", "role": "TEAM_LEADER | MEMBER", "joinedAt": "ISO-8601" }
  ]
}
```

**Errors:**
```
404  { "error": "You are not a member of any group" }
```

---

## 2.2 — Search Student & Send Invitation

### `GET /api/students/search?q={query}` — SP: 2 | Difficulty: Easy
**Auth:** Student JWT | **Issue:** API-02

Searches `D2: Users` for students who are **not** currently in any group. Minimum 3 characters required.

**Query params:**
- `q` — partial match on `studentId` (required, min 3 chars)

**Response 200:**
```json
[
  {
    "studentId": "string",
    "githubUsername": "string | null",
    "inGroup": false
  }
]
```

**Errors:**
```
400  { "error": "Query must be at least 3 characters" }
```

---

### `POST /api/groups/{groupId}/invitations` — SP: 3 | Difficulty: Medium
**Auth:** Student JWT (must be `TEAM_LEADER` of `groupId`) | **Issue:** API-02

Looks up target student in `D2: Users`, checks they are not already in a group, then creates a `PENDING` invitation record.

**Request:**
```json
{
  "targetStudentId": "string"
}
```

**Response 201:**
```json
{
  "invitationId": "uuid",
  "groupId": "uuid",
  "targetStudentId": "string",
  "status": "PENDING",
  "sentAt": "ISO-8601"
}
```

**Errors:**
```
403  { "error": "Only the Team Leader can send invitations" }
400  { "error": "Cannot send invitation from a disbanded group" }
400  { "error": "Student '{id}' is not registered in the system" }
400  { "error": "Student '{id}' is already a member of a group" }
400  { "error": "Group has reached maximum team size" }
409  { "error": "A pending invitation already exists for this student" }
```

---

### `GET /api/groups/{groupId}/invitations` — SP: 1 | Difficulty: Easy
**Auth:** Student JWT (must be `TEAM_LEADER` of `groupId`) | **Issue:** API-02

Returns all invitations sent by this group (all statuses).

**Response 200:**
```json
[
  {
    "invitationId": "uuid",
    "targetStudentId": "string",
    "status": "PENDING | ACCEPTED | DECLINED | AUTO_DENIED",
    "sentAt": "ISO-8601"
  }
]
```

**Errors:**
```
403  { "error": "Only the Team Leader can view group invitations" }
404  { "error": "Group not found" }
```

---

### `DELETE /api/invitations/{invitationId}` — SP: 2 | Difficulty: Easy
**Auth:** Student JWT (must be `TEAM_LEADER` of the inviting group) | **Issue:** API-02

Cancels a `PENDING` invitation before the invitee responds.

**Response 200:**
```json
{ "invitationId": "uuid", "status": "CANCELLED" }
```

**Errors:**
```
403  { "error": "Only the Team Leader can cancel invitations" }
400  { "error": "Invitation is no longer pending" }
404  { "error": "Invitation not found" }
```

---

## 2.3 — Process Invitation Response

### `GET /api/invitations/pending` — SP: 1 | Difficulty: Easy
**Auth:** Student JWT | **Issue:** API-03

Returns all `PENDING` invitations addressed to the authenticated student.

**Response 200:**
```json
[
  {
    "invitationId": "uuid",
    "groupId": "uuid",
    "groupName": "string",
    "teamLeaderStudentId": "string",
    "sentAt": "ISO-8601"
  }
]
```

---

### `PATCH /api/invitations/{invitationId}/respond` — SP: 5 | Difficulty: Hard
**Auth:** Student JWT (must be the invitee) | **Issue:** API-03

On `ACCEPTED`: creates a `GroupMembership` record with `role=MEMBER`, auto-denies all other `PENDING` invitations for the same student in the same transaction.
On `DECLINED`: updates only this invitation's status.

**Request:**
```json
{
  "accept": true
}
```

**Response 200 (accepted):** Full `GroupDetailResponse` (same shape as `GET /api/groups/my`)

**Response 200 (declined):**
```json
{
  "invitationId": "uuid",
  "status": "DECLINED"
}
```

**Errors:**
```
403  { "error": "This invitation does not belong to you" }
400  { "error": "Invitation is no longer pending" }
404  { "error": "Invitation not found" }
```

---

## 2.4 — Validate & Bind JIRA

### `POST /api/groups/{groupId}/jira` — SP: 5 | Difficulty: Hard
**Auth:** Student JWT (must be `TEAM_LEADER` of `groupId`) | **Issue:** API-04

Performs a live test call to JIRA API before storing anything. Stores `jiraSpaceUrl` and `jiraProjectKey` in plaintext; `jiraApiToken` is encrypted (AES-256-GCM) before persisting. Status transitions to `TOOLS_BOUND` if GitHub is already bound.

**Request:**
```json
{
  "jiraSpaceUrl": "string",
  "jiraProjectKey": "string",
  "jiraApiToken": "string"
}
```

**Response 200:**
```json
{
  "groupId": "uuid",
  "status": "FORMING | TOOLS_PENDING | TOOLS_BOUND",
  "jiraSpaceUrl": "string",
  "jiraProjectKey": "string",
  "jiraBound": true
}
```

**Errors:**
```
403  { "error": "Only the Team Leader can bind tool integrations" }
422  { "error": "JIRA validation failed: JIRA space URL is unreachable" }
422  { "error": "JIRA validation failed: Project key '{key}' not found" }
422  { "error": "JIRA validation failed: API token is invalid or expired" }
```

---

## 2.5 — Validate & Bind GitHub

### `POST /api/groups/{groupId}/github` — SP: 5 | Difficulty: Hard
**Auth:** Student JWT (must be `TEAM_LEADER` of `groupId`) | **Issue:** API-05

Performs two test calls: `GET /orgs/{org}` (org existence + PAT validity) and `GET /orgs/{org}/repos` (confirms `repo` scope). Stores `githubOrgName` in plaintext; PAT is AES-256-GCM encrypted. Status transitions to `TOOLS_BOUND` if JIRA is already bound.

**Request:**
```json
{
  "githubOrgName": "string",
  "githubPat": "string"
}
```

**Response 200:**
```json
{
  "groupId": "uuid",
  "status": "FORMING | TOOLS_PENDING | TOOLS_BOUND",
  "githubOrgName": "string",
  "githubBound": true
}
```

**Errors:**
```
403  { "error": "Only the Team Leader can bind tool integrations" }
422  { "error": "GitHub validation failed: PAT is invalid or expired" }
422  { "error": "GitHub validation failed: PAT lacks required 'repo' scope" }
422  { "error": "GitHub validation failed: Organization '{name}' not found" }
```

---

## 2.6 — Coordinator Group Override

### `GET /api/coordinator/groups` — SP: 2 | Difficulty: Easy
**Auth:** Staff JWT (Role = `Coordinator`) | **Issue:** API-06

> `termId` is resolved server-side via `TermConfigService.getActiveTermId()` — no query param required.

**Query params:** _(none)_

**Response 200:**
```json
[
  {
    "id": "uuid",
    "groupName": "string",
    "termId": "string",
    "status": "FORMING | TOOLS_PENDING | TOOLS_BOUND | ADVISOR_ASSIGNED | DISBANDED",
    "memberCount": 4,
    "jiraBound": true,
    "githubBound": false
  }
]
```

---

### `GET /api/coordinator/groups/{groupId}` — SP: 1 | Difficulty: Easy
**Auth:** Staff JWT (Role = `Coordinator`) | **Issue:** API-06

Returns full group detail including members. Encrypted tokens are **never** returned.

**Response 200:**
```json
{
  "id": "uuid",
  "groupName": "string",
  "termId": "string",
  "status": "string",
  "createdAt": "ISO-8601",
  "jiraSpaceUrl": "string | null",
  "jiraProjectKey": "string | null",
  "jiraBound": true,
  "githubOrgName": "string | null",
  "githubBound": true,
  "members": [
    { "studentId": "string", "role": "TEAM_LEADER | MEMBER", "joinedAt": "ISO-8601" }
  ]
}
```

**Errors:**
```
404  { "error": "Group not found" }
```

---

### `PATCH /api/coordinator/groups/{groupId}/members` — SP: 3 | Difficulty: Medium
**Auth:** Staff JWT (Role = `Coordinator`) | **Issue:** API-06

Overrides the normal invitation flow. Removing the `TEAM_LEADER` is blocked.

**Request:**
```json
{
  "studentId": "string",
  "action": "ADD | REMOVE"
}
```

**Response 200:** Full `GroupDetailResponse` (same shape as `GET /api/coordinator/groups/{groupId}`)

**Errors:**
```
400  { "error": "Cannot remove Team Leader; transfer leadership first" }
400  { "error": "Student '{id}' is already a member of a group" }
404  { "error": "Group not found" }
404  { "error": "Student '{id}' not found" }
```

---

### `PATCH /api/coordinator/groups/{groupId}/disband` — SP: 3 | Difficulty: Medium
**Auth:** Staff JWT (Role = `Coordinator`) | **Issue:** API-06

Sets group `status` to `DISBANDED`. All `GroupMembership` rows for the group are hard-deleted (freeing members to join new groups). All `PENDING` invitations for members are set to `AUTO_DENIED`. Group record itself is retained.

**Request:** _(no body)_

**Response 200:**
```json
{
  "groupId": "uuid",
  "status": "DISBANDED"
}
```

**Errors:**
```
400  { "error": "Group is already disbanded" }
404  { "error": "Group not found" }
```

---

## Endpoint Summary Table

| # | Method | Path | Auth | Sub-process | Issue | SP | Difficulty |
|---|--------|------|------|-------------|-------|----|------------|
| 1 | POST | `/api/groups` | Student | 2.1 | API-01 | 3 | Medium |
| 2 | GET | `/api/groups/my` | Student | 2.1 | API-01 | 1 | Easy |
| 3 | GET | `/api/students/search?q=` | Student | 2.2 | API-02 | 2 | Easy |
| 4 | POST | `/api/groups/{groupId}/invitations` | Student (TEAM_LEADER) | 2.2 | API-02 | 3 | Medium |
| 5 | GET | `/api/groups/{groupId}/invitations` | Student (TEAM_LEADER) | 2.2 | API-02 | 1 | Easy |
| 6 | DELETE | `/api/invitations/{invitationId}` | Student (TEAM_LEADER) | 2.2 | API-02 | 2 | Easy |
| 7 | GET | `/api/invitations/pending` | Student | 2.3 | API-03 | 1 | Easy |
| 8 | PATCH | `/api/invitations/{invitationId}/respond` | Student (invitee) | 2.3 | API-03 | 5 | Hard |
| 9 | POST | `/api/groups/{groupId}/jira` | Student (TEAM_LEADER) | 2.4 | API-04 | 5 | Hard |
| 10 | POST | `/api/groups/{groupId}/github` | Student (TEAM_LEADER) | 2.5 | API-05 | 5 | Hard |
| 11 | GET | `/api/coordinator/groups` | Staff (Coordinator) | 2.6 | API-06 | 2 | Easy |
| 12 | GET | `/api/coordinator/groups/{groupId}` | Staff (Coordinator) | 2.6 | API-06 | 1 | Easy |
| 13 | PATCH | `/api/coordinator/groups/{groupId}/members` | Staff (Coordinator) | 2.6 | API-06 | 3 | Medium |
| 14 | PATCH | `/api/coordinator/groups/{groupId}/disband` | Staff (Coordinator) | 2.6 | API-06 | 3 | Medium |

**Total SP: 37**

---

## Issue Breakdown

> Issues group endpoints that share a service layer and can be developed, reviewed, and merged together. Each issue assumes the infrastructure issues from `estimated_issues_process2.md` (B-01 through B-07, B-17, B-18) are completed first.

---

### API-01 — Group Setup Endpoints
**Story Points: 4** | **Sub-process: 2.1** | **Depends on: B-01, B-02, B-03, B-05, B-07, B-08, B-17, B-18**

Implements the group creation controller and service method. The `POST` endpoint is the most business-logic-heavy of this issue — it must atomically create the `ProjectGroup` and the `TEAM_LEADER` membership row after checking the schedule window and duplicate name constraints.

**Endpoints:**

| Method | Path | SP | Difficulty |
|--------|------|----|------------|
| POST | `/api/groups` | 3 | Medium |
| GET | `/api/groups/my` | 1 | Easy |

**What makes it Medium:**
- Must query `D1: ScheduleWindow` to verify an active `GROUP_CREATION` window exists for the given `termId`
- Three pre-condition checks before any write (window active, student not already in group, name unique per term)
- Group + membership created atomically in a single `@Transactional` method

**Acceptance criteria:**
- [ ] `POST /api/groups` returns 400 if the schedule window is closed for the given `termId`
- [ ] `POST /api/groups` returns 400 if the authenticated student is already in any group
- [ ] `POST /api/groups` returns 409 if `groupName` already exists in the same `termId`
- [ ] `POST /api/groups` creates both a `ProjectGroup` row (status=`FORMING`) and a `GroupMembership` row (role=`TEAM_LEADER`) atomically
- [ ] `GET /api/groups/my` returns 404 when the student has no membership
- [ ] `GET /api/groups/my` reflects updated `status` and tool-binding fields after subsequent bind operations

---

### API-02 — Invitation Dispatch Endpoints
**Story Points: 8** | **Sub-process: 2.2** | **Depends on: B-03, B-04, B-05, B-09, B-17, B-18**

Implements all invitation-sending operations from the TEAM_LEADER perspective. The `POST` endpoint has the most logic (4 pre-condition checks); search and list are straightforward queries.

**Endpoints:**

| Method | Path | SP | Difficulty |
|--------|------|----|------------|
| GET | `/api/students/search?q=` | 2 | Easy |
| POST | `/api/groups/{groupId}/invitations` | 3 | Medium |
| GET | `/api/groups/{groupId}/invitations` | 1 | Easy |
| DELETE | `/api/invitations/{invitationId}` | 2 | Easy |

**What makes POST Medium:**
- TEAM_LEADER authorization check against `GroupMembership`
- Target student must exist in `D2: Users` and must not already belong to any group
- Duplicate-invitation guard: a second `PENDING` invite to the same student → 409 (not a DB unique constraint — service-layer check)

**Acceptance criteria:**
- [ ] `GET /api/students/search` only returns students where `existsByStudentId(id)` is `false`
- [ ] `POST` returns 403 when caller is not TEAM_LEADER of `groupId`
- [ ] `POST` returns 400 when target student is already in any group
- [ ] `POST` returns 409 when a `PENDING` invitation to the same student already exists from this group
- [ ] `GET invitations` (list) returns all statuses, not just `PENDING`
- [ ] `DELETE` returns 400 if the invitation is not in `PENDING` status

---

### API-03 — Invitation Response Endpoints
**Story Points: 6** | **Sub-process: 2.3** | **Depends on: B-03, B-04, B-09, B-17, B-18**

The `PATCH /respond` endpoint is the most complex single endpoint in this process. The auto-denial side-effect (bulk-updating all other `PENDING` invitations for the accepting student) must execute atomically — no partial state allowed.

**Endpoints:**

| Method | Path | SP | Difficulty |
|--------|------|----|------------|
| GET | `/api/invitations/pending` | 1 | Easy |
| PATCH | `/api/invitations/{invitationId}/respond` | 5 | Hard |

**What makes PATCH Hard:**
- Dual response shape depending on `accept` value (two different DTO types from one method)
- On accept: creates `GroupMembership` + bulk-updates competing invitations to `AUTO_DENIED` in the same transaction
- Must verify the invitee's JWT identity against the invitation's `inviteeId` — 403 if they don't match
- Race condition risk: two concurrent accepts of different invitations by the same student must not result in two memberships (handled by the transactional bulk-deny)

**Acceptance criteria:**
- [ ] `GET /api/invitations/pending` returns empty list (not 404) when no pending invitations exist
- [ ] `PATCH` with `accept: true` creates a `GroupMembership` row with `role=MEMBER`
- [ ] `PATCH` with `accept: true` sets all other `PENDING` invitations for the same student to `AUTO_DENIED` in the same transaction
- [ ] `PATCH` with `accept: false` does not affect other invitations
- [ ] `PATCH` returns 403 when the authenticated student is not the invitation's invitee
- [ ] `PATCH` returns 400 when the invitation status is not `PENDING`

---

### API-04 — JIRA Binding Endpoint
**Story Points: 5** | **Sub-process: 2.4** | **Depends on: B-02, B-06, B-10, B-12, B-17, B-18**

Single endpoint, but the most infrastructure-heavy of the non-GitHub issues. Requires `EncryptionService` (AES-256-GCM) and `JiraValidationService` (live HTTP) to both be complete before this can be fully tested end-to-end.

**Endpoints:**

| Method | Path | SP | Difficulty |
|--------|------|----|------------|
| POST | `/api/groups/{groupId}/jira` | 5 | Hard |

**What makes it Hard:**
- Live outbound HTTP call to JIRA REST API (5-second timeout, multiple failure modes each mapping to a specific 422 message)
- `jiraApiToken` must be encrypted before persistence; plaintext must never reach the DB
- `TOOLS_BOUND` status transition logic: only fires if `encryptedGithubPat` is already set
- Validation failure must not persist any data (validate before encrypt before save — order matters)

**Acceptance criteria:**
- [ ] A non-TEAM_LEADER requester gets 403 before any external HTTP call is made
- [ ] An invalid JIRA token returns 422 with the `"API token is invalid or expired"` message
- [ ] A bad project key returns 422 with the `"Project key '{key}' not found"` message
- [ ] An unreachable URL returns 422 with the `"JIRA space URL is unreachable"` message
- [ ] On success, `encryptedJiraToken` in DB is not the plaintext token (verified by direct DB query)
- [ ] `jiraSpaceUrl` and `jiraProjectKey` are stored as plaintext
- [ ] If GitHub was already bound, response contains `status: "TOOLS_BOUND"`
- [ ] If GitHub was not bound, response contains `status: "TOOLS_PENDING"`
- [ ] Calling the endpoint a second time overwrites the previous credentials

---

### API-05 — GitHub Binding Endpoint
**Story Points: 5** | **Sub-process: 2.5** | **Depends on: B-02, B-06, B-11, B-12, B-17, B-18**

Similar structure to API-04 but with two sequential outbound calls to GitHub. The second call (`/repos`) is specifically for scope verification — GitHub returns 403 (not 401) for scope failures.

**Endpoints:**

| Method | Path | SP | Difficulty |
|--------|------|----|------------|
| POST | `/api/groups/{groupId}/github` | 5 | Hard |

**What makes it Hard:**
- Two sequential GitHub API calls with distinct failure modes each mapped to a different 422 message
- Scope check (`repo` scope) is separate from authentication check — 403 from GitHub must map to a specific user-facing message, not a generic "forbidden"
- `githubPat` must be AES-256-GCM encrypted before persistence
- `TOOLS_BOUND` transition only if `encryptedJiraToken` is already set

**Acceptance criteria:**
- [ ] A non-TEAM_LEADER requester gets 403 before any external HTTP call
- [ ] An invalid PAT returns 422 `"PAT is invalid or expired"` (GitHub returns 401)
- [ ] A PAT without `repo` scope returns 422 `"PAT lacks required 'repo' scope"` (GitHub returns 403 on second call)
- [ ] A non-existent org name returns 422 `"Organization '{name}' not found"` (GitHub returns 404)
- [ ] On success, `encryptedGithubPat` in DB is not the plaintext PAT
- [ ] `githubOrgName` is stored as plaintext
- [ ] If JIRA was already bound, response contains `status: "TOOLS_BOUND"`
- [ ] If JIRA was not bound, response contains `status: "TOOLS_PENDING"`

---

### API-06 — Coordinator Override Endpoints
**Story Points: 9** | **Sub-process: 2.6** | **Depends on: B-02, B-03, B-04, B-05, B-13, B-17, B-18**

All coordinator endpoints share the same role guard (Staff JWT with role=`Coordinator`). The disband endpoint has a cascade side effect (auto-deny all pending invitations) similar to API-03's accept flow.

**Endpoints:**

| Method | Path | SP | Difficulty |
|--------|------|----|------------|
| GET | `/api/coordinator/groups` | 2 | Easy |
| GET | `/api/coordinator/groups/{groupId}` | 1 | Easy |
| PATCH | `/api/coordinator/groups/{groupId}/members` | 3 | Medium |
| PATCH | `/api/coordinator/groups/{groupId}/disband` | 3 | Medium |

**What makes PATCH endpoints Medium:**
- `members`: must handle both `ADD` and `REMOVE` from a single `action` field; TEAM_LEADER removal is explicitly blocked
- `disband`: cascade auto-deny of all `PENDING` invitations for members must be atomic with the status update

**Acceptance criteria:**
- [ ] All four endpoints return 403 for a Staff JWT with role `Professor` or `Admin`
- [ ] `GET /coordinator/groups` resolves termId server-side via `TermConfigService.getActiveTermId()` — no query param accepted (ADR 2026-04-02)
- [ ] `PATCH /members` with `action: "REMOVE"` on a TEAM_LEADER returns 400 with the exact message `"Cannot remove Team Leader; transfer leadership first"`
- [ ] `PATCH /members` with `action: "ADD"` for a student already in any group returns 400
- [ ] `PATCH /disband` sets group status to `DISBANDED` and all `PENDING` invitations for group members to `AUTO_DENIED`
- [ ] `PATCH /disband` on an already-disbanded group returns 400
- [ ] Encrypted token fields (`encryptedJiraToken`, `encryptedGithubPat`) are never present in any coordinator response body

---

## GroupStatus State Machine

```
FORMING ──(first tool bound)──► TOOLS_PENDING
FORMING ──(both tools bound simultaneously)──► TOOLS_BOUND
TOOLS_PENDING ──(second tool bound)──► TOOLS_BOUND
Any state ──(coordinator disbands)──► DISBANDED
```

## Security Notes

| Credential | Storage |
|------------|---------|
| `jiraApiToken` | AES-256-GCM → `encryptedJiraToken` column (length 1024) |
| `githubPat` | AES-256-GCM → `encryptedGithubPat` column (length 1024) |
| `jiraSpaceUrl` | Plaintext (required for P5 sprint queries) |
| `jiraProjectKey` | Plaintext (required for P5 sprint queries) |
| `githubOrgName` | Plaintext (required for P5 sprint queries) |
| Any token | **Never returned** in any API response |
