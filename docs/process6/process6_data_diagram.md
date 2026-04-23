# Level 1 Data Flow Diagram (DFD) 
## Process 6: Deliverable Submission & Review

This document outlines the nodes, actors, data stores, and data flows needed to draw the Level 1 DFD for the deliverable submission and review lifecycle.

---

### 1. External Entities (Actors)
* **E1: Team Leader** (The student responsible for authoring and submitting the group's document).
* **E2: Committee Member** (The Advisor or Jury evaluating the submission).

### 2. Data Stores
* **D1: Deliverables & Rubrics** (Configured in Process 0; read-only here to enforce deadlines and active rubrics).
* **D2: Committees & Groups** (Configured in Process 4; read-only here to enforce RBAC and data isolation).
* **D3: Deliverable Submissions** (Stores the actual markdown content, timestamps, and revision history).
* **D4: Rubric Mappings** (Stores the links between specific text sections and rubric criteria).
* **D5: Submission Comments** (Stores the feedback left by the committee).

---

### 3. Processes (Level 1 Nodes) & Data Flows

#### **6.1 Submit Initial Deliverable**
* **Description:** The Team Leader writes the document, links rubric criteria, and submits before the deadline.
* **Data In:** 
  * From **E1 (Team Leader)**: `markdownContent`, `embeddedImages[]`, `rubricSectionLinks[]`, `groupId`, `deliverableId`.
  * From **D1 (Deliverables)**: `submissionDeadline` (for validation).
  * From **D2 (Committees)**: `committeeId` (to verify the group is assigned).
* **Data Out:** 
  * To **D3 (Submissions)**: New `DeliverableSubmission` record (`submissionId`, `submittedAt`).
  * To **D4 (Mappings)**: New `RubricMapping` records.
  * To **Process 6.2**: Trigger event `SubmissionCreatedEvent`.

#### **6.2 Dispatch Committee Notification**
* **Description:** The system automatically alerts the assigned committee that a document is ready.
* **Data In:**
  * From **Process 6.1**: `SubmissionCreatedEvent` (`submissionId`, `groupId`).
  * From **D2 (Committees)**: `professorId[]` (Advisor and Jury members for that group).
* **Data Out:**
  * To **E2 (Committee Member)**: `NotificationPayload` ("New submission ready for review").

#### **6.3 Retrieve Submission for Review**
* **Description:** The Committee Member opens the read-only split-panel to view the document and the rubric.
* **Data In:**
  * From **E2 (Committee Member)**: `GET` request with `submissionId`, `reviewerId`.
  * From **D3 (Submissions)**: `markdownContent`.
  * From **D4 (Mappings)**: `rubricSectionLinks[]`.
  * From **D1 (Deliverables)**: Evaluation Rubric structure.
* **Data Out:**
  * To **E2 (Committee Member)**: `SubmissionDetailDTO` (Compiled document and rubric UI).

#### **6.4 Post Submission Feedback**
* **Description:** Committee members leave inline or general comments requesting changes or providing feedback.
* **Data In:**
  * From **E2 (Committee Member)**: `commentText`, `submissionId`, `reviewerId`.
* **Data Out:**
  * To **D5 (Comments)**: New `SubmissionComment` record.
  * To **E1 (Team Leader)**: (Optional) Feedback visibility when viewing the submission.

#### **6.5 Submit Document Revision**
* **Description:** The Team Leader updates the submission based on feedback before the final review deadline.
* **Data In:**
  * From **E1 (Team Leader)**: `revisedMarkdownContent`, `submissionId`.
* **Data Out:**
  * To **D3 (Submissions)**: Updated `DeliverableSubmission` record (`updatedAt`).