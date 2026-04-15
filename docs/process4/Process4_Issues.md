# Process 4: Committee Assignment Issues

## [Backend] Implement POST /api/committees endpoint
**Problem Summary:** Allow Coordinators to create new committee entities bounded to a specific term.

**Scope:** Backend API

**Estimate:** 2 Points

**Deliverables:**
1. Database schema and migration script for the `Committee` table (`committeeName`, `termId`).
2. Protected backend REST endpoint (`POST /api/committees`).

**References:** `README.md` (Process 4, Step 1), NFR-4 (Role-Based Access)

**Acceptance Criteria:**
- [ ] Endpoint rejects non-COORDINATOR users with a 403 Forbidden error.
- [ ] Successfully creates a new committee by providing a unique `committeeName` and valid `termId`.
- [ ] Returns a 201 Created status with the newly created committee ID.

**Related Issues:** None

---

## [Backend] Implement POST /api/committees/{id}/professors endpoint

**Problem Summary:** Handle the assignment of professors to a committee with designated ADVISOR or JURY roles.

**Scope:** Backend API

**Estimate:** 3 Points

**Deliverables:**
1. Many-to-Many DB mapping table for `Committee_Professors` with a `role` column (Enum: `ADVISOR`, `JURY`).
2. API endpoint `POST /api/committees/{id}/professors` that accepts an array of professor objects with roles.

**References:** `README.md` (Process 4, Steps 2 & 3)

**Acceptance Criteria:**
- [ ] System enforces that a committee has exactly one primary `ADVISOR` assigned.
- [ ] System allows 0 or multiple `JURY` members to be assigned to the same committee.
- [ ] Fails with 400 Bad Request if professor IDs are invalid or non-existent.

**Related Issues:** [Backend] Implement POST /api/committees endpoint

---

## [Backend] Implement POST /api/committees/{id}/groups endpoint

**Problem Summary:** Allow Coordinators to bind multiple active student groups to a specified committee.

**Scope:** Backend API

**Estimate:** 3 Points

**Deliverables:**
1. API endpoint (`POST /api/committees/{id}/groups`) to accept an array of `groupIds`.
2. Database updates to assign the `committeeId` to the specific Group entities.

**References:** `README.md` (Process 4, Step 4)

**Acceptance Criteria:**
- [ ] Endpoint accepts a batch assignment of multiple student groups to one committee.
- [ ] Fails with 400 Bad Request if a group has not completed Advisor Association (Process 3).
- [ ] The assignment successfully flags the group as eligible for Proposal submission.

**Related Issues:** [Backend] Implement POST /api/committees endpoint, [Backend] Implement Committee Validation Service for Assignments

---

## [Backend] Implement Committee Validation Service for Assignments

**Problem Summary:** Establish a service layer to validate professor scheduling conflicts and group mapping uniqueness.

**Scope:** Backend API

**Estimate:** 2 Points

**Deliverables:**
1. Service method to check if a professor is already overbooked for a deliverable cycle.
2. Service method to ensure a group is not mapped to multiple committees for the same deliverable.

**References:** `README.md` (Process 4, Step 5)

**Acceptance Criteria:**
- [ ] Service returns an error if a professor has a scheduling conflict for the same deliverable cycle.
- [ ] Service returns an error if a group is already assigned to another committee.

**Related Issues:** [Backend] Implement POST /api/committees/{id}/professors endpoint, [Backend] Implement POST /api/committees/{id}/groups endpoint

---

## [Backend] Implement Automated Committee Assignment Notifications

**Problem Summary:** Dispatch automated notifications to professors when they are assigned to a new committee.

**Scope:** Backend API

**Estimate:** 2 Points

**Deliverables:**
1. Backend event publisher that triggers upon successful committee group/professor bindings.
2. Notification service payload builder extracting `committeeId`, `professorId[]`, and `assignedGroups[]`.

**References:** `README.md` (Process 4, Step 6), System Notifications (FR-8)

**Acceptance Criteria:**
- [ ] Automated notification is triggered exactly once when a Coordinator finalizes a committee assignment.
- [ ] Payload correctly details whether the professor is an Advisor or Jury member.
- [ ] Payload includes a list of the student groups they are now responsible for evaluating.

