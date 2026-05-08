# Audit Log QA Spec — Part 1

## TC-AUDIT-01 through TC-AUDIT-15

All tests use `@SpringBootTest` + `@AutoConfigureMockMvc` + H2 in-memory DB.
`JiraValidationService`, `GitHubValidationService`, and `GithubService` are mocked with `@MockBean`.
`AuditLogRepository` is cleared in `@BeforeEach`; each test asserts an exact audit row count.

---

### TC-AUDIT-01 — STAFF_LOGIN SUCCESS

| Field | Expected |
|---|---|
| action | `STAFF_LOGIN` |
| outcome | `SUCCESS` |
| userType | `STAFF` |
| userId | staff member's UUID |
| occurredAt | not null |

**Steps:** POST `/api/auth/login` with valid credentials → expect HTTP 200.  
**Assert:** 1 audit row in DB with fields above.

---

### TC-AUDIT-02 — STAFF_LOGIN FAILURE

| Field | Expected |
|---|---|
| action | `STAFF_LOGIN` |
| outcome | `FAILURE` |
| userType | `STAFF` |
| userId | `null` |

**Steps:** POST `/api/auth/login` with wrong password → expect HTTP 401.  
**Assert:** 1 audit row committed despite failed login (REQUIRES_NEW — no outer tx but independent tx created).

---

### TC-AUDIT-03 — STUDENT_LOGIN SUCCESS

| Field | Expected |
|---|---|
| action | `STUDENT_LOGIN` |
| outcome | `SUCCESS` |
| userType | `STUDENT` |
| userId | student's UUID |

**Steps:** POST `/api/auth/github` with mocked GitHub OAuth exchange → expect HTTP 200.  
**Note:** `GithubService` mocked to avoid real OAuth calls.

---

### TC-AUDIT-04 — PASSWORD_RESET SUCCESS

| Field | Expected |
|---|---|
| action | `PASSWORD_RESET` |
| outcome | `SUCCESS` |
| userType | `STAFF` |
| userId | staff member's UUID |

**Steps:** Seed valid `PasswordResetToken`; POST `/api/auth/reset-password` → expect HTTP 200.

---

### TC-AUDIT-05 — GROUP_CREATED SUCCESS

| Field | Expected |
|---|---|
| action | `GROUP_CREATED` |
| outcome | `SUCCESS` |
| userType | `STUDENT` |
| userId | creating student's UUID |

**Steps:** Seed open `GROUP_CREATION` schedule window; POST `/api/groups` with student JWT → expect HTTP 201.

---

### TC-AUDIT-06 — INVITATION_SENT SUCCESS

| Field | Expected |
|---|---|
| action | `INVITATION_SENT` |
| outcome | `SUCCESS` |
| userType | `STUDENT` |
| userId | team leader's UUID |

**Steps:** POST `/api/groups/{id}/invitations` with team leader JWT → expect HTTP 201.

---

### TC-AUDIT-07 — JIRA_BOUND FAILURE (REQUIRES_NEW)

| Field | Expected |
|---|---|
| action | `JIRA_BOUND` |
| outcome | `FAILURE` |
| userId | team leader's UUID |

**Steps:** Mock `JiraValidationService.validate()` to throw `JiraValidationException`;
POST `/api/groups/{id}/jira` → expect HTTP 422.  
**Assert:** FAILURE audit row committed even though outer `@Transactional` rolled back.

---

### TC-AUDIT-08 — GITHUB_BOUND FAILURE (REQUIRES_NEW)

| Field | Expected |
|---|---|
| action | `GITHUB_BOUND` |
| outcome | `FAILURE` |
| userId | team leader's UUID |

**Steps:** Mock `GitHubValidationService.validate()` to throw `GitHubValidationException`;
POST `/api/groups/{id}/github` → expect HTTP 422.  
**Assert:** FAILURE audit row committed even though outer `@Transactional` rolled back.

---

### TC-AUDIT-09 — INVITATION_RESPONDED (ACCEPT)

| Field | Expected |
|---|---|
| action | `INVITATION_RESPONDED` |
| outcome | `SUCCESS` |
| userType | `STUDENT` |
| userId | invitee's UUID |

**Steps:** Seed PENDING invitation; PATCH `/api/invitations/{id}/respond` with `accept=true` → expect HTTP 200.

---

### TC-AUDIT-10 — INVITATION_RESPONDED (DECLINE)

| Field | Expected |
|---|---|
| action | `INVITATION_RESPONDED` |
| outcome | `SUCCESS` |
| userType | `STUDENT` |
| userId | invitee's UUID |

**Steps:** Seed PENDING invitation; PATCH `/api/invitations/{id}/respond` with `accept=false` → expect HTTP 200.

---

### TC-AUDIT-11 — MEMBER_ADDED coordinator userId (FIX-2)

| Field | Expected |
|---|---|
| action | `MEMBER_ADDED` |
| outcome | `SUCCESS` |
| userType | `STAFF` |
| userId | coordinator's UUID (**NOT null**) |

**Steps:** PATCH `/api/coordinator/groups/{id}/members` (ADD) with coordinator JWT.  
**Note:** This TC catches a regression of FIX-2 from PR #297. A null userId here means the SecurityContext fix was reverted.

---

### TC-AUDIT-12 — MEMBER_REMOVED coordinator userId (FIX-2)

| Field | Expected |
|---|---|
| action | `MEMBER_REMOVED` |
| outcome | `SUCCESS` |
| userType | `STAFF` |
| userId | coordinator's UUID (**NOT null**) |

**Steps:** PATCH `/api/coordinator/groups/{id}/members` (REMOVE) with coordinator JWT.

---

### TC-AUDIT-13 — GROUP_DISBANDED coordinator userId (FIX-2)

| Field | Expected |
|---|---|
| action | `GROUP_DISBANDED` |
| outcome | `SUCCESS` |
| userType | `STAFF` |
| userId | coordinator's UUID (**NOT null**) |

**Steps:** PATCH `/api/coordinator/groups/{id}/disband` with coordinator JWT.

---

### TC-AUDIT-14 — ADVISOR_ASSIGNED coordinator userId (FIX-2)

| Field | Expected |
|---|---|
| action | `ADVISOR_ASSIGNED` |
| outcome | `SUCCESS` |
| userType | `STAFF` |
| userId | coordinator's UUID (**NOT null**) |

**Steps:** PATCH `/api/coordinator/groups/{id}/advisor` (ASSIGN) with coordinator JWT;
group must be in `TOOLS_BOUND` status.

---

### TC-AUDIT-15 — REQUIRES_NEW contract end-to-end via API

**Dual assertion — both must hold:**

1. **Outer tx rolled back:** group's `encryptedJiraToken` is still `null` after the failed bind.
2. **Inner REQUIRES_NEW committed:** `JIRA_BOUND FAILURE` audit row exists in DB.

**Steps:** Same setup as TC-AUDIT-07 (mock `JiraValidationService` to throw).
POST `/api/groups/{id}/jira` → expect HTTP 422.  
**Assert both conditions above explicitly** — this confirms the full REQUIRES_NEW contract at the API layer.

---

## Out of scope for Part 1

- `ADVISOR_REQUEST_SENT`, `ADVISOR_REQUEST_CANCELLED`, `ADVISOR_REQUEST_RESPONDED` — deferred to Part 2
- `ADVISOR_REMOVED` — covered implicitly by TC-AUDIT-14 pattern (same `currentUserId()` path)
- `SCRUM_GRADE_SUBMITTED` — deferred to Part 2
