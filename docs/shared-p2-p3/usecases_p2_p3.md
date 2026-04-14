# Use Cases — Process 2 & Process 3

---

## Process 2 — Group Creation & Tool Integration

---

### 2.1 — Create a Group

**Actor:** Student (becomes Team Leader)

> A student opens the app during the group creation window and creates a new group for their term.

**Happy path:**
1. Student submits a group name. `termId` is resolved server-side — not sent by the client.
2. System checks the group creation window is open.
3. System checks the student is not already in a group.
4. Group is created with status `FORMING`. Student is automatically set as `TEAM_LEADER`.

**Fails if:**
- The group creation window is closed while process. 
- The student is already in a group.
- The group name already exists for that term.

---

### 2.2 — Invite a Member

**Actor:** Team Leader

> The Team Leader searches for classmates by student ID and sends them an invitation.

**Happy path:**
1. Team Leader searches for a student by partial student ID (min 3 chars).
2. System returns students who are not yet in any group.
3. Team Leader sends an invitation to a chosen student.
4. Invitation is created with status `PENDING`.
5. Team Leader can cancel a pending invitation at any time.

**Fails if:**
- Caller is not the Team Leader.
- Target student is already in a group.
- A pending invitation to the same student already exists.

---

### 2.3 — Respond to an Invitation

**Actor:** Invited Student

> The invited student sees their pending invitations and accepts or declines.

**Happy path (Accept):**
1. Student views their pending invitations.
2. Student accepts one invitation.
3. Student is added to the group as `MEMBER`.
4. All other pending invitations the student received are automatically denied.

**Happy path (Decline):**
1. Student declines the invitation.
2. Only that invitation is updated to `DECLINED`. Nothing else changes.

**Fails if:**
- The invitation does not belong to the authenticated student.
- The invitation is no longer pending (already accepted, declined, or cancelled).
- Group status is `TOOLS_BOUND` or higher — student accept is blocked (roster lock). Coordinator force-add bypasses this.

---

### 2.4 — Bind JIRA

**Actor:** Team Leader

> The Team Leader connects the group's JIRA workspace so sprint data can be pulled later.

**Happy path:**
1. Team Leader submits JIRA space URL, project key, and API token.
2. System makes a live test call to JIRA to validate all three.
3. Token is encrypted (AES-256) before saving. URL and key saved as plaintext.
4. If GitHub is already bound → group status becomes `TOOLS_BOUND`.
5. If GitHub is not yet bound → group status becomes `TOOLS_PENDING`.

**Fails if:**
- JIRA URL is unreachable.
- Project key is not found in that JIRA space.
- API token is invalid or expired.

---

### 2.5 — Bind GitHub

**Actor:** Team Leader

> The Team Leader connects the group's GitHub organization so PR and code data can be pulled later.

**Happy path:**
1. Team Leader submits GitHub organization name and Personal Access Token (PAT).
2. System calls GitHub to verify the org exists and the PAT is valid.
3. System checks the PAT has `repo` scope (second call).
4. PAT is encrypted before saving. Org name saved as plaintext.
5. If JIRA is already bound → group status becomes `TOOLS_BOUND`.
6. If JIRA is not yet bound → group status becomes `TOOLS_PENDING`.

**Fails if:**
- PAT is invalid or expired.
- PAT does not have `repo` scope.
- Organization name does not exist on GitHub.

---

### 2.6 — Coordinator Group Override

**Actor:** Coordinator

> The Coordinator can inspect all groups for a term and intervene when needed.

**Sub-cases:**

| Action | Description |
|---|---|
| List groups | View all groups for a term with status and tool binding summary. |
| View group detail | See full member list and tool binding state for one group. |
| Add / remove member | Bypass the invitation flow — add or remove a student directly. Removing the Team Leader is blocked. Force-add is rejected if (current members + pending outbound invitations) >= max team size. Force-add also bulk auto-denies all other PENDING invitations for the added student (same transaction). |
| Disband group | Set group status to `DISBANDED`. All pending invitations for members are auto-denied. All pending advisor requests for the group are auto-rejected. All GroupMembership rows are hard-deleted. |