**Related Issues:** [Backend] Implement POST /api/committees/{id}/professors endpoint

---

## [Frontend] Create Committee Management UI

**Problem Summary:** Build the interface for Coordinators to view and create new committees for a specific term.

**Scope:** Frontend

**Estimate:** 3 Points

**Deliverables:**
1. A "Committees" tab in the Coordinator Dashboard.
2. A form/modal to input a `committeeName` and submit it to the `POST /api/committees` endpoint.
3. A data table displaying all created committees.

**References:** `README.md` (Process 4, Step 1)

**Acceptance Criteria:**
- [ ] The UI allows the Coordinator to type a name and create a committee.
- [ ] The newly created committee immediately appears in the list without requiring a full page reload.
- [ ] Displays a clear error toast if the creation fails (e.g., duplicate name).

**Related Issues:** [Backend] Implement POST /api/committees endpoint, [Backend] Implement GET /api/committees endpoint

---

## [Frontend] Implement Professor Assignment UI

**Problem Summary:** Provide a UI for Coordinators to assign 1 Advisor and multiple Jury members to a selected committee.

**Scope:** Frontend

**Estimate:** 3 Points

**Deliverables:**
1. A detail view/modal for a selected committee.
2. A dropdown/picker to select one primary `ADVISOR`.
3. A multi-select dropdown/picker to assign `JURY` members.
4. API integration with `POST /api/committees/{id}/professors`.

**References:** `README.md` (Process 4, Steps 2 & 3)

**Acceptance Criteria:**
- [ ] UI strictly enforces the selection of exactly one primary Advisor before allowing submission.
- [ ] UI allows 0 to N Jury members to be selected.
- [ ] Displays a user-friendly error message if the backend rejects the assignment due to scheduling conflicts.

**Related Issues:** [Backend] Implement POST /api/committees/{id}/professors endpoint, [Backend] Implement GET /api/committees/{id} endpoint

---

## [Frontend] Implement Group Binding Multi-select UI

**Problem Summary:** Build a batch-selection interface allowing Coordinators to bind active student groups to a committee.

**Scope:** Frontend

**Estimate:** 3 Points

**Deliverables:**
1. A multi-select checklist or dual-listbox UI component displaying unassigned, advised groups.
2. Integration with the `POST /api/committees/{id}/groups` endpoint to save bindings.
3. A visual indicator on the committee detail page showing currently assigned groups.

**References:** `README.md` (Process 4, Step 4)

**Acceptance Criteria:**
- [ ] Coordinator can select multiple student groups and assign them simultaneously.
- [ ] The list of selectable groups only includes those that have completed Advisor Association (Process 3).
- [ ] Successfully assigned groups update the local UI state and appear under the committee's group list.

**Related Issues:** [Backend] Implement POST /api/committees/{id}/groups endpoint, [Backend] Implement GET /api/committees/{id} endpoint

---

## [Frontend] Develop Committee Member Dashboard

**Problem Summary:** Create a dedicated dashboard for Professors to see the committees they belong to, along with the assigned student groups and schedules.

**Scope:** Frontend

**Estimate:** 3 Points

**Deliverables:**
1. A "My Committees" page for authenticated users with the `PROFESSOR` role.
2. UI cards/tables displaying the groups assigned to each of their committees.
3. A schedule view displaying upcoming deliverable deadlines configured in Process 0.

**References:** `README.md` (Process 4, Step 7)

**Acceptance Criteria:**
- [ ] Professor can successfully view all student groups assigned to their committee.
- [ ] Professor can see upcoming evaluation deadlines and active rubric constraints.
- [ ] UI securely hides any buttons or actions reserved exclusively for Coordinators.

**Related Issues:** [Backend] Implement Automated Committee Assignment Notifications, [Backend] Implement GET /api/professors/me/committees endpoint

---

## [QA] E2E Testing for Committee Creation & RBAC

**Problem Summary:** Verify that committee creation works flawlessly for Coordinators and blocks unauthorized users.

**Scope:** QA Test

**Estimate:** 2 Points

**Deliverables:**
1. Cypress/Playwright automated tests for the Committee Creation flow.
2. API security tests verifying 403 Forbidden responses for non-coordinator roles.

