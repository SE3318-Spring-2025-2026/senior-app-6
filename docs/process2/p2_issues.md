# P2 Issues — Sprint 2

---

## #39 — [Backend] P2 Core Domain Entities & Repositories
**Estimate:** 2 Points

**Problem Summary:** Implement the complete JPA Data Layer in a single pass to ensure all foreign keys, unique constraints, and bidirectional relationships are mapped correctly.
**Scope:** Backend API and Database (Data Layer).

**Deliverables:**
1. Implement `ScheduleWindow`, `ProjectGroup`, `GroupMembership`, and `GroupInvitation` JPA entities with exact constraints (e.g., `uq_gm_student`), `@Version Long version` field on `ProjectGroup` for optimistic locking.
2. Create corresponding Spring Data JPA repositories.

**References:** Process 2 (DFD 2.1 - 2.6); Data Models.
**Acceptance Criteria:**
- [ ] Tables are auto-created by Hibernate on startup with all specified columns and constraints.
- [ ] Database-level unique constraints successfully prevent duplicate memberships, even under race conditions.
- [ ] Repositories return correct `Optional` or aggregate counts based on provided query methods.
- [ ] `project_group` table has a `version` column (BIGINT); concurrent updates on the same group throw `OptimisticLockException`.

**Related Issues:** Foundational task. Consolidates the creation of all database tables, fields, and queries into a single setup effort. Required by all backend services.

---

## #40 — [Backend] Base API Layer & Exception Handling
**Estimate:** 1 Point

**Problem Summary:** Establish the foundation for REST controllers, request/response validation, and a standardized global exception handler for the process-2.
**Scope:** Backend API (Controller Layer).

**Deliverables:**
1. Create all Request DTOs with Bean Validation and Response DTOs using Lombok `@Data`.
2. Implement the base `GroupController` for student endpoints.
3. Extend existing `GlobalExceptionHandler` mapping domain exceptions to standard HTTP statuses within a `{ "error": "message" }` JSON envelope.

**References:** Process 2 API Definitions; HTTP Status Code Conventions.
**Acceptance Criteria:**
- [ ] No endpoint returns a raw Java stack trace; all errors match the standard envelope.
- [ ] Invalid DTO payloads immediately return 400 with a readable validation error.
- [ ] Encrypted fields never appear in Response DTOs.

**Related Issues:** Consolidates the setup of student controllers, data transfer objects, and error handling. Depends on **[Backend] Core Domain Entities & Repositories**.

---

## #41 — [Frontend] Shared UI Components

**Estimate:** 1 Point

**Problem Summary:** Create the reusable Vue components required across multiple group and coordinator views.
**Scope:** Frontend (Shared Components). **Framework:** Vue.

**Deliverables:**
1. Implement `GroupStatusBadge.vue` mapping status strings to Tailwind color pills.
2. Implement `MemberList.vue` displaying members, distinguishing the `TEAM_LEADER`, and conditionally rendering an `onRemove` action button.

**References:** Process 2 GroupStatus state machine; GroupDetailResponse schema.
**Acceptance Criteria:**
- [ ] Status badges render correct colors in both light and dark modes.
- [ ] Empty member arrays render a graceful placeholder.
- [ ] TEAM_LEADER rows feature a distinct icon.

**Related Issues:** Foundational frontend task. Consolidates the creation of reusable badges and list items. Used by all Student and Coordinator frontend views.

---

## #42 — [Backend] Validate Schedule & Create Group Service
**Estimate:** 1 Point

**Problem Summary:** Implement the business logic for creating a group, checking schedule windows and uniqueness constraints.
**Scope:** Backend API (Service Layer).

**Deliverables:**
1. Implement `@Transactional` `GroupService.createGroup()` method.
2. Validate active schedule window, check that the user isn't grouped, and ensure group name uniqueness.
3. Save the new `ProjectGroup` and `GroupMembership`.

**References:** Process 2 (DFD 2.1).
**Acceptance Criteria:**
- [ ] Method rolls back entirely if any condition fails (no partial database state).
- [ ] Correctly throws specific domain exceptions mapping to 400 or 409 errors.

**Related Issues:** Depends on **[Backend] Core Domain Entities & Repositories**.

---

## #43 — [Frontend] Student Group Hub & Creation Flow

**Estimate:** 2 Points

**Problem Summary:** Implement the main student landing page, conditional states, and creation form.
**Scope:** Frontend (Pages). **Framework:** Vue.

