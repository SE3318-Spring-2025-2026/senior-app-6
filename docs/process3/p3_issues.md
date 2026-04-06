# P3 Issues — Sprint 2


---

## [Backend] P3 Entities & Repository Additions
**Estimate:** 1 Point

**Problem Summary:** Extend the data model to support Process 3. Adds `AdvisorRequest` entity and small additions to existing `ProjectGroup` and `StaffUser` entities.
**Scope:** Backend — Data Layer.

**Deliverables:**
1. Create `AdvisorRequest` JPA entity: `id` (UUID), `group` (`@ManyToOne ProjectGroup`), `advisor` (`@ManyToOne StaffUser`), `status` (Enum: `PENDING`, `ACCEPTED`, `REJECTED`, `AUTO_REJECTED`, `CANCELLED`), `sentAt`, `respondedAt` (nullable).
2. Add `AdvisorRequestRepository` with required query methods including `@Modifying bulkUpdateStatus` for cascade rejections.
3. Update `ProjectGroup` entity: add nullable `advisorId` (FK to `StaffUser`).
4. Update `StaffUser` entity: add `advisorCapacity` integer column (default 5).

**References:** `endpoints_p3.md`, `er_p2_p3.md`.
**Acceptance Criteria:**
- [ ] `advisorId` on `ProjectGroup` is nullable and persists correctly.
- [ ] `bulkUpdateStatus` properly updates request statuses in bulk (excluding a target ID if specified).
- [ ] Schema changes auto-generate via Hibernate on startup.

**Related Issues:** Required by all other Backend P3 issues: **[Backend] AdvisorService — Browse & Request Flow**, **[Backend] AdvisorService — Review & Respond Flow**, **[Backend] SanitizationService — Auto-Disband Scheduler**, **[Backend] P3 Controllers, DTOs & Coordinator Override**. Note: `@Version Long version` on `ProjectGroup` is already covered by **[Backend] P2 Core Domain Entities & Repositories**.

---

## [Backend] AdvisorService — Browse & Request Flow
**Estimate:** 2 Points

**Problem Summary:** Implement the service methods enabling a Team Leader to browse available advisors and manage their single active advisor request.
**Scope:** Backend — Service Layer.

**Deliverables:**
1. `getAvailableAdvisors()`: Fetch active term via `TermConfigService.getActiveTermId()`, query all Professors, calculate current active groups per professor, return only those below `advisorCapacity`.
2. `sendAdvisorRequest()`: Check active `ADVISOR_ASSOCIATION` window, enforce `TOOLS_BOUND` status, verify no existing `PENDING` request, check advisor capacity, save request.
3. `getAdvisorRequest()` & `cancelAdvisorRequest()`: Fetch or cancel the active pending request for the caller's group.

**References:** Process 3 (DFD 3.1), `3.1_advisor_request_p3.md`, `endpoints_p3.md` (P3-API-01).
**Acceptance Criteria:**
- [ ] `termId` resolved server-side via `TermConfigService` — never from client.
- [ ] `sendAdvisorRequest` returns 400 if group is not `TOOLS_BOUND`.
- [ ] `sendAdvisorRequest` returns 409 if another `PENDING` request already exists for the group.
- [ ] `getAvailableAdvisors` filters out advisors where `currentGroupCount >= advisorCapacity`.

**Related Issues:** Depends on **[Backend] P3 Entities & Repository Additions**.

---

## [Backend] AdvisorService — Review & Respond Flow
**Estimate:** 2 Points

**Problem Summary:** Implement the logic allowing a Professor to view their pending requests and accept or decline them. Accepting requires a transactional capacity guard to prevent race conditions.
**Scope:** Backend — Service Layer.

**Deliverables:**
1. `getPendingRequestsForAdvisor(professorId)` & `getRequestDetail(requestId, professorId)`: Return read-only request data.
2. `respondToRequest(requestId, professorId, accept)`: Process the advisor's decision.
3. **Capacity Guard:** Inside the `@Transactional` block on `ACCEPT`, re-run `ProjectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(advisorId, termId, GroupStatus.DISBANDED)`. If at capacity, throw `AdvisorAtCapacityException` (400) and roll back. **Do NOT auto-reject the request** — it stays `PENDING` so the group can retry later.
4. **Cascade Rejections:** On `ACCEPT`, assign `advisorId` to group, update group status to `ADVISOR_ASSIGNED`, bulk update all other `PENDING` requests for this group to `AUTO_REJECTED`.