**References:** `README.md` (Process 4, Step 1), NFR-4 (Role-Based Access)

**Acceptance Criteria:**
- [ ] Automated test successfully logs in as Coordinator, creates a committee, and verifies it in the UI.
- [ ] Automated test verifies that Student and Professor accounts cannot access the creation endpoint or UI.

**Related Issues:** [Frontend] Create Committee Management UI, [Backend] Implement POST /api/committees endpoint

---

## [QA] Integration Testing for Professor Assignment Validation

**Problem Summary:** Ensure the system correctly handles valid and invalid professor-to-committee assignments.

**Scope:** QA Test

**Estimate:** 2 Points

**Deliverables:**
1. Integration tests simulating valid assignments (1 Advisor, multiple Jury).
2. Integration tests triggering the schedule conflict validation service to ensure it blocks overbooking.

**References:** `README.md` (Process 4, Steps 2, 3 & 5)

**Acceptance Criteria:**
- [ ] Test suite confirms an assignment fails if 0 or 2+ primary Advisors are submitted.
- [ ] Test suite confirms that attempting to assign a professor to conflicting deliverable schedules throws a 400 Bad Request.
- [ ] Test suite verifies successful assignments are persisted in the database.

**Related Issues:** [Backend] Implement POST /api/committees/{id}/professors endpoint, [Backend] Implement Committee Validation Service for Assignments

---

## [QA] Integration Testing for Group Assignment Validation

**Problem Summary:** Verify the strict business rules surrounding group assignments to committees.

**Scope:** QA Test

**Estimate:** 2 Points

**Deliverables:**
1. Automated tests confirming groups without an advisor cannot be assigned to a committee.
2. Automated tests confirming a group cannot be double-booked into two committees for the same deliverable.
3. Positive flow test confirming successful batch assignment.

**References:** `README.md` (Process 4, Steps 4 & 5)

**Acceptance Criteria:**
- [ ] System accurately throws an error if the group lacks the `ADVISED` state.
- [ ] System accurately prevents duplicate group-committee mappings for a single term.
- [ ] Positive tests verify that the group state updates correctly to unlock Proposal submission (Process 6).

**Related Issues:** [Backend] Implement POST /api/committees/{id}/groups endpoint, [Frontend] Implement Group Binding Multi-select UI

---

## [QA] Validate Automated Notifications and Dashboard Data Isolation
**Problem Summary:** Ensure notifications trigger upon assignment and that data isolation is strictly enforced on the professor dashboard.

**Scope:** QA Test

**Estimate:** 2 Points

**Deliverables:**
1. Backend unit/integration tests for the Event Publisher ensuring notification dispatch.
2. E2E/API tests validating that Professor A cannot query or view committee groups belonging exclusively to Professor B.

**References:** `README.md` (Process 4, Steps 6 & 7)

**Acceptance Criteria:**
- [ ] Automated test verifies the notification payload includes `committeeId` and `assignedGroups`.
- [ ] Automated test authenticates as Professor A and receives a 403 or empty array when attempting to fetch Professor B's isolated committee data.

**Related Issues:** [Backend] Implement Automated Committee Assignment Notifications, [Frontend] Develop Committee Member Dashboard

---





## [Backend] Implement GET /api/committees endpoint

**Problem Summary:** Allow Coordinators to retrieve a list of all committees for a specific term to populate the management dashboard.

**Scope:** Backend API

**Estimate:** 2 Points

**Deliverables:**
1. API endpoint `GET /api/committees` supporting an optional `termId` query parameter.
2. Backend logic to fetch and map committee records to a summary DTO.

**References:** `README.md` (Process 4, Step 1)

**Acceptance Criteria:**
- [ ] Endpoint successfully returns a 200 OK with a list of committees.
- [ ] Enforces RBAC: Returns 403 Forbidden if accessed by anyone other than a Coordinator.
- [ ] Correctly filters by `termId` if the query parameter is provided.

**Related Issues:** [Backend] Implement POST /api/committees endpoint

---

## [Backend] Implement GET /api/committees/{id} endpoint

**Problem Summary:** Fetch detailed information for a specific committee, including assigned professors and bounded student groups.