**Deliverables:**
1. Implement `/student/group/create` page with Zod client-side validation.
2. Implement `/student/group` hub page functioning as a router for "No Group", "Leader", and "Member" views.

**References:** Process 2 (DFD 2.1).
**Acceptance Criteria:**
- [ ] Group creation form handles 409 conflicts with inline errors.
- [ ] Hub page conditionally renders the correct interface based on API response.

**Related Issues:** Consolidates the dedicated group creation form page with the main student routing hub page. Depends on **[Frontend] Shared UI Components**.

---

## #44 — [QA] Schedule & Creation Integration
**Estimate:** 1 Point

**Problem Summary:** Verify group creation end-to-end, testing boundaries and database integrity.
**Scope:** QA Integration Tests.

**Deliverables:**
1. Write tests for creating a group in an active window.
2. Write tests attempting creation outside the window, with duplicate names, or as a grouped student.

**References:** Process 2 (DFD 2.1).
**Acceptance Criteria:**
- [ ] Closed-window scenarios produce the correct 400 error.
- [ ] Direct DB queries verify the presence of Group and Membership records on success.

**Related Issues:** Consolidates end-to-end tests for both the group creation happy path and the schedule window enforcement logic. Depends on **[Backend] Validate Schedule & Create Group Service**.

---

## #45 — [Backend] Invitation Lifecycle Services & Controller
**Estimate:** 2 Points

**Problem Summary:** Implement logic for sending, canceling, accepting, and declining invitations, including bulk auto-denial.
**Scope:** Backend API (Service & Controller Layers).

**Deliverables:**
1. Implement `GroupService` methods for the invite lifecycle.
2. Build the `@Transactional` response logic: On accept, create the `GroupMembership` row and bulk update competing `PENDING` invites to `AUTO_DENIED`.
3. Implement `InvitationController`.
4. Enforce DISBANDED freeze: throw 400 if `group.status == DISBANDED` before sending an invitation.
5. Enforce max team size on send and accept: count = current members + pending outbound invitations; read limit via `TermConfigService.getMaxTeamSize()`.
6. Enforce roster lock on accept: throw 400 if `group.status` is `TOOLS_BOUND` or higher when student calls `PATCH /invitations/{id}/respond`.

**References:** Process 2 (DFD 2.2, 2.3).
**Acceptance Criteria:**
- [ ] Only the `TEAM_LEADER` can send/cancel invitations.
- [ ] Accepting an invitation atomically updates all competing invites.
- [ ] Sending an invitation to/from a DISBANDED group returns 400.
- [ ] Invitation rejected when (members + pending invitations) >= max team size.
- [ ] Student accept blocked with 400 when group status is `TOOLS_BOUND` or higher.

**Related Issues:** Consolidates the business service layer logic for invitations with the REST controller endpoints. Depends on **[Backend] Core Domain Entities & Repositories**.

---

## #46 — [Frontend] Invitation Interface & Navigation Badge

**Estimate:** 1 Point

**Problem Summary:** Build the UI for managing pending invitations and a persistent notification badge.
**Scope:** Frontend (Pages & Layout). **Framework:** Vue.

**Deliverables:**
1. Implement `InvitationCard.vue`.
2. Implement `/student/group/invitations` inbox page.
3. Implement a layout-level composable to fetch/display the pending invitation count.

**References:** Process 2 (DFD 2.2, 2.3).
**Acceptance Criteria:**
- [ ] Buttons disable during API calls.
- [ ] Navigation badge updates when an invitation is accepted/declined without a page reload.

**Related Issues:** Consolidates the invitation card component, the inbox list page, and the persistent navigation layout badge. Depends on **[Backend] Invitation Lifecycle Services & Controller**.

---

## #47 — [QA] Invitation Lifecycle E2E
**Estimate:** 1 Point

**Problem Summary:** Verify the complete invitation lifecycle and high-risk auto-denial side-effects.
**Scope:** QA Integration Tests.

**Deliverables:**
1. Test successful dispatch and TEAM_LEADER authorization.
2. Test the accept flow and auto-denial updates in the database.
3. Execute a concurrency test on simultaneous accept requests.
4. Test DISBANDED freeze: verify sending an invitation on a DISBANDED group returns 400.
5. Test roster lock: verify student accept is blocked with 400 when group is `TOOLS_BOUND`.
6. Test max team size: verify invitation is rejected when (members + pending) >= limit.

