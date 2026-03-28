# Requirements Specification

## 1. Gather Functional Requirements

### 1.1 Are all the inputs to the system specified, including their source, accuracy, range of values, and frequency?

* **FR-1 (User Inputs)**: The system accepts strictly valid student IDs that have been pre-uploaded to the system by the Coordinator.
* **FR-2 (Grading Inputs)**: Advisor grading inputs are strictly bounded to predefined ranges via a user interface picker. Binary grading accepts only `S-100` and `F-0`. Soft grading accepts only `A-100`, `B-80`, `C-60`, `D-50`, and `F-0`.
* **FR-3 (Document Inputs)**: The system accepts text-based document uploads for preset deliverables like the Proposal and Statement of Work (SoW). It also accepts Markdown text inputs with WYSIWYG support, including image insertions.
* **FR-4 (Integration & AI Keys)**: The system accepts GitHub Personal Access Tokens (PAT) and JIRA space bindings supplied manually by Team Leaders. It also securely accepts LLM API keys from the Admin to power AI-driven validation features.

### 1.2 Are all the outputs from the system specified, including their destination, accuracy, range of values, frequency, and format?

* **FR-5 (Grading Outputs)**: The system outputs precisely calculated mathematical averages for final deliverable grades based on a team's "coefficient of allowance" and individual completion ratios.
* **FR-6 (Dashboard Outputs)**: The system displays active live grades directly on the advisor panel during a sprint.
* **FR-7 (Analytics Outputs)**: The system analyzes student activities and outputs activity intensity visually on a daily, weekly, and monthly basis.
* **FR-8 (System Notifications)**: The system outputs one-time-use password reset links generated manually by the Admin.

### 1.3 Are all the external communication interfaces specified, including handshaking, error-checking, and communication protocols?

* **FR-9 (Authentication Interface)**: The system interfaces with GitHub using OAuth protocols (e.g., OAuth2) to securely authenticate students and retrieve their usernames. This interface is strictly isolated for identity verification, not for reading repository data.
* **FR-10 (Data Fetching Interface)**: The system communicates with JIRA and GitHub APIs via the provided PAT to match issue keys with branch names and verify if Pull Requests have been successfully merged.
* **FR-11 (Markdown Rubric Association)**: The WYSIWYG Markdown editor must allow grading criteria descriptions in the Evaluation Rubric to be associated with specific sections of the markdown document.

### 1.4 Are all the tasks the user wants to perform specified?

* **FR-12 (Student Tasks)**: Students register to the system, connect their GitHub accounts, create groups, invite peers, and submit advisee requests to professors.
* **FR-13 (Coordinator Tasks)**: Coordinators upload valid student IDs, configure deliverables, set rubric structures, determine story point targets, assign committees, and manually manage/transfer groups if necessary.
* **FR-14 (Advisor Tasks)**: Advisors review student proposals, leave comments, grade team Scrum performance, and grade work/code reviews.
* **FR-15 (Admin Tasks)**: Admins manually register professors into the system and assign points to specific platform issues.
* **FR-16 (Document Revision Cycle)**: The system enforces a cyclic review process: groups submit a proposal, committees review and request changes, groups submit a revised proposal, and finally, the committee grades it.

### 1.5 Is the data used in each task and the data resulting from each task specified?

* **FR-17 (Group Creation Flow)**: The task uses approved student IDs; it results in the creation of a group entity, automatically assigns the creator as the "Team Leader," and generates pending notifications for invited members.
* **FR-18 (Individual Evaluation Flow)**: The task uses the ratio of completed story points against the coordinator's target; it results in an individual allowance modifier that adjusts the final team grade for that specific student.
* **FR-19 (AI Pull Request Reader)**: The system utilizes an AI module to read Pull Request comments and verify that a legitimate code review process occurred, resulting in data that impacts the Team Evaluation grade.
* **FR-20 (AI Issue Validator)**: The system utilizes an AI module to read file diffs introduced in a PR and validates them against the original JIRA issue description, resulting in verification data for proper implementation.

---

## 2. Define Non-Functional Requirements

### 2.1 Is the expected response time, from the user's point of view, specified?

* **NFR-1**: The system's delay time must not exceed 5 seconds from the perspective of the user.

### 2.2 Are other timing considerations specified?

