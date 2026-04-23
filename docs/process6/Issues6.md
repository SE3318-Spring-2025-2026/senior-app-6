# Process 6: Deliverable Submission & Review - Planning

## Identified API Endpoints
1. **`POST /api/deliverables/{deliverableId}/submissions`**
   - **Purpose:** Accept the initial Markdown submission from the Team Leader.
   - **Validations:** Enforces deadline, committee assignment prerequisite, and Team Leader role.
2. **`PUT /api/submissions/{submissionId}`**
   - **Purpose:** Allow the Team Leader to submit a revised version if changes are requested before the final deadline.
3. **`GET /api/submissions/{submissionId}`**
   - **Purpose:** Fetch the submitted markdown document for the Committee's read-only review view or the student's history.
4. **`POST /api/submissions/{submissionId}/rubric-mappings`**
   - **Purpose:** Save the specific sections of the markdown document that link to evaluation rubric criteria.
5. **`POST /api/submissions/{submissionId}/comments`**
   - **Purpose:** Allow committee members (Advisor/Jury) to leave inline or general feedback.
6. **`GET /api/submissions/{submissionId}/comments`**
   - **Purpose:** Retrieve feedback for the group to review and act upon.

## Proposed Issue Titles

### Backend (API & DB)
- [Backend] Implement Process 6 Entities & Repositories (Data Layer)
- [Backend] Implement POST /api/deliverables/{id}/submissions endpoint
- [Backend] Implement PUT /api/submissions/{id} endpoint
- [Backend] Implement GET /api/submissions/{id} endpoint
- [Backend] Implement POST /api/submissions/{id}/rubric-mappings endpoint
- [Backend] Implement POST /api/submissions/{id}/comments endpoint
- [Backend] Implement GET /api/submissions/{id}/comments endpoint
- [Backend] Implement Deliverable Submission Notification Service

### Frontend (UI)
- [Frontend] Integrate WYSIWYG Markdown Editor with Image Support
- [Frontend] Build Deliverable Submission UI for Team Leaders
- [Frontend] Implement Rubric Linking UI inside Markdown Editor
- [Frontend] Develop Committee Read-Only Review & Rubric Split-Panel
- [Frontend] Implement Submission Commenting & Feedback UI

### QA Test
- [QA] Integration Testing for Submission Deadlines & Committee Prerequisite
- [QA] API Security & RBAC Enforcement Testing (Team Leader vs Members vs Staff)
- [QA] Integration Testing for Committee Review & Commenting Lifecycle
- [QA] E2E Testing for WYSIWYG Editor & Rubric Mapping Flow

---

## [Backend] Implement Process 6 Entities & Repositories (Data Layer)

**Problem Summary:** Establish the database schema and Spring Data JPA repositories for Deliverable Submissions, Rubric Mappings, and Submission Comments.

**Scope:** Backend API and Database

**Estimate:** 2 Points

**Deliverables:**
1. JPA Entities for `DeliverableSubmission`, `RubricMapping`, and `SubmissionComment`.
2. Corresponding Spring Data JPA Repositories.
3. Database migration scripts/Hibernate schema updates.

**References:** `README.md` (Process 6)

**Acceptance Criteria:**
- [ ] Tables are auto-created by Hibernate on startup with all specified columns and constraints.
- [ ] Proper foreign key relationships exist (e.g., submission -> group, submission -> deliverable, comment -> submission).

**Related Issues:** None

---

## [Backend] Implement POST /api/deliverables/{id}/submissions endpoint

**Problem Summary:** Allow the Team Leader to submit the initial Markdown document for a deliverable.

**Scope:** Backend API

**Estimate:** 3 Points

**Deliverables:**
1. API endpoint `POST /api/deliverables/{id}/submissions` accepting markdown content.
2. Business logic to validate deadlines, committee assignment, and Team Leader role.

**References:** `README.md` (Process 6, Steps 1 & 3)

**Acceptance Criteria:**
- [ ] Returns 201 Created on success with the new submission ID.
- [ ] Rejects submission if the deadline configured by the Coordinator has passed (400 Bad Request).
- [ ] Rejects submission if the group is not assigned to a committee (400 Bad Request).
- [ ] Enforces RBAC: Returns 403 Forbidden if accessed by anyone other than the Team Leader.

**Related Issues:** [Backend] Implement Process 6 Entities & Repositories (Data Layer)

---

## [Backend] Implement PUT /api/submissions/{id} endpoint

**Problem Summary:** Allow the Team Leader to submit a revised version of the Markdown document if changes are requested before the final deadline.