**References:** Process 3 (DFD 3.2, 3.3), `3.2_3.3_advisor_respond_p3.md`, `endpoints_p3.md` (P3-API-02, P3-API-03).
**Acceptance Criteria:**
- [ ] `respondToRequest(accept=true)` changes group status to `ADVISOR_ASSIGNED` and links the advisor.
- [ ] Capacity Guard throws `AdvisorAtCapacityException` (400) and rolls back if advisor exceeds capacity inside the transaction.
- [ ] Cascade rejection sets all competing `PENDING` requests for the same group to `AUTO_REJECTED` atomically.

**Related Issues:** Depends on **[Backend] P3 Entities & Repository Additions**, **[Backend] AdvisorService — Browse & Request Flow**.

---

## [Backend] SanitizationService — Auto-Disband Scheduler
**Estimate:** 2 Points

**Problem Summary:** Implement the scheduled job and manual trigger to disband unadvised groups once the association window closes, utilizing optimistic locking to prevent race conditions.
**Scope:** Backend — Service Layer & Scheduler.

**Deliverables:**
1. Create `SanitizationService` with `@Scheduled` method checking for newly closed `ADVISOR_ASSOCIATION` windows.
2. `runSanitization(termId, force)`: Query all groups without an `advisorId` (status `FORMING`, `TOOLS_PENDING`, `TOOLS_BOUND`) and mark them `DISBANDED`.
3. Hard-delete associated `GroupMembership` rows.
4. Atomically bulk update all their `PENDING` advisor requests to `AUTO_REJECTED`.
5. Handle `OptimisticLockException` gracefully in the disband loop — skip group if concurrently modified by an advisor accept, continue loop.

**References:** Process 3 (DFD 3.4), `3.4_sanitization_p3.md`, `endpoints_p3.md` (P3-API-04).
**Acceptance Criteria:**
- [ ] Groups lacking an `advisorId` transition to `DISBANDED` upon window close.
- [ ] `OptimisticLockException` triggered by `@Version` correctly skips the group — does not halt the entire job.
- [ ] Manual trigger `POST /api/coordinator/sanitize` requires `force: true` if the schedule window is still open.

**Related Issues:** Depends on **[Backend] P3 Entities & Repository Additions**.

---

## [Backend] P3 Controllers, DTOs & Coordinator Override
**Estimate:** 2 Points

**Problem Summary:** Map all Process 3 REST endpoints, create Request/Response DTOs, wire to services, and implement the Coordinator advisor override service methods.
**Scope:** Backend — Controller & Service Layer.

**Deliverables:**
1. Create `AdvisorController` for Student (`/api/advisors`, `/api/groups/{id}/advisor-request`) and Professor (`/api/advisor/requests/**`) endpoints.
2. Update `CoordinatorGroupController` with `/api/coordinator/advisors` and `/api/coordinator/groups/{groupId}/advisor`.
3. Add `SanitizationController` for `POST /api/coordinator/sanitize`.
4. Create DTOs: `AdvisorRequestResponse`, `AdvisorCapacityResponse`, `SanitizationReport`.
5. Ensure Professor endpoints use `@PreAuthorize("hasRole('PROFESSOR')")`.
6. Implement coordinator override methods:
   - `getAllAdvisorsWithCapacity()`: bypasses capacity filter, includes `atCapacity` flag.
   - `assignAdvisor(groupId, advisorId)`: Two guards before write — (a) throw 400 if `group.status == DISBANDED`; (b) throw 400 if `group.status` is not `TOOLS_BOUND` and not `ADVISOR_ASSIGNED`. On success, set `group.advisorId`, set `group.status = ADVISOR_ASSIGNED`, bulk `AUTO_REJECTED` all `PENDING` advisor requests for the group. No capacity check. No window check.
   - `removeAdvisor(groupId)`: Clears `group.advisorId`, sets `group.status = TOOLS_BOUND`. Throw 400 if group has no advisor.
7. Amend `GroupService.disbandGroup()` (implemented in **[Backend] Coordinator Override Services & Controller**) to also call `AdvisorRequestRepository.bulkUpdateStatusByGroupId(groupId, PENDING → AUTO_REJECTED)` in the same `@Transactional` block. This is a P3 cleanup step that must be added to the existing P2 disband path once `AdvisorRequestRepository` exists.