---

## Process 3 — Advisor Association & Sanitization

> Only groups with status `TOOLS_BOUND` can request an advisor. The `ADVISOR_ASSOCIATION` schedule window must be open.

---

### 3.1 — Browse Advisors & Send Request

**Actor:** Team Leader

> The Team Leader browses available professors and sends one advisor request at a time.

**Happy path:**
1. Team Leader views the list of professors who are below their group capacity for the term.
2. Team Leader sends a request to one advisor.
3. `AdvisorRequest` is created with status `PENDING`.
4. Team Leader can cancel the pending request and send a new one if needed.

**Fails if:**
- Advisor association window is not open.
- Group status is not `TOOLS_BOUND`.
- Group already has an active `PENDING` request (one at a time).
- Target advisor has reached their maximum capacity.

---

### 3.2 — Advisor Reviews Requests

**Actor:** Advisor (Professor)

> The advisor sees all pending requests addressed to them and can review each group before deciding.

**Happy path:**
1. Advisor views their list of pending requests (group name, member count, term).
2. Advisor opens a specific request to see full group detail and member list.
3. No state changes — this step is read-only.

---

### 3.3 — Advisor Responds to a Request

**Actor:** Advisor (Professor)

> The advisor accepts or rejects a group's request.

**Happy path (Accept):**
1. Advisor accepts the request.
2. `group.advisorId` is set. Group status → `ADVISOR_ASSIGNED`.
3. Any other pending requests from the same group to other advisors are auto-rejected.
4. All changes happen in one atomic transaction.

**Happy path (Reject):**
1. Advisor rejects the request.
2. Only that request is marked `REJECTED`. Group status stays `TOOLS_BOUND`.
3. Team Leader is free to send a new request to any advisor.

---

### 3.4 — Auto-Sanitization (Disband Unadvised Groups)

**Actor:** System Scheduler / Coordinator (manual trigger)

> When the advisor association window closes, any group that still has no advisor is automatically disbanded.

**Happy path:**
1. Scheduled job fires when the `ADVISOR_ASSOCIATION` window `closesAt` passes.
2. All groups without an assigned advisor (`FORMING`, `TOOLS_PENDING`, `TOOLS_BOUND`) are disbanded.
3. All pending advisor requests for those groups are auto-rejected.
4. A sanitization report (count of disbanded groups, count of rejected requests) is returned.

**Manual trigger:**
- Coordinator can fire this early via `POST /api/coordinator/sanitize`.
- Requires `force: true` if the window is still active.

---

### 3.5 — Coordinator Advisor Override

**Actor:** Coordinator

> The Coordinator can assign or remove an advisor from any group, bypassing the normal request flow.

**Sub-cases:**

| Action | Description |
|---|---|
| List advisors | See all professors with current group count and capacity for the term (including those at capacity). |
| Assign advisor | Directly sets `group.advisorId` → status `ADVISOR_ASSIGNED`. No window check, no capacity limit. Auto-rejects any pending requests for the group. |
| Remove advisor | Clears `group.advisorId` → status reverts to `TOOLS_BOUND`. Group can re-enter the request flow. |

---

## Group Status Flow (P2 → P3)

```
FORMING
  │ (first tool bound)
  ▼
TOOLS_PENDING
  │ (second tool bound)
  ▼
TOOLS_BOUND
  │ (advisor accepts OR coordinator assigns)
  ▼
ADVISOR_ASSIGNED
  │ (coordinator removes advisor)
  └──► TOOLS_BOUND

FORMING / TOOLS_PENDING / TOOLS_BOUND ──(sanitization — no advisor at window close)──► DISBANDED
Any state ──(coordinator disbands)──► DISBANDED
Note: ADVISOR_ASSIGNED groups are NOT affected by sanitization — only coordinator disband can reach DISBANDED from that state.
```
