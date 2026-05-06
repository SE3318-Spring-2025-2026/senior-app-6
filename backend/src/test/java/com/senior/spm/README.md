# Coordinator Student Upload Integration Tests

## Description

This integration test class verifies the student upload functionality of the Coordinator setup flow at both API and database level.

## Implemented Test Cases

### 1. Successful Student Upload

Verifies that a Coordinator can upload a valid list of student IDs and receive a `201 Created` response. It also checks that the uploaded students are actually persisted in the database and that the correct number of records is inserted.

### 2. Invalid Student ID Format

Verifies that student upload is rejected with `400 Bad Request` when at least one student ID does not match the required 11-digit numeric format.

### 3. Duplicate Student IDs

Verifies that student upload is rejected with `400 Bad Request` when duplicate student IDs are included in the same request. It also checks that no student record is inserted into the database in this case.

### 4. Empty Student List

Verifies that student upload is rejected with `400 Bad Request` when the request contains an empty student ID list.

## Technical Notes

* Tests are implemented using `SpringBootTest`, `MockMvc`, and `WithMockUser`.
* `WithMockUser(roles = "COORDINATOR")` is used to simulate an authenticated Coordinator user.
* `StudentRepository` is used to verify database state where API response alone is not sufficient.
* The database is cleaned before each test to ensure test isolation and avoid inter-test dependency.

## Purpose

These integration tests were added because the student upload endpoint does not return the inserted count in its response body. Therefore, database-level verification was necessary to fully validate the requirement.

---

## Submission Integration Tests

### Description

This integration test class verifies submission rules and role-based access control at both API and database level.

### Implemented Test Cases

1. Valid submission → 201 Created  
2. Expired deadline → 400 Bad Request  
3. Missing committee assignment → 400 Bad Request  
4. Admin submission attempt → 403 Forbidden  
5. Member submission/update attempt → 403 Forbidden  
6. Unauthorized professor access/comment → 403 Forbidden  
7. Assigned professor access → 200 OK  

### Technical Notes

* Tests are implemented using `SpringBootTest` and `MockMvc`.
* `JWTService` is used to generate role-based tokens (student, admin, professor).
* Seed data is provided via `data.sql` to ensure deterministic test scenarios.
* Submission records are cleaned before each test to ensure isolation.

### Purpose

These tests ensure that submission validation rules (deadline and committee assignment) and RBAC constraints are correctly enforced according to system requirements.

---

## Other Test Classes

### Config
| Class | Summary |
|---|---|
| `GlobalExceptionHandlerTest` | Exception-to-HTTP-status mappings |
| `RestTemplateConfigTest` | RestTemplate bean configuration |

### Entity
| Class | Summary |
|---|---|
| `EntityAnnotationTest` | JPA annotations and constraints on all entities |

### Repository
| Class | Summary |
|---|---|
| `AdvisorRequestRepositoryTest` | Custom queries on `AdvisorRequestRepository` |
| `GroupInvitationRepositoryTest` | Custom queries on `GroupInvitationRepository` |
| `GroupMembershipRepositoryTest` | Custom queries on `GroupMembershipRepository` |
| `ProjectGroupRepositoryTest` | Custom queries on `ProjectGroupRepository` |

### Service
| Class | Summary |
|---|---|
| `AdvisorServiceTest` | Core advisor assignment and capacity logic |
| `AdvisorServiceBrowseRequestTest` | Advisor list and request-browsing scenarios |
| `EncryptionServiceTest` | AES-256 encrypt/decrypt correctness |
| `GitHubValidationServiceTest` | GitHub PAT validation (mocked) |
| `GitHubValidationWireMockTest` | GitHub PAT validation against WireMock stub |
| `GroupServiceTest` | Group creation, disband, and membership rules |
| `InvitationServiceTest` | Invitation send, accept, and deny flows |
| `InvitationServiceEdgeCaseTest` | Edge cases: capacity, roster-lock, status guards |
| `InvitationServicePersistenceTest` | Database-level state after invitation operations |
| `InvitationConcurrencyTest` | Race-condition safety on concurrent invitation accepts |
| `InvitationLifecycleQaIntegrationTest` | Full invitation lifecycle end-to-end |
| `JiraValidationServiceTest` | Jira PAT validation (mocked) |
| `JiraValidationWireMockTest` | Jira PAT validation against WireMock stub |
| `SanitizationServiceTest` | Scheduled sanitization job and optimistic-lock handling |
| `ScrumGradingServiceTest` | Grade submit/upsert, ownership guard, tracking aggregation, AI priority logic |
| `TermConfigServiceTest` | Active term ID and max team size config reads |

### Controller / API
| Class | Summary |
|---|---|
| `AdvisorControllerIntegrationTest` | Advisor endpoints — happy path and error flows |
| `CommitteeControllerIntegrationTest` | Committee professor assignment validation, advisor rules, conflict checks, and persistence flows |
| `CommitteeGroupAssignmentIntegrationTest` | Committee group assignment validation and batch binding flows |
| `CoordinatorGroupOverrideIntegrationTest` | Coordinator force-add and advisor override scenarios |
| `CoordinatorControllerIntegrationTest` | Coordinator setup flow and student upload scenarios |
| `GroupCreationIntegrationTest` | Group creation and tool-binding flows |
| `InvitationControllerIntegrationTest` | Invitation API at HTTP level |
| `InvitationControllerSecurityTest` | RBAC enforcement on invitation endpoints |
| `CapacityGuardSanitizationIntegrationTest` | Capacity guard and sanitization race conditions |
| `P3AdvisorLifecycleIntegrationTest` | Full P3 advisor request lifecycle via API |
| `P3RbacSecurityTest` | Role-based access control for all P3 endpoints |
| `SubmissionControllerIntegrationTest` | Submission validation (deadline, committee assignment) and RBAC enforcement for Issues #203 and #204 |
| `ScrumGradingControllerTest` | Grade submit (201/200 upsert), wrong-advisor 403, invalid enum 400, GET 404 when no grade |
| `P5SecurityTest` | URL-pattern guards for `/api/sprints/**`, `/api/advisor/sprints/**`, `/api/groups/*/sprints/**` |

### DTO
| Class | Summary |
|---|---|
| `InvitationResponseSerializationTest` | JSON serialization of invitation response DTOs |

---

## Postman / E2E Tests

Collections are located in `tests/postman/`. Set `baseUrl = http://localhost:8080` and run via Postman Runner.

| Collection | Summary |
|---|---|
| `Coordinator QA Tests` | Auth, student upload, deliverable management, rubric |
| `Coordinator QA Tests - Part 2 (Sprints & Publish)` | Sprint creation, sprint mapping, publish flow |
| `auth-and-rbac-tests` | Login and RBAC enforcement across all roles |
| `P2-P3-GroupAdvisor` | Group formation, invitations, tool binding, advisor request lifecycle |
| `P5-SprintTracking` | Sprint tracking pipeline, LLM grading dashboard, scrum grade submit/get, RBAC |