# Process 6: Deliverable Submission Database Schema Implementation

## Overview
This document summarizes the implementation of the database schema and JPA repositories for Process 6: Deliverable Submission & Review lifecycle.

## Implemented Entities

### 1. DeliverableSubmission
**Table:** `deliverable_submission`

**Purpose:** Stores the submitted deliverable documents (markdown content) for groups with submission tracking.

**Fields:**
- `id` (UUID, PK) - Primary key
- `group_id` (UUID, FK) - Foreign key to `project_group` (NOT NULL)
- `deliverable_id` (UUID, FK) - Foreign key to `deliverable` (NOT NULL)
- `markdown_content` (LONGTEXT) - The actual markdown document content (NOT NULL)
- `submitted_at` (DATETIME) - Timestamp when the submission was created (NOT NULL)
- `updated_at` (DATETIME) - Timestamp when the submission was last updated (nullable)
- `is_revision` (BOOLEAN) - Flag indicating if this is a revised submission (default: false)
- `revision_number` (INT) - Tracks the revision count (default: 0)

**Relationships:**
- Many-to-One to `ProjectGroup` (group_id)
- Many-to-One to `Deliverable` (deliverable_id)
- One-to-Many to `RubricMapping` (mappedBy: submission)
- One-to-Many to `SubmissionComment` (mappedBy: submission)

**Foreign Key Constraints:**
- FK: `fk_ds_group` → `project_group.id`
- FK: `fk_ds_deliverable` → `deliverable.id`

---

### 2. RubricMapping
**Table:** `rubric_mapping`

**Purpose:** Maps sections of a submission to specific rubric criteria, enabling the committee to link evaluation criteria with document sections.

**Fields:**
- `id` (UUID, PK) - Primary key
- `submission_id` (UUID, FK) - Foreign key to `deliverable_submission` (NOT NULL)
- `rubric_criterion_id` (UUID, FK) - Foreign key to `rubricCriterion` (NOT NULL)
- `section_start` (INT) - Start position in the markdown content (NOT NULL)
- `section_end` (INT) - End position in the markdown content (NOT NULL)
- `mapped_at` (DATETIME) - Timestamp when the mapping was created (NOT NULL)

**Relationships:**
- Many-to-One to `DeliverableSubmission` (submission_id)
- Many-to-One to `RubricCriterion` (rubric_criterion_id)

**Foreign Key Constraints:**
- FK: `fk_rm_submission` → `deliverable_submission.id`
- FK: `fk_rm_rubric_criterion` → `rubricCriterion.id`

---

### 3. SubmissionComment
**Table:** `submission_comment`

**Purpose:** Stores feedback and comments left by committee members (Advisors and Jury) on submissions.

**Fields:**
- `id` (UUID, PK) - Primary key
- `submission_id` (UUID, FK) - Foreign key to `deliverable_submission` (NOT NULL)
- `commenter_id` (UUID, FK) - Foreign key to `staffUser` (NOT NULL)
- `comment_text` (LONGTEXT) - The comment content (NOT NULL)
- `created_at` (DATETIME) - Timestamp when the comment was created (NOT NULL)

**Relationships:**
- Many-to-One to `DeliverableSubmission` (submission_id)
- Many-to-One to `StaffUser` (commenter_id)

**Foreign Key Constraints:**
- FK: `fk_sc_submission` → `deliverable_submission.id`
- FK: `fk_sc_commenter` → `staffUser.id`

---

## Implemented Repositories

### 1. DeliverableSubmissionRepository
**Location:** `backend/src/main/java/com/senior/spm/repository/DeliverableSubmissionRepository.java`

**Methods:**
- `findByGroup(ProjectGroup)` - Find all submissions for a group
- `findByDeliverable(Deliverable)` - Find all submissions for a deliverable
- `findFirstByGroupAndDeliverableOrderBySubmittedAtDesc(ProjectGroup, Deliverable)` - Find latest submission
- `existsByGroupAndDeliverable(ProjectGroup, Deliverable)` - Check if submission exists

### 2. RubricMappingRepository
**Location:** `backend/src/main/java/com/senior/spm/repository/RubricMappingRepository.java`

**Methods:**
- `findBySubmission(DeliverableSubmission)` - Find all mappings for a submission
- `findByRubricCriterion(RubricCriterion)` - Find all mappings for a criterion
- `findBySubmissionAndRubricCriterion(...)` - Find mapping for submission & criterion
- `existsBySubmissionAndRubricCriterion(...)` - Check if mapping exists