**Scope:** Backend API

**Estimate:** 2 Points

**Deliverables:**
1. API endpoint `GET /api/committees/{id}`.
2. Backend logic to construct a detailed DTO aggregating the base committee data, `ADVISOR` and `JURY` members, and assigned student `groups`.

**References:** `README.md` (Process 4, Steps 2, 3 & 4)

**Acceptance Criteria:**
- [ ] Endpoint returns a 200 OK with the fully populated committee DTO.
- [ ] Returns 404 Not Found if the committee ID does not exist.
- [ ] Enforces RBAC: Returns 403 Forbidden if accessed by a non-Coordinator.

**Related Issues:** [Backend] Implement POST /api/committees/{id}/professors endpoint, [Backend] Implement POST /api/committees/{id}/groups endpoint

---

## [Backend] Implement GET /api/professors/me/committees endpoint

**Problem Summary:** Provide an endpoint for professors to fetch their assigned committees, associated student groups, and active rubrics for their dashboard.

**Scope:** Backend API

**Estimate:** 2 Points

**Deliverables:**
1. API endpoint `GET /api/professors/me/committees` that extracts the professor's ID from the JWT token.
2. Backend query logic to return a list of committees where the professor is assigned (either as ADVISOR or JURY), including the student groups mapped to those committees.

**References:** `README.md` (Process 4, Step 7), NFR-4 (Data Isolation)

**Acceptance Criteria:**
- [ ] Endpoint securely uses the JWT to identify the requesting professor.
- [ ] Returns 200 OK with only the data relevant to the authenticated professor.
- [ ] Strict data isolation: Does not return committees or groups the professor is not assigned to evaluate.

**Related Issues:** [Frontend] Develop Committee Member Dashboard

---


**Related Issues:** [Frontend] Develop Committee Member Dashboard

---

## [Backend] Unify the encryption services

**Problem Summary:** Both development teams have implemented separate encryption services. This task is to consolidate them into a single, shared service to eliminate code duplication and enforce a single cryptographic standard across the application.

**Scope:** Backend API (Refactoring/Security)

**Estimate:** 1 Point

**Deliverables:**
1. Review the existing `EncryptionService` from Process 2 and any other implementations.
2. Create a single, canonical `EncryptionService` in a shared utility package.
3. Refactor all code currently using separate encryption utilities (e.g., `GroupService` for tool binding) to depend on the unified service.
4. Delete the redundant/old encryption service classes.

**References:** `docs/process2/p2_issues.md` (#49 — [Backend] Tool Binding & Encryption Orchestration)

**Acceptance Criteria:**
- [ ] Only one `EncryptionService` class exists in the codebase after the merge.
- [ ] All features requiring encryption (e.g., JIRA/GitHub token storage) function correctly with the unified service.
- [ ] The unified service uses AES-256-GCM as the standard.
- [ ] The build is successful and all related tests pass after refactoring.

**Related Issues:** [Backend] Tool Binding & Encryption Orchestration (#49)

Priority: Low

---





## Issue Priorities

| Issue Name | Priority |
| :--- | :--- |
| [Backend] Implement POST /api/committees endpoint | High |
| [Backend] Implement GET /api/committees endpoint | High |
| [Backend] Implement Committee Validation Service for Assignments | High |
| [Backend] Implement POST /api/committees/{id}/professors endpoint | High |
| [Backend] Implement POST /api/committees/{id}/groups endpoint | High |
| [Backend] Implement GET /api/committees/{id} endpoint | High |
| [Frontend] Create Committee Management UI | Medium |
| [Frontend] Implement Professor Assignment UI | Medium |
| [Frontend] Implement Group Binding Multi-select UI | Medium |
| [Backend] Implement Automated Committee Assignment Notifications | Medium |
| [Backend] Implement GET /api/professors/me/committees endpoint | Medium |
| [Frontend] Develop Committee Member Dashboard | Medium |
| [QA] E2E Testing for Committee Creation & RBAC | Low |
| [QA] Integration Testing for Professor Assignment Validation | Low |
| [QA] Integration Testing for Group Assignment Validation | Low |
| [QA] Validate Automated Notifications and Dashboard Data Isolation | Low |