**References:** Process 2 (DFD 2.2, 2.3).
**Acceptance Criteria:**
- [ ] Auto-denial verified via direct database queries.
- [ ] Concurrency test proves only one membership row is generated.
- [ ] DISBANDED group invitation attempt returns 400.
- [ ] Accept on TOOLS_BOUND group returns 400.
- [ ] Over-capacity invitation attempt returns 400.

**Related Issues:** Depends on **[Backend] Invitation Lifecycle Services & Controller**.

---

## #48 — [Backend] External Tool Validation Services
**Estimate:** 2 Points

**Problem Summary:** Build REST clients to ping the JIRA and GitHub APIs.
**Scope:** Backend API (External Integrations).

**Deliverables:**
1. Implement `JiraValidationService` and `GitHubValidationService`.
2. Map external HTTP failures to specific 422 domain exceptions.

**References:** Process 2 (DFD 2.4, 2.5).
**Acceptance Criteria:**
- [ ] Services strictly enforce a 5-second timeout.
- [ ] GitHub service halts if the first call fails, skipping the second.

**Related Issues:** Consolidates the external API REST clients for both JIRA and GitHub into a single implementation effort.

---

## #49 — [Backend] Tool Binding & Encryption Orchestration
**Estimate:** 3 Points

**Problem Summary:** Implement AES encryption at rest and orchestration logic.
**Scope:** Backend API (Security & Service Layer).

**Deliverables:**
1. Implement `EncryptionService` using AES-256-GCM.
2. Implement `GroupService.bindJira()` and `bindGitHub()`.
3. Enforce DISBANDED freeze in both `bindJira()` and `bindGitHub()`: throw 400 if `group.status == DISBANDED`.

**References:** Process 2 (DFD 2.4, 2.5).
**Acceptance Criteria:**
- [ ] Encryption requires a 32-byte key and throws unchecked exceptions on tampering.
- [ ] No DB changes occur if external validation fails.
- [ ] Tokens are never saved as plaintext.
- [ ] Binding attempt on a DISBANDED group returns 400 without touching the DB.

**Related Issues:** Consolidates the creation of the AES encryption service, configuration setup, and the tool-binding service logic. Depends on **[Backend] External Tool Validation Services**.

---

## #50 — [Frontend] Tool Binding UI

**Estimate:** 1 Point

**Problem Summary:** Create the interface for the Team Leader to input external credentials.
**Scope:** Frontend (Components & Pages). **Framework:** Vue.

**Deliverables:**
1. Implement `ToolBindingForm.vue` with locked/success states.
2. Implement `/student/group/tools` page for JIRA and GitHub forms.

**References:** Process 2 (DFD 2.4, 2.5).
**Acceptance Criteria:**
- [ ] Redirects non-Team Leaders.
- [ ] Forms lock into a read-only state displaying a success icon when bound.

**Related Issues:** Consolidates the dual-purpose form component with the overarching binding page. Depends on **[Backend] Tool Binding & Encryption Orchestration**.

---

## #51 — [QA] Tool Integration & Security E2E
**Estimate:** 2 Points

**Problem Summary:** Verify external tool binding logic via WireMock and assert at-rest encryption.
**Scope:** QA Integration Tests.

**Deliverables:**
1. Test JIRA and GitHub binding using WireMock.
2. Write JDBC-level tests to assert stored ciphertext does not match plaintext.
3. Test DISBANDED freeze: verify JIRA and GitHub binding on a DISBANDED group returns 400.

**References:** Process 2 (DFD 2.4, 2.5).
**Acceptance Criteria:**
- [ ] WireMock verifies API call behaviors and specific 422 error strings.
- [ ] Direct DB queries confirm random IVs per encryption.
- [ ] DISBANDED group tool binding attempt returns 400.

**Related Issues:** Consolidates the WireMock tests for both external tools alongside the database-level AES encryption checks. Depends on **[Backend] Tool Binding & Encryption Orchestration**.

---

## #52 — [Backend] Coordinator Override Services & Controller
**Estimate:** 2 Points

**Problem Summary:** Provide endpoints for staff to manually add/remove students and disband groups.
**Scope:** Backend API (Service & Controller Layers).

**Deliverables:**
1. Implement `GroupService` bypass methods and `CoordinatorGroupController`.
2. Ensure disbanding cascades correctly.
3. On coordinator force-add: bulk `AUTO_DENY` all other `PENDING` invitations for that student in the same `@Transactional` block.
4. Enforce max team size on coordinator force-add: count = current members + pending outbound invitations; read limit via `TermConfigService.getMaxTeamSize()`.