### 3. SubmissionCommentRepository
**Location:** `backend/src/main/java/com/senior/spm/repository/SubmissionCommentRepository.java`

**Methods:**
- `findBySubmission(DeliverableSubmission)` - Find all comments on a submission
- `findByCommenter(StaffUser)` - Find all comments by a commenter
- `findBySubmissionAndCommenter(...)` - Find comments by a user on a submission
- `countBySubmission(DeliverableSubmission)` - Count comments on a submission

---

## Database Schema Auto-Creation

The Hibernate DDL configuration is set to `update` in `application.properties`:
```properties
spring.jpa.hibernate.ddl-auto=update
```

**Impact:** 
- Tables are automatically created on application startup if they don't exist
- Existing columns/constraints are preserved
- New columns/constraints are added as needed
- No manual SQL migration scripts are required

---

## Foreign Key Relationships Diagram

```
DeliverableSubmission
├── group_id (FK) ──→ ProjectGroup.id
├── deliverable_id (FK) ──→ Deliverable.id
├── rubricMappings (1-to-Many) ──→ RubricMapping.submission_id
└── comments (1-to-Many) ──→ SubmissionComment.submission_id

RubricMapping
├── submission_id (FK) ──→ DeliverableSubmission.id
└── rubric_criterion_id (FK) ──→ RubricCriterion.id

SubmissionComment
├── submission_id (FK) ──→ DeliverableSubmission.id
└── commenter_id (FK) ──→ StaffUser.id
```

---

## Acceptance Criteria Status

✅ **Tables are auto-created by Hibernate on startup with all specified columns and constraints.**
- Confirmed: All three tables will be created automatically on Spring Boot startup
- Hibernate DDL mode: `update`
- All columns are properly annotated with `@Column` specifying nullability

✅ **Proper foreign key relationships exist**
- ✅ submission → group: `fk_ds_group` (NOT NULL)
- ✅ submission → deliverable: `fk_ds_deliverable` (NOT NULL)
- ✅ mapping → submission: `fk_rm_submission` (NOT NULL)
- ✅ mapping → rubric_criterion: `fk_rm_rubric_criterion` (NOT NULL)
- ✅ comment → submission: `fk_sc_submission` (NOT NULL)
- ✅ comment → commenter: `fk_sc_commenter` (NOT NULL)

---

## Testing

All entity annotation tests pass (27/27):
- ✅ Verified foreign key column names
- ✅ Verified nullability constraints
- ✅ Verified entity-to-table mappings
- ✅ Verified relationship annotations

**Test Command:**
```bash
mvn test -Dtest=EntityAnnotationTest
```

---

## Build Status

✅ **Compilation:** All 155 source files compile successfully
```bash
mvn clean compile -DskipTests
# BUILD SUCCESS
```

---

## Files Created

1. **Entities:**
   - `backend/src/main/java/com/senior/spm/entity/DeliverableSubmission.java`
   - `backend/src/main/java/com/senior/spm/entity/RubricMapping.java`
   - `backend/src/main/java/com/senior/spm/entity/SubmissionComment.java`

2. **Repositories:**
   - `backend/src/main/java/com/senior/spm/repository/DeliverableSubmissionRepository.java`
   - `backend/src/main/java/com/senior/spm/repository/RubricMappingRepository.java`
   - `backend/src/main/java/com/senior/spm/repository/SubmissionCommentRepository.java`

3. **Tests:**
   - Updated: `backend/src/test/java/com/senior/spm/entity/EntityAnnotationTest.java`
     - Added 9 new test methods for the three entities

---

## Next Steps

The following endpoints and services can now be implemented:

1. POST `/api/deliverables/{id}/submissions` - Submit initial deliverable
2. PUT `/api/submissions/{id}` - Update submission (revisions)
3. GET `/api/submissions/{id}` - Retrieve submission for review
4. POST `/api/submissions/{id}/rubric-mappings` - Link rubric sections
5. POST `/api/submissions/{id}/comments` - Post feedback comments
6. GET `/api/submissions/{id}/comments` - Retrieve all comments
7. Deliverable Submission Notification Service

---

## References

- README.md - Process 6
- docs/process6/process6_data_diagram.md
- docs/process6/Issues6.md
- Entity Relationship Diagram: docs/shared-p2-p3/er_p2_p3.md