**Scope:** Backend API

**Estimate:** 2 Points

**Deliverables:**
1. API endpoint `PUT /api/submissions/{id}` to update the existing submission.
2. Logic to update the markdown content and track revision timestamps.

**References:** `README.md` (Process 6, Step 7)

**Acceptance Criteria:**
- [ ] Returns 200 OK on successful update with the revised submission data.
- [ ] Rejects the update if the final grading deadline has passed (400 Bad Request).
- [ ] Enforces RBAC: Returns 403 Forbidden if updated by anyone other than the Team Leader.

**Related Issues:** [Backend] Implement POST /api/deliverables/{id}/submissions endpoint

---


## [Backend] Implement GET /api/submissions/{id} endpoint

**Problem Summary:** Fetch the submitted markdown document for the Committee's read-only review view or the student's history.

**Scope:** Backend API

**Estimate:** 2 Points

**Deliverables:**
1. API endpoint `GET /api/submissions/{id}`.
2. DTO mapping to return submission content safely to the frontend.

**References:** `README.md` (Process 6, Step 5)

**Acceptance Criteria:**
- [ ] Endpoint returns a 200 OK with the fully populated submission DTO.
- [ ] Students can only view their own group's submissions (403 Forbidden otherwise).
- [ ] Committee members can view submissions specifically assigned to their committee.

**Related Issues:** [Backend] Implement POST /api/deliverables/{id}/submissions endpoint

---

## [Backend] Implement POST /api/submissions/{id}/rubric-mappings endpoint

**Problem Summary:** Save the specific sections of the markdown document that link to evaluation rubric criteria.

**Scope:** Backend API

**Estimate:** 2 Points

**Deliverables:**
1. API endpoint `POST /api/submissions/{id}/rubric-mappings` accepting an array of mapping objects.
2. Logic to parse and store mappings between markdown sections and `rubricId`/`criterionId`.

**References:** `README.md` (Process 6, Step 2)

**Acceptance Criteria:**
- [ ] Validates that criteria IDs exist in the deliverable's rubric; returns 400 Bad Request if invalid.
- [ ] Successfully replaces any existing mappings for the submission and returns 200 OK.
- [ ] Enforces RBAC: Returns 403 Forbidden if accessed by anyone other than the Team Leader.

**Related Issues:** [Backend] Implement POST /api/deliverables/{id}/submissions endpoint

---

## [Backend] Implement POST /api/submissions/{id}/comments endpoint

**Problem Summary:** Allow committee members (Advisor/Jury) to leave inline or general feedback on a submission.

**Scope:** Backend API

**Estimate:** 2 Points

**Deliverables:**
1. API endpoint `POST /api/submissions/{id}/comments` accepting comment text and optional section references.
2. Database updates to attach the comment to the parent submission and the logged-in reviewer.

**References:** `README.md` (Process 6, Step 6)

**Acceptance Criteria:**
- [ ] Returns 201 Created upon successfully adding the comment.
- [ ] Enforces RBAC: Validates that the caller is an assigned Committee Member for this group (403 Forbidden if not).

**Related Issues:** [Backend] Implement GET /api/submissions/{id} endpoint

---

## [Backend] Implement GET /api/submissions/{id}/comments endpoint

**Problem Summary:** Retrieve feedback for the group to review and act upon.

**Scope:** Backend API

**Estimate:** 1 Point

**Deliverables:**
1. API endpoint `GET /api/submissions/{id}/comments`.
2. Backend logic to fetch and map comment records to a DTO list.

**References:** `README.md` (Process 6, Step 6)

**Acceptance Criteria:**
- [ ] Endpoint returns a 200 OK with a list of comments for the specified submission.
- [ ] Visible to both the owning group members and the assigned committee members; blocks unauthorized users.

**Related Issues:** [Backend] Implement POST /api/submissions/{id}/comments endpoint

---

## [Backend] Implement Deliverable Submission Notification Service

**Problem Summary:** Notify all committee members (Advisor + Jury) when a new deliverable submission is available for review.

**Scope:** Backend API

**Estimate:** 2 Points

**Deliverables:**
1. Backend event publisher triggered upon successful `POST /api/deliverables/{id}/submissions`.
2. Notification service payload dispatch to the assigned professors in the committee.

**References:** `README.md` (Process 6, Step 4)

**Acceptance Criteria:**
- [ ] Automated notification is triggered exactly once when a Team Leader submits a deliverable.
- [ ] The dispatched notification payload includes the `submissionId` and the `groupId`.
- [ ] Only professors assigned to the specific committee for that group receive the notification.