**References:** Process 2 (DFD 2.6).
**Acceptance Criteria:**
- [ ] Blocks the removal of a `TEAM_LEADER`.
- [ ] Disbanding nullifies group associations cleanly.
- [ ] Force-add atomically sets all competing PENDING invitations for that student to AUTO_DENIED.
- [ ] Force-add rejected with 400 when (members + pending) >= max team size.

**Related Issues:** Consolidates the coordinator-specific bypass logic with its corresponding secure controller endpoints. Depends on **[Backend] Core Domain Entities & Repositories**.

---

## #53 — [QA] Coordinator Role & Overrides E2E
**Estimate:** 2 Points

**Problem Summary:** Verify manual staff overrides and strict RBAC enforcement.
**Scope:** QA Integration Tests.

**Deliverables:**
1. Test manual additions, removals, disband operations, and the `TEAM_LEADER` removal blocker.
2. Verify RBAC with `Professor` and `Admin` tokens.
3. Test auto-deny on force-add: verify all PENDING invitations for the added student are set to AUTO_DENIED in the DB.
4. Test max team size on force-add: verify 400 is returned when group is at capacity.

**References:** Process 2 (DFD 2.6).
**Acceptance Criteria:**
- [ ] Disband test confirms DB cascading.
- [ ] Non-Coordinator roles receive a strict 403 Forbidden.
- [ ] Direct DB query confirms AUTO_DENIED status on competing invitations after force-add.
- [ ] Force-add at capacity returns 400.

**Related Issues:** Depends on **[Backend] Coordinator Override Services & Controller**.

---

## #54 — [Frontend] Coordinator Dashboard & Management

**Estimate:** 1 Point

**Problem Summary:** Build staff-facing table views and detail pages.
**Scope:** Frontend (Pages). **Framework:** Vue.

**Deliverables:**
1. Implement `/coordinator/groups` term-filtered table page.
2. Implement `/coordinator/groups/:groupId` detail page with inline member controls and a disband button.

**References:** Process 2 (DFD 2.6).
**Acceptance Criteria:**
- [ ] Disband requires confirmation dialog.
- [ ] Member list updates immediately upon adding/removing without hard refreshes.

**Related Issues:** Consolidates the list-view table page with the detailed individual group management view. Depends on **[Frontend] Shared UI Components**.

---

## #55 — [Backend] TermConfigService — Active Term & Max Team Size Resolution
**Estimate:** 1 Point

**Problem Summary:** All P2 services require a reliable way to read `active_term_id` and `max_team_size` from the `system_config` table without accepting these values from the frontend. Red Team owns this table — Blue Team adds their own config keys to it later.
**Scope:** Backend — Data Layer + Service Layer.

**Deliverables:**
1. Create `SystemConfig` JPA entity mapping to `system_config` table: `config_key (VARCHAR PK)`, `config_value (VARCHAR)`.
2. Create `SystemConfigRepository` with `findByConfigKey(String key)`.
3. Seed `data.sql` with Red Team's required rows:
   ```sql
   INSERT IGNORE INTO system_config (config_key, config_value) VALUES ('active_term_id', '2024-FALL');
   INSERT IGNORE INTO system_config (config_key, config_value) VALUES ('max_team_size', '5');
   ```
4. Implement `TermConfigService` with `getActiveTermId()` and `getMaxTeamSize()` reading from `system_config` by `config_key`.
5. Throw `TermConfigNotFoundException` (500) if the key is missing.
6. Integrate into `GroupService` and `InvitationService` so `termId` is never sourced from request bodies or query params.

**References:** Process 2 — foundational dependency for all P2 services.
**Acceptance Criteria:**
- [ ] `system_config` table auto-created by Hibernate on startup.
- [ ] `getActiveTermId()` returns the value where `config_key = 'active_term_id'`.
- [ ] `getMaxTeamSize()` returns the integer value where `config_key = 'max_team_size'`.
- [ ] Missing key throws `TermConfigNotFoundException` with HTTP 500.
- [ ] No P2 endpoint accepts `termId` as a request parameter.

**Note for Blue Team:** `system_config` is a shared key-value config table owned by Red Team. Add your own config keys (e.g. `system_state`) as rows — do not create a separate table.

**Related Issues:** No Blue Team dependency — Red Team creates the table. Depended on by **[Backend] Validate Schedule & Create Group Service** and **[Backend] Invitation Lifecycle Services & Controller**.