* **NFR-2 (Daily Refreshes)**: The system must automatically execute a daily refresh of active stories via the JIRA and GitHub integrations to validate work estimates.
* **NFR-3 (Strict Deadlines)**: Critical workflow phases—such as Group Creation, Advisor Association, Proposal Submission, and SoW Submission—are strictly bounded by schedules and deadlines set by the Coordinator.

### 2.3 Is the level of security specified?

* **NFR-4 (Role-Based Access)**: The system implements multiple security layers, ensuring that only authorized users (Admin, Coordinator, Advisor, Students) can access their specific role-based pages and private group data.
* **NFR-5 (Enforced Resets)**: Coordinators and Advisors are strictly required to change their passwords during their initial login to the system.
* **NFR-6 (Audit Logging)**: The system must securely trace and log user events to maintain an audit trail.
* **NFR-7 (Data at Rest Encryption)**: The system must encrypt highly sensitive data at rest using industry-standard algorithms (e.g., AES-256). This includes GitHub PATs, JIRA bindings, and LLM API keys to prevent plain-text exposure in the database.

### 2.4 Is the reliability specified and the strategy for error detection and recovery?

* **NFR-8 (Data Integrity & Recovery)**: The system must work reliably without data loss; errors (such as failed logins or broken integrations) must be explicitly detected, logged, displayed to the user via readable error codes, and allow for a retry when possible.
* **NFR-9 (Automated Sanitization)**: As an error-recovery and data-cleaning strategy, the system actively identifies and automatically disbands any student groups that fail to secure an advisor.

### 2.5 Is the maintainability of the system specified?

* **NFR-10**: The architecture must be highly modular, ensuring that grading rules, integrations, and user roles can be safely updated without requiring major changes to the entire system infrastructure. The codebase must be readable.

### 2.6 Is the definition of success included? Of failure?

* **NFR-11 (Success Metrics)**: The system is a success if it can support 10,000 concurrent users; students successfully form groups and submit documents; advisors evaluate correctly; and the math distributes points accurately.
* **NFR-12 (Failure Metrics)**: The system is deemed a failure if users cannot access the platform, file uploads fail, calculations produce incorrect mathematical results, or points are awarded to the wrong entities.

---

## 3. Document Integration Requirements

### 3.1 What external systems are involved?

* **IR-1 (Authentication)**: Integrates with GitHub via OAuth protocols strictly for secure user authentication and username retrieval.
* **IR-2 (Sprint Tracking)**: Integrates with JIRA via Team Leader supplied bindings to fetch active stories, issue keys, assignees, and descriptions.
* **IR-3 (Code & PR Validation)**: Integrates with GitHub Organizations via Team Leader supplied Personal Access Tokens (PAT) to track branches, read file diffs, and verify merged Pull Requests against JIRA issue keys.
* **IR-4 (AI Services)**: Integrates with an external LLM API to process text from Pull Requests and issue descriptions for automated validation and review verification.




# Phase 2: Business Process Mapping


## Identified Critical Business Processes

| **Process** | **Description** | **Primary Actors** | **System Components Involved** |
| --- | --- | --- | --- |
| P0 – Coordinator Setup | Rubrics, sprint targets, deliverable weights, sprint-to-deliverable assignments, and schedules | Coordinator | Frontend, Backend, Database |
| P1 – Registration & Authentication | GitHub OAuth for students; manual registration for professors via Admin | Admin, Student, Professor | Frontend, Backend, Auth Service, External GitHub API |
| P2 – Group Creation & Tool Integration | Group formation, member invitation, JIRA/GitHub PAT binding | Student (Team Leader) | Frontend, Backend, Database, External JIRA API, External GitHub API |
| P3 – Advisor Association & Sanitization | Advisee requests, approvals, releases, transfers, and auto-disbanding | Team Leader, Advisor, Coordinator | Frontend, Backend, Database, Scheduler/Cron Job |
| P4 – Committee Assignment | Assigning advisors and jury members to committees; binding groups | Coordinator | Frontend, Backend, Database, Notification Service |
| P5 – Sprint Tracking, Scrum Grading & AI Validation | Automated JIRA/GitHub fetch, advisor sprint grading, AI PR and diff validation | System (Automated), Advisor | Backend, Database, External JIRA/GitHub APIs, AI Validation Service |
| P6 – Deliverable Submission & Review | Proposal/SoW/Demonstration lifecycle, authorship, submission, and review cycle | Team Leader, Committee | Frontend, Backend, Database, Notification Service |
| P7 – Grading, Scalar & Final Grade Calculation | Rubric grading, scalar math, weighted totals, and individual grade calculation | Committee, System | Frontend, Backend, Grading Engine, Database |