**Related Issues:** [Backend] Implement POST /api/deliverables/{id}/submissions endpoint

---

## [Frontend] Integrate WYSIWYG Markdown Editor with Image Support

**Problem Summary:** Embed a robust WYSIWYG Markdown editor allowing rich text formatting and image insertions for deliverable documents.

**Scope:** Frontend

**Estimate:** 2 Points

**Deliverables:**
1. Selection and integration of a Vue-compatible WYSIWYG Markdown editor component.
2. Configuration to handle embedded image data (e.g., base64 encoding or external image hosting).

**References:** `README.md` (Process 6, Step 2)

**Acceptance Criteria:**
- [ ] Editor seamlessly converts user inputs into valid Markdown format.
- [ ] Images can be successfully embedded and previewed within the editor.
- [ ] Editor state safely synchronizes with the Vue component's data model.

**Related Issues:** None

---

## [Frontend] Build Deliverable Submission UI for Team Leaders

**Problem Summary:** Create the page for Team Leaders to write, save, and submit their group's deliverable documents (Proposal, SoW, etc.).

**Scope:** Frontend

**Estimate:** 2 Points

**Deliverables:**
1. Deliverable workspace page containing the WYSIWYG editor.
2. Integration with `POST /api/deliverables/{id}/submissions` for initial submission and `PUT /api/submissions/{id}` for revisions.
3. UI logic to enforce strict deadlines (disabling the submit button if the deadline has passed).

**References:** `README.md` (Process 6, Steps 1, 3 & 7)

**Acceptance Criteria:**
- [ ] Team Leader can successfully submit and update a deliverable before the deadline.
- [ ] Non-Team Leader members are presented with a read-only view of the current document.
- [ ] UI clearly displays remaining time before the submission deadline.

**Related Issues:** [Frontend] Integrate WYSIWYG Markdown Editor with Image Support, [Backend] Implement POST /api/deliverables/{id}/submissions endpoint

---

## [Frontend] Implement Rubric Linking UI inside Markdown Editor

**Problem Summary:** Allow students to highlight sections of their markdown document and link them to specific criteria from the evaluation rubric.

**Scope:** Frontend

**Estimate:** 3 Points

**Deliverables:**
1. A side-panel or modal displaying the deliverable's active rubric criteria.
2. Logic to wrap or tag highlighted markdown text with specific `criterionId` references.
3. API integration with `POST /api/submissions/{id}/rubric-mappings`.

**References:** `README.md` (Process 6, Step 2)

**Acceptance Criteria:**
- [ ] User can select text in the editor and click a rubric criterion to create a mapping link.
- [ ] Mapped sections are visually distinct (e.g., highlighted or underlined) in the editor.
- [ ] Mappings are successfully saved to the backend upon submission.

**Related Issues:** [Frontend] Build Deliverable Submission UI for Team Leaders, [Backend] Implement POST /api/submissions/{id}/rubric-mappings endpoint

---

## [Frontend] Develop Committee Read-Only Review & Rubric Split-Panel

**Problem Summary:** Build a split-screen view for committee members to read the submitted markdown document alongside the evaluation rubric.

**Scope:** Frontend

**Estimate:** 3 Points

**Deliverables:**
1. A split-panel layout: Left side rendering the Markdown submission, Right side displaying the rubric.
2. Interactive mapping links: Clicking a rubric criterion automatically scrolls to or highlights the mapped text in the document.
3. Integration with `GET /api/submissions/{id}`.

**References:** `README.md` (Process 6, Step 5)

**Acceptance Criteria:**
- [ ] Submitted markdown and embedded images render perfectly in read-only mode.
- [ ] Clicking a criterion on the rubric highlights the student-mapped section in the text.
- [ ] The panel strictly blocks any text editing capabilities.

**Related Issues:** [Backend] Implement GET /api/submissions/{id} endpoint

---

## [Frontend] Implement Submission Commenting & Feedback UI

**Problem Summary:** Provide an interface for committee members to leave feedback on a submission, and for students to view it.

**Scope:** Frontend

**Estimate:** 2 Points

**Deliverables:**
1. A comment thread component attached to the bottom or side of the review panel.
2. A comment input form for Professors.
3. API integration with `GET /api/submissions/{id}/comments` and `POST /api/submissions/{id}/comments`.

**References:** `README.md` (Process 6, Step 6)

**Acceptance Criteria:**
- [ ] Committee members can successfully submit text comments on the submission.
- [ ] Students can view the comments left by the committee.
- [ ] Student view hides the input form (students cannot reply directly via comments, only via revising the document).