**References:** `endpoints_p3.md`, Process 3 (DFD 3.5), `3.5_coordinator_advisor_p3.md`.
**Acceptance Criteria:**
- [ ] All request payload validations return 400 correctly.
- [ ] Role-based access control restricts Student, Professor, and Coordinator endpoints.
- [ ] Force assignment returns 400 if group is `DISBANDED`.
- [ ] Force assignment returns 400 if group status is not `TOOLS_BOUND` or `ADVISOR_ASSIGNED`.
- [ ] Force assignment does not fail if advisor is at or above capacity.
- [ ] Force assignment atomically triggers cascade `AUTO_REJECTED` for all `PENDING` requests of the group.
- [ ] Removing an advisor reverts group status to `TOOLS_BOUND`.
- [ ] Coordinator disband endpoint now also auto-rejects all `PENDING` `AdvisorRequest` rows for the group.
- [ ] Controllers never accept `termId` as input.

**Related Issues:** Depends on **[Backend] AdvisorService — Browse & Request Flow**, **[Backend] AdvisorService — Review & Respond Flow**, **[Backend] SanitizationService — Auto-Disband Scheduler**.

---

## [Frontend] Student Advisor Discovery & Request UI

**Estimate:** 1 Point

**Problem Summary:** Provide a UI for the Team Leader to browse available advisors, send a request, view the pending request, and cancel it.
**Scope:** Frontend — Page / Components. **Framework:** Vue.

**Deliverables:**
1. Advisor Discovery view: fetch `GET /api/advisors`, display cards/list.
2. Request CTA: POST to `/api/groups/{groupId}/advisor-request`. Handle 400/409 errors gracefully.
3. Active Request component: show current `PENDING` request on the Group Hub with a "Cancel Request" button.

**References:** `endpoints_p3.md` (P3-API-01), Process 3 (DFD 3.1).
**Acceptance Criteria:**
- [ ] Discovery view handles empty state if no advisors have capacity.
- [ ] Team Leader sees action buttons; normal Members see read-only request status.
- [ ] Canceling an active request optimistically updates the UI to allow sending a new request.

**Related Issues:** Depends on **[Backend] P3 Controllers, DTOs & Coordinator Override**.

---

## [Frontend] Professor Requests Inbox & Detail View

**Estimate:** 2 Points

**Problem Summary:** Create the dashboard where Professors review incoming advisee requests and accept or decline them.
**Scope:** Frontend — Page / Components. **Framework:** Vue.

**Deliverables:**
1. Professor Inbox page (`/advisor/requests`): fetch `GET /api/advisor/requests`.
2. Request Detail Modal/Page: show group members and details via `GET /api/advisor/requests/{requestId}`.
3. Accept / Decline action buttons. Must handle 400 capacity guard errors with a clear user-facing message.

**References:** `endpoints_p3.md` (P3-API-02, P3-API-03), Process 3 (DFD 3.2, 3.3).
**Acceptance Criteria:**
- [ ] Inbox handles empty state (`[]` response) gracefully.
- [ ] Accepting a request visually removes it from the pending list and shows success.
- [ ] A 400 from the Capacity Guard shows a readable error: "You have reached maximum capacity".

**Related Issues:** Depends on **[Backend] P3 Controllers, DTOs & Coordinator Override**.

---

## [Frontend] Coordinator Advisor Override & Sanitization Trigger

**Estimate:** 1 Point

**Problem Summary:** Extend the existing Coordinator Group Detail page with advisor management, and add a manual sanitization trigger to the Coordinator dashboard.
**Scope:** Frontend — Components. **Framework:** Vue.

**Deliverables:**
1. Update `/coordinator/groups/:groupId` to include an "Advisor Management" panel with a dropdown fetching `GET /api/coordinator/advisors`. Highlight advisors `atCapacity` but keep them selectable.
2. Submit assignment (`PATCH .../advisor {action: "ASSIGN"}`). Add "Remove Advisor" button (`PATCH .../advisor {action: "REMOVE"}`), requires confirmation click.
3. Add "Run Sanitization" button in Coordinator dashboard. Send `POST /api/coordinator/sanitize` (with `{ force: true }` if window is active). Display returned `SanitizationReport` in a modal.