---

## Process 0: Coordinator Setup & Configuration

> **Source:** *Setting Evaluation Rubric*, *Group Creation*, *Scrum* sections of the Project Definition.

| **Step** | **Action** | **System Component** | **Required Data** |
| --- | --- | --- | --- |
| 1 | Coordinator logs in; system enforces mandatory password change on first login. | Frontend / Backend – Auth Service | `email`, `password` |
| 2 | Coordinator uploads the list of eligible Student IDs. | Frontend – Admin Dashboard | `studentIds[]` |
| 3 | Coordinator creates deliverable entries (Proposal, SoW, Demonstration) and sets their submission and review deadlines (bounded schedules). | Frontend – Deliverable Config Panel | `name`, `type`, `submissionDeadline`, `reviewDeadline` |
| 4 | Coordinator defines the evaluation rubric per deliverable: criterion name, grading type (Binary / Soft), and weight. | Frontend – Rubric Builder | `deliverableId`, `criterionName`, `gradingType`, `weight` |
| 5 | Coordinator sets the per-sprint story point target for each student (used for Individual Evaluation). | Frontend – Sprint Config Panel | `sprintId`, `storyPointTarget` |
| 6 | Coordinator defines which sprint(s) contribute to which deliverable and at what percentage. | Frontend – Sprint-to-Deliverable Mapping Panel | `sprintId`, `deliverableId`, `contributionPercentage` |
| 7 | Coordinator sets the weight of each deliverable (e.g., Documents 50%: Proposal 15%, SoW 35%; Demonstration 50%). | Frontend – Deliverable Weight Panel | `deliverableId`, `weight` |
| 8 | Coordinator sets the Scrum schedule (sprint start/end dates). | Frontend – Schedule Panel | `sprintId`, `startDate`, `endDate` |

---

## Process 1: User Registration & Authentication

> **Source:** *User Registration*, *GitHub OAuth Integration*, *Admin password reset link* sections.

| **Step** | **Action** | **System Component** | **Required Data** |
| --- | --- | --- | --- |
| 1 | Admin manually registers a professor account into the system. | Frontend – Admin Panel / Backend | `name`, `email`, `department` |
| 2 | System generates a one-time-use password reset link and dispatches it to the professor's email. | Backend – Notification & Auth Service | `staffId`, `email`, `token` |
| 3 | Professor logs in for the first time and is required to change their password before proceeding. | Frontend – Login Page / Backend | `token`, `password` |
| 4 | Student navigates to the login page and clicks "Sign in with GitHub". | Frontend – Login Page | *(UI action)* |
| 5 | System redirects the student to GitHub's OAuth 2.0 authorization page via OAuth2. | External Service – GitHub OAuth | `client_id`, `redirect_uri`, `scope` |
| 6 | Student grants permission; Spring Security OAuth2 handles the callback and exchanges the authorization code for an access token. | Backend – Auth Service | `code`, `client_secret` |
| 7 | Backend fetches the authenticated student's GitHub username from the GitHub API. | External Service – GitHub REST API | `OAuth access_token` |
| 8 | System checks the GitHub username against the pre-uploaded list of valid Student IDs. If unmatched, access is denied with a readable error code. | Backend – Auth Service / Database | `githubUsername`, `student.studentId` |
| 9 | If matched, a JWT session token is issued and role-based access control (RBAC) is enforced on all subsequent requests. | Backend – Auth Service / Frontend | `JWT`, `userId`, `role` |

---

## Process 2: Group Creation & Tool Integration

> **Source:** *Group Creation*, *JIRA/GitHub Integration* sections. This process is bounded by a schedule set by the Coordinator.