**Related Issues:** [Frontend] Develop Committee Read-Only Review & Rubric Split-Panel, [Backend] Implement POST /api/submissions/{id}/comments endpoint

---

## [QA] Integration Testing for Submission Deadlines & Committee Prerequisite

**Problem Summary:** Verify that the backend strictly enforces submission deadlines and committee assignments prior to accepting documents.

**Scope:** QA Test

**Estimate:** 2 Points

**Deliverables:**
1. Automated tests attempting to submit a document after the deliverable deadline has passed.
2. Automated tests attempting to submit a document for a group that lacks a committee assignment.

**References:** `README.md` (Process 6, Steps 1 & 3)

**Acceptance Criteria:**
- [ ] Test suite confirms a 400 Bad Request is returned if the submission deadline has expired.
- [ ] Test suite confirms a 400 Bad Request is returned if the group is not bounded to a committee.
- [ ] Valid submissions return 201 Created.

**Related Issues:** [Backend] Implement POST /api/deliverables/{id}/submissions endpoint

---

## [QA] API Security & RBAC Enforcement Testing (Team Leader vs Members vs Staff)

**Problem Summary:** Ensure only the Team Leader can submit or update documents and that data isolation is maintained.

**Scope:** QA Test

**Estimate:** 2 Points

**Deliverables:**
1. API security tests verifying standard group members are blocked from `POST/PUT` endpoints.
2. Data isolation tests verifying non-committee professors cannot access submission endpoints for unassigned groups.

**References:** `README.md` (Process 6, Steps 3 & 5), NFR-4 (Role-Based Access)

**Acceptance Criteria:**
- [ ] Standard members receive a 403 Forbidden when attempting to submit or update the deliverable.
- [ ] Professor B receives a 403 Forbidden when attempting to query or comment on Professor A's assigned group submissions.

**Related Issues:** [Backend] Implement POST /api/deliverables/{id}/submissions endpoint, [Backend] Implement GET /api/submissions/{id} endpoint

---

## [QA] Integration Testing for Committee Review & Commenting Lifecycle

**Problem Summary:** Verify the end-to-end flow of a professor viewing a submission and leaving feedback.

**Scope:** QA Test

**Estimate:** 2 Points

**Deliverables:**
1. Integration tests simulating a Professor posting a comment.
2. Integration tests validating the students can retrieve the posted comments.

**References:** `README.md` (Process 6, Step 6)

**Acceptance Criteria:**
- [ ] Test suite confirms comments are successfully persisted and associated with the correct submission ID and Professor ID.
- [ ] Test suite verifies the retrieved comments list matches the expected data format for the frontend.

**Related Issues:** [Backend] Implement POST /api/submissions/{id}/comments endpoint, [Backend] Implement GET /api/submissions/{id}/comments endpoint

---

## [QA] E2E Testing for WYSIWYG Editor & Rubric Mapping Flow

**Problem Summary:** Verify the complex frontend interaction of mapping document sections to rubric criteria.

**Scope:** QA Test

**Estimate:** 2 Points

**Deliverables:**
1. Cypress/Playwright scripts mimicking a user highlighting text and mapping it to a criterion.
2. Verification that the correct payload shape is sent to the backend.

**References:** `README.md` (Process 6, Step 2)

**Acceptance Criteria:**
- [ ] Automated test successfully highlights text, clicks a rubric mapping button, and verifies the UI updates to show the mapped state.
- [ ] The test intercepts the network request and asserts that the `rubricId`/`criterionId` mapping array is populated correctly.

**Related Issues:** [Frontend] Implement Rubric Linking UI inside Markdown Editor, [Frontend] Develop Committee Read-Only Review & Rubric Split-Panel

---

## [Backend] Implement GET /api/deliverables endpoint

**Problem Summary:** Allow students to fetch all active deliverables, their deadlines, and the current submission status of their group.

**Scope:** Backend API

**Estimate:** 2 Points

**Deliverables:**
1. API endpoint `GET /api/deliverables`.
2. Backend logic to append the caller group's submission status (e.g., Not Submitted, Submitted, Graded) to each deliverable DTO based on the `DeliverableSubmission` table.

**References:** `README.md` (Process 6, Phase 6A)

**Acceptance Criteria:**
- [ ] Endpoint returns a 200 OK with a list of deliverables configured by the Coordinator.
- [ ] The response correctly indicates whether the logged-in student's group has already submitted a document for each deliverable.
- [ ] Properly enforces RBAC: returns 403 Forbidden for unauthenticated users.