**References:** `endpoints_p3.md` (P3-API-04, P3-API-05), Process 3 (DFD 3.5).
**Acceptance Criteria:**
- [ ] Advisors at capacity are marked but not disabled (Coordinator override is absolute).
- [ ] UI reflects `ADVISOR_ASSIGNED` or `TOOLS_BOUND` status update without full page reload.
- [ ] Sanitization button while window is active prompts a severe warning before sending.
- [ ] Results (disbanded count, rejected request count) are displayed clearly.

**Related Issues:** Depends on **[Backend] P3 Controllers, DTOs & Coordinator Override**.

---

## [QA] Advisor Lifecycle, Override & Security E2E
**Estimate:** 1 Point

**Problem Summary:** Verify the advisor request lifecycle (send, cancel, reject), coordinator override flow, and strict RBAC enforcement across all P3 endpoints.
**Scope:** QA — Integration Tests.

**Deliverables:**
1. Test happy path: send request → 201 Created, cancel → 200.
2. Test business rule violations: group not `TOOLS_BOUND` → 400, double request → 409, advisor at capacity → 400.
3. Test coordinator force-assign at max capacity → 200 OK, cascade `AUTO_REJECTED` on pending requests verified in DB.
4. Test remove advisor → 200, group status reverts to `TOOLS_BOUND`.
5. Security: Student JWT on Professor endpoints → 403. Professor JWT on Coordinator endpoints → 403.

**References:** `endpoints_p3.md` (P3-API-01, P3-API-05).
**Acceptance Criteria:**
- [ ] DB state correctly reflects `PENDING` or `CANCELLED` statuses after lifecycle tests.
- [ ] Non-Team Leader token on send request rejected with 403.
- [ ] Coordinator overrides bypass all capacity and window validations.
- [ ] All P3 endpoints enforce correct role mapping.

**Related Issues:** Depends on **[Backend] AdvisorService — Browse & Request Flow**, **[Backend] P3 Controllers, DTOs & Coordinator Override**.

---

## [QA] Capacity Guard, Sanitization & Race Conditions E2E
**Estimate:** 1 Point

**Problem Summary:** Verify the transactional capacity guard, auto-sanitization scheduler, and optimistic locking behavior under concurrent load.
**Scope:** QA — Integration Tests / Concurrency.

**Deliverables:**
1. Capacity guard: force advisor capacity max-out during accept → verify `AdvisorAtCapacityException` rollback.
2. Cascade rejection: accept a request → other `PENDING` requests for the same group become `AUTO_REJECTED`.
3. Sanitization: trigger job → verify all unadvised groups (`FORMING`, `TOOLS_PENDING`, `TOOLS_BOUND`) are `DISBANDED`, `GroupMembership` rows hard-deleted.
4. Race condition: simulate Professor "Accept" simultaneously with Sanitization job → `@Version` throws `OptimisticLockException`, group is preserved, job skips it.
5. State machine: `TOOLS_BOUND` → `ADVISOR_ASSIGNED` → `TOOLS_BOUND` (remove) → `DISBANDED` (sanitize).

**References:** `endpoints_p3.md` (P3-API-03, P3-API-04), `3.2_3.3_advisor_respond_p3.md`, `3.4_sanitization_p3.md`.
**Acceptance Criteria:**
- [ ] Capacity constraints cannot be bypassed by concurrent Professor accepts.
- [ ] `SanitizationReport` returns accurate disbanded and rejected counts.
- [ ] Race condition simulation safely protects newly-advised groups from being disbanded.
- [ ] State transitions match the P3 state machine exactly.

**Related Issues:** Depends on **[Backend] AdvisorService — Review & Respond Flow**, **[Backend] SanitizationService — Auto-Disband Scheduler**, **[Backend] P3 Controllers, DTOs & Coordinator Override**.

---

## Summary Table

| Description | SP |
|---|:---:|
| [Backend] P3 Entities & Repository Additions | 1 |
| [Backend] AdvisorService — Browse & Request Flow | 2 |
| [Backend] AdvisorService — Review & Respond (Capacity Guard) | 2 |
| [Backend] SanitizationService — Auto-Disband Scheduler | 2 |
| [Backend] P3 Controllers, DTOs & Coordinator Override | 2 |
| [Frontend] Student Advisor Discovery & Request UI | 1 |
| [Frontend] Professor Requests Inbox & Detail View | 2 |
| [Frontend] Coordinator Override UI & Sanitization Trigger | 1 |
| [QA] Advisor Lifecycle, Override & Security E2E | 1 |
| [QA] Capacity Guard, Sanitization & Race Conditions E2E | 1 |
| **Total** | **15** |