| **Step** | **Action** | **System Component** | **Required Data** |
| --- | --- | --- | --- |
| 1 | Student submits a group creation request within the active schedule window. The creator is automatically appointed as Team Leader. | Frontend – Group Creation Form / Backend | `studentId`, `groupName` |
| 2 | Team Leader searches for peers by Student ID and sends group invitations. | Frontend – Group Dashboard | `groupId`, `targetStudentId` |
| 3 | Invited students receive a notification. Upon acceptance, all other pending invitations for that student are automatically denied. | Frontend – Notification Panel / Backend | `invitationId`, `studentId`, `response: ACCEPTED` |
| 4 | Team Leader enters the JIRA Space URL and Project Key to bind the group's JIRA workspace. | Frontend – Integration Settings | `groupId`, `jiraSpaceUrl`, `jiraProjectKey` |
| 5 | Backend validates the JIRA credentials with a test call. Returns a readable error if invalid. | External Service – JIRA REST API | `jiraSpaceUrl`, `jiraProjectKey`, `jiraApiToken` |
| 6 | Team Leader enters the GitHub Organization name and a Personal Access Token (PAT) with `repo` scope. | Frontend – Integration Settings | `groupId`, `githubOrgName`, `githubPAT` |
| 7 | Backend validates the GitHub PAT with a test call. Returns a readable error if the token is invalid or lacks required scope. | External Service – GitHub REST API | `githubPAT`, `githubOrgName` |
| 8 | Credentials are encrypted and stored. Group status is updated to `TOOLS_BOUND`. | Database – Integration Store | `groupId`, `encryptedJiraToken`, `encryptedGithubPAT` |
| 9 | Coordinator can manually add or remove a student from/to any group at any time (override capability). | Frontend – Coordinator Admin Panel / Backend | `coordinatorId`, `groupId`, `studentId`, `action: ADD | REMOVE` |

---

## Process 3: Group–Advisor Association & Sanitization

> **Source:** *Group - Advisor Association*, *Sanitization* sections. This process is bounded by a schedule set by the Coordinator.

| **Step** | **Action** | **System Component** | **Required Data** |
| --- | --- | --- | --- |
| 1 | Team Leader browses available advisors and submits an Advisee Request. | Frontend – Advisor Discovery / Backend | `groupId`, `advisorId` |
| 2 | Team Leader may also withdraw a pending request before it is reviewed. | Frontend – Group Dashboard | `requestId`, `groupId` |
| 3 | Advisor receives a notification about the new request and reviews the group's profile. | Frontend – Advisor Notifications | `notificationId`, `groupId` |
| 4 | Advisor accepts or rejects the request. Upon acceptance, a formal Advisor–Group association is created. | Frontend – Advisor Dashboard / Backend | `requestId`, `decision: ACCEPTED | REJECTED` |
| 5 | If the advisor wishes to de-associate, they must explicitly **release** the group first. Only after release can the group submit a new advisee request. | Frontend – Advisor Dashboard | `groupId`, `advisorId` |
| 6 | The Coordinator can transfer a group from one advisor to another directly. | Frontend – Coordinator Admin Panel | `groupId`, `currentAdvisorId`, `newAdvisorId` |
| 7 | A scheduled daily background job checks whether the Advisor Association deadline has passed. | Backend – Scheduler / Cron Job | `deadline` |
| 8 | The job queries all groups in `UNADVISED` status that are past the deadline. | Backend – Sanitization Service / Database | `status: UNADVISED`, `deadline` |
| 9 | System automatically disbands all identified groups. Member slots are released and records are archived with reason `NO_ADVISOR`. | Backend – Sanitization Service / Database | `groupId`, `memberIds[]`, `disbandReason: NO_ADVISOR` |
| 10 | All former members receive a notification about the disbandment. | Backend – Notification Service | `studentId[]`, `disbandReason` |

---

## Process 4: Committee Assignment

> **Source:** *Committee Assignment* section. Requires Group Creation and Group–Advisor Association to be completed first.

| **Step** | **Action** | **System Component** | **Required Data** |
| --- | --- | --- | --- |
| 1 | Coordinator creates a new committee entity. | Frontend – Committee Management | `committeeName` |
| 2 | Coordinator assigns an advisor as the primary Advisor member of the committee. Each advisor is a member of a committee. | Frontend – Committee Assignment UI | `committeeId`, `advisorId`, `role: ADVISOR` |
| 3 | Coordinator optionally assigns additional professors as Jury members. | Frontend – Committee Assignment UI | `committeeId`, `professorId[]`, `role: JURY` |
| 4 | Coordinator assigns student groups to the committee. A group must be committee-assigned before it can submit a Proposal. | Frontend – Group Assignment Panel | `committeeId`, `groupId[]` |
| 5 | System validates assignment (no professor conflict; group not already assigned for same deliverable). | Backend – Committee Validation Service | `committeeId`, `professorId`, `groupId` |
| 6 | Assigned professors are notified of their committee membership and assigned groups. | Backend – Notification Service | `professorId[]`, `committeeId`, `assignedGroups[]` |
| 7 | Committee members can view their dashboard: assigned groups, evaluation schedule, and active rubric. Note: Advisors grade **their own groups and groups of other advisors** within the committee. | Frontend – Committee Dashboard | `professorId`, `committeeId` |