**Related Issues:** [Backend] Implement Process 6 Entities & Repositories (Data Layer)

---

## [Backend] Implement GET /api/committees/{id}/submissions endpoint

**Problem Summary:** Allow committee members to retrieve all submissions made to their assigned committee to facilitate the review process.

**Scope:** Backend API

**Estimate:** 2 Points

**Deliverables:**
1. API endpoint `GET /api/committees/{id}/submissions`.
2. Backend logic to fetch the latest `DeliverableSubmission` records associated with the groups bounded to this committee.

**References:** `README.md` (Process 6, Phase 6B)

**Acceptance Criteria:**
- [ ] Endpoint returns a 200 OK with a summary list of submissions (excluding full markdown content to save bandwidth).
- [ ] Enforces strict data isolation: returns 403 Forbidden if the requesting professor is not a member of the specified committee.

**Related Issues:** [Backend] Implement POST /api/deliverables/{id}/submissions endpoint

---

## [Frontend] Build Student Deliverables Dashboard

**Problem Summary:** Create a dashboard for students to view all deliverables, deadlines, and navigate to the WYSIWYG submission workspace.

**Scope:** Frontend

**Estimate:** 2 Points

**Deliverables:**
1. A "Deliverables" page for students mapping data from `GET /api/deliverables`.
2. UI cards or a table displaying the `submissionDeadline` and `reviewDeadline`.
3. Contextual action buttons (e.g., "Start Submission", "Edit Submission", or "View Read-Only").

**References:** `README.md` (Process 6, Phase 6A)

**Acceptance Criteria:**
- [ ] Students can clearly see upcoming deadlines and their current submission status.
- [ ] Clicking "Start Submission" correctly routes the Team Leader to the WYSIWYG editor workspace.

**Related Issues:** [Backend] Implement GET /api/deliverables endpoint, [Frontend] Build Deliverable Submission UI for Team Leaders

---

## [Frontend] Build Committee Submissions List View

**Problem Summary:** Create a table view for committee members to see which groups have submitted documents pending review.

**Scope:** Frontend

**Estimate:** 2 Points

**Deliverables:**
1. A "Pending Reviews" tab/page for Professors fetching from `GET /api/committees/{id}/submissions`.
2. A data table listing the submitting group, the deliverable name, and the submission timestamp.
3. Navigation links routing the professor to the Split-Panel Review view.

**References:** `README.md` (Process 6, Phase 6B)

**Acceptance Criteria:**
- [ ] Professors can clearly see which groups have submitted work.
- [ ] Clicking a submission record correctly routes the professor to the read-only review workspace.

**Related Issues:** [Backend] Implement GET /api/committees/{id}/submissions endpoint, [Frontend] Develop Committee Read-Only Review & Rubric Split-Panel

---

## Issue Priorities

| Issue Name | Priority |
| :--- | :--- |
| [Backend] Implement Process 6 Entities & Repositories (Data Layer) | High |
| [Backend] Implement GET /api/deliverables endpoint | High |
| [Backend] Implement POST /api/deliverables/{id}/submissions endpoint | High |
| [Backend] Implement GET /api/submissions/{id} endpoint | High |
| [Frontend] Integrate WYSIWYG Markdown Editor with Image Support | High |
| [Frontend] Build Student Deliverables Dashboard | High |
| [Frontend] Build Deliverable Submission UI for Team Leaders | Medium |
| [Backend] Implement PUT /api/submissions/{id} endpoint | Medium |
| [Backend] Implement GET /api/committees/{id}/submissions endpoint | Medium |
| [Frontend] Build Committee Submissions List View | Medium |
| [Backend] Implement POST /api/submissions/{id}/rubric-mappings endpoint | Medium |
| [Frontend] Implement Rubric Linking UI inside Markdown Editor | Medium |
| [Frontend] Develop Committee Read-Only Review & Rubric Split-Panel | Medium |
| [Backend] Implement POST /api/submissions/{id}/comments endpoint | Medium |
| [Backend] Implement GET /api/submissions/{id}/comments endpoint | Medium |
| [Frontend] Implement Submission Commenting & Feedback UI | Low |
| [Backend] Implement Deliverable Submission Notification Service | Low |
| [QA] Integration Testing for Submission Deadlines & Committee Prerequisite | Low |
| [QA] API Security & RBAC Enforcement Testing (Team Leader vs Members vs Staff) | Low |
| [QA] Integration Testing for Committee Review & Commenting Lifecycle | Low |
| [QA] E2E Testing for WYSIWYG Editor & Rubric Mapping Flow | Low |
