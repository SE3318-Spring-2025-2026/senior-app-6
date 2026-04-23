# Process 6: Data Flow Diagram (Simplified) - Draw.io Template

This is a simplified Level 1 DFD that groups individual API endpoints into high-level business processes to keep the diagram clean and easy to read.

## 1. Nodes (Shapes to create)

### External Entities (Draw as Squares)
- **E1**: Team Leader (Student)
- **E2**: Committee Member (Professor)

### Processes (Draw as Circles or Rounded Rectangles)
- **P1: Manage Deliverable Submission** (Groups the dashboard, submission, and mapping APIs)
- **P2: Dispatch Notifications** (Background event publisher)
- **P3: Review & Feedback** (Groups the read-only view and commenting APIs)

### Data Stores (Draw as Open-ended Rectangles or Database Cylinders)
- **D1**: `Deliverables_Config` (Read-only rules)
- **D2**: `Committee_Groups` (Read-only RBAC)
- **D3**: `DeliverableSubmission` (Docs & Revisions)
- **D4**: `RubricMapping` (Text-to-Rubric Links)
- **D5**: `SubmissionComment` (Professor Feedback)

---

## 2. Connection List (Edges/Arrows)

Draw directional arrows from the `Source` to the `Target` and label the arrow with the `Data Flow`.

### 1. The Submission Flow (Team Leader -> System -> DB)
| Source Node | Target Node | Data Flow (Arrow Label) |
| :--- | :--- | :--- |
| E1 (Team Leader) | P1 (Manage Submission) | Markdown Document & Mapping Data |
| D1 (`Deliverables_Config`) | P1 (Manage Submission) | Deadline Rules & Rubric Info |
| D2 (`Committee_Groups`) | P1 (Manage Submission) | Group Validation Info |
| P1 (Manage Submission) | D3 (`DeliverableSubmission`) | Document Content |
| P1 (Manage Submission) | D4 (`RubricMapping`) | Save Rubric Links |

### 2. The Notification Flow
| Source Node | Target Node | Data Flow (Arrow Label) |
| :--- | :--- | :--- |
| P1 (Manage Submission) | P2 (Dispatch Notifications) | Submission Event Data |
| P2 (Dispatch Notifications) | E2 (Committee Member) | "New Submission" Alert |

### 3. The Review Flow (Committee -> System -> DB -> Student)
| Source Node | Target Node | Data Flow (Arrow Label) |
| :--- | :--- | :--- |
| E2 (Committee Member) | P3 (Review & Feedback) | Request Submission & Submit Comments |
| D2 (`Committee_Groups`) | P3 (Review & Feedback) | RBAC Permissions |
| D3 (`DeliverableSubmission`) | P3 (Review & Feedback) | Load Document Content |
| D4 (`RubricMapping`) | P3 (Review & Feedback) | Load Mapped Highlights |
| D1 (`Deliverables_Config`) | P3 (Review & Feedback) | Load Evaluation Rubric |
| P3 (Review & Feedback) | D5 (`SubmissionComment`) | Save Feedback |
| D5 (`SubmissionComment`) | P3 (Review & Feedback) | Load Feedback Thread |
| P3 (Review & Feedback) | E1 (Team Leader) | Display Feedback to Student |