---

## Process 5: Sprint Tracking, Advisor Scrum Grading & AI Validation

> **Source:** *Scrum*, *JIRA/GitHub Integration*, *AI to Read Pull Requests (Difficulty 3)*, *AI to Validate Issue Implementation (Difficulty 4)* sections.

| **Step** | **Action** | **System Component** | **Required Data** |
| --- | --- | --- | --- |
| 1 | At the end of each sprint, a scheduled job triggers for all active groups with `TOOLS_BOUND` status. | Backend – Scheduler / Cron Job | `currentSprintId`, active `groupId[]` |
| 2 | System fetches all active stories in the sprint from the group's bound JIRA project. Fields extracted: Issue Key, Assignee, Story Points, Description. | External Service – JIRA REST API | `jiraProjectKey`, `sprintId`, `jiraApiToken` |
| 3 | For each JIRA issue, system searches the bound GitHub Organization for branches whose names begin with the Issue Key. | External Service – GitHub REST API | `githubOrgName`, `repoName`, `issueKey`, `githubPAT` |
| 4 | System retrieves the Pull Request associated with the matched branch and verifies whether it has been **merged** into the main branch. | External Service – GitHub REST API | `branchName`, `repoName` |
| 5 | **[AI – Difficulty 3]** AI engine fetches all PR review comments and validates whether a substantive code review has occurred (not trivial or empty). Result: PASS / WARN / FAIL. | Backend – AI Validation Service | `prId`, `reviewComments[]`, `reviewerGithubId` |
| 6 | **[AI – Difficulty 4]** AI engine fetches the file-level diff of the merged PR and compares it semantically to the JIRA issue description to detect unrelated code changes. Result: PASS / WARN / FAIL. | Backend – AI Validation Service | `prDiff` (file diffs), `issueDescription` |
| 7 | Validation results and completed story points are recorded per student per sprint in the database. | Database – Sprint Tracking Log | `studentId`, `issueKey`, `completedPoints`, `aiValidationResult`, `sprintId` |
| 8 | **[Advisor Action]** Advisor grades the team's **Scrum performance (Point A)** for the sprint using Soft Grading (A/B/C/D/F). | Frontend – Advisor Sprint Panel | `advisorId`, `groupId`, `sprintId`, `pointA_grade` |
| 9 | **[Advisor Action]** Advisor grades the team's **Work/Code Review quality (Point B)** for the sprint using Soft Grading (A/B/C/D/F). | Frontend – Advisor Sprint Panel | `advisorId`, `groupId`, `sprintId`, `pointB_grade` |
| 10 | Live sprint grades are immediately made visible on the Advisor's dashboard panel. | Frontend – Advisor Live Panel / Database | `groupId`, `sprintId`, `pointA_grade`, `pointB_grade` |

---

## Process 6: Deliverable Submission & Review

> **Source:** *Proposal Submission*, *Proposal Review*, *SoW Submission*, *Embedded Markdown Editor (Difficulty 2)* sections. All phases are bounded by schedules set by the Coordinator.

### Phase 6A — Submission

| **Step** | **Action** | **System Component** | **Required Data** |
| --- | --- | --- | --- |
| 1 | Team Leader opens the WYSIWYG Markdown editor for the active deliverable. System enforces that the group is assigned to a committee (required before submission). | Frontend – WYSIWYG Editor / Backend | `groupId`, `deliverableId`, `committeeId`, `deadline` |
| 2 | Team Leader authors the deliverable content using the WYSIWYG editor (Markdown with rich text and image support). Rubric criteria descriptions may be linked to specific sections of the document. | Frontend – WYSIWYG Editor | `markdownContent`, `embeddedImages[]`, `rubricSectionLinks[]` |
| 3 | Team Leader submits the deliverable. System timestamps the submission. If the deadline has passed, the system rejects the submission. Individual students cannot submit — only the Team Leader can. | Backend – Submission Service / Database | `groupId`, `deliverableId`, `markdownContent`, `submittedAt` |
| 4 | All committee members (Advisor + Jury) are notified that the submission is available for review. | Backend – Notification Service | `committeeId`, `professorIds[]`, `groupId` |

### Phase 6B — Review

| **Step** | **Action** | **System Component** | **Required Data** |
| --- | --- | --- | --- |
| 5 | Committee member opens the submitted document in a read-only view alongside the linked evaluation rubric. | Frontend – Review Panel | `submissionId`, `rubricId`, `reviewerId` |
| 6 | Committee member leaves inline comments or general feedback on the submission. | Frontend – Commenting UI / Backend | `submissionId`, `reviewerId`, `commentText` |
| 7 | If changes are requested, the group revises and re-submits a revised version before the grading deadline. | Frontend – WYSIWYG Editor / Backend | `submissionId`, `revisedMarkdownContent` |

---

## Process 7: Grading, Scalar & Final Grade Calculation

> **Source:** *Grading Reviewed Proposal Submission*, *Team and Individual Evaluation* sections.

### Phase 7A — Grading

| **Step** | **Action** | **System Component** | **Required Data** |
| --- | --- | --- | --- |
| 1 | Committee member grades each rubric criterion using the defined grading type: **Binary** (`S = 100`, `F = 0`) or **Soft** (`A = 100`, `B = 80`, `C = 60`, `D = 50`, `F = 0`). | Frontend – Rubric Grading Panel / Backend | `rubricId`, `criterionId[]`, `selectedGrade[]`, `reviewerId` |
| 2 | System aggregates all committee member scores to produce the **Base Deliverable Grade (B)**. | Backend – Grading Engine | `rubricScores[]`, `committeeId`, `submissionId` |

### Phase 7B — Scalar & Final Grade Calculation

> The following steps implement the Team Evaluation formula from the Project Definition.

| **Step** | **Action** | **System Component** | **Required Data** |
| --- | --- | --- | --- |
| 3 | For each deliverable, system identifies contributing sprints (configured in P0, Step 6). | Backend – Grading Engine / Database | `deliverableId`, `contributingSprintIds[]` |
| 4 | System computes the **Scrum Scalar** for the deliverable: average of Point A (Scrum) grades from contributing sprints, converted to a decimal. **`ScrumScalar = AVG(Point A grades) ÷ 100`** | Backend – Grading Engine | `pointA_grades[]`, `contributingSprintIds[]` |
| 5 | System computes the **Review Scalar** for the deliverable: average of Point B (Code Review) grades from contributing sprints, converted to a decimal. **`ReviewScalar = AVG(Point B grades) ÷ 100`** | Backend – Grading Engine | `pointB_grades[]`, `contributingSprintIds[]` |
| 6 | System computes the **Deliverable Scalar (DS)**: average of Scrum Scalar and Review Scalar. **`DS = AVG(ScrumScalar, ReviewScalar)`** | Backend – Grading Engine | `ScrumScalar`, `ReviewScalar` |
| 7 | System computes the **Scaled Deliverable Grade**: **`ScaledGrade = B × DS`** | Backend – Grading Engine | `baseGrade (B)`, `deliverableScalar (DS)` |
| 8 | System computes the **Weighted Total Grade** across all deliverables using Coordinator-configured weights: **`WeightedTotal = Σ (ScaledGrade × DeliverableWeight)`** | Backend – Grading Engine | `scaledGrades[]`, `deliverableWeights[]` |
| 9 | System retrieves each student's **Individual Completion Ratio (Cᵢ)**: ratio of story points completed vs. target. **`Cᵢ = completedStoryPoints ÷ targetStoryPoints`** | Backend – Grading Engine / Database | `studentId`, `completedPoints`, `targetPoints` |
| 10 | System computes each student's **Individual Final Grade**: **`Gᵢ = WeightedTotal × Cᵢ`** | Backend – Grading Engine | `WeightedTotal`, `Cᵢ`, `studentId` |
| 11 | All final individual grades are persisted and made visible on the Advisor's dashboard. | Database – Final Grades Store / Frontend – Advisor Dashboard | `studentId`, `deliverableId`, `Gᵢ`, `WeightedTotal` |

---
