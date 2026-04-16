# Integration Tests

## CoordinatorControllerIntegrationTest

Verifies the student upload functionality of the Coordinator setup flow at both API and database level.

### Test Cases

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Valid student ID list | 201 Created + records persisted in DB |
| 2 | Invalid ID format (< 11 digits) | 400 Bad Request |
| 3 | Duplicate IDs in same request | 400 Bad Request + no records inserted |
| 4 | Empty student list | 400 Bad Request |

### Technical Notes

* Uses `SpringBootTest`, `MockMvc`, and `WithMockUser(roles = "COORDINATOR")`.
* `StudentRepository` is used for DB-level verification since the endpoint does not return the inserted count.
* Database is cleaned before each test for isolation.

---

## AdvisorControllerIntegrationTest

Black-box integration tests for the professor-facing advisor request endpoints (P3-API-02 & P3-API-03).
All 20 tests pass green against the full Spring Boot context and H2 in-memory DB.

### Endpoints Covered

* `GET  /api/advisor/requests`
* `GET  /api/advisor/requests/{requestId}`
* `PATCH /api/advisor/requests/{requestId}/respond`

### Test Cases

#### GET /api/advisor/requests

| Order | Scenario | Expected |
|-------|----------|----------|
| 1 | Professor has PENDING requests | 200 + array with requestId, groupId, groupName, termId, memberCount, sentAt |
| 2 | Professor has no pending requests | 200 + empty array (not 404) |
| 3 | No token | 4xx |
| 4 | Student JWT | 403 |
| 5 | Coordinator JWT | 403 |

#### GET /api/advisor/requests/{requestId}

| Order | Scenario | Expected |
|-------|----------|----------|
| 6 | Professor fetches own request | 200 + {requestId, group:{id, groupName, termId, status, members:[...]}, sentAt} |
| 7 | Professor fetches another professor's request | 403 |
| 8 | Request does not exist | 404 |
| 9 | No token | 4xx |

#### PATCH /api/advisor/requests/{requestId}/respond

| Order | Scenario | Expected |
|-------|----------|----------|
| 10 | accept: true, professor under capacity | 200 + status: ACCEPTED + DB: group → ADVISOR_ASSIGNED, advisor set |
| 11 | accept: false | 200 + status: REJECTED + DB: group stays TOOLS_BOUND, no advisor set |
| 12 | Non-existent request | 404 |
| 13 | Professor responds to another professor's request | 403 |
| 14 | Request already REJECTED | 400 |
| 15 | Request already ACCEPTED | 400 |
| 16 | Professor at capacity (advisorCapacity=1, 1 group already assigned) | 400 + group stays TOOLS_BOUND |
| 17 | accept: true, second PENDING request exists for same group | 200 ACCEPTED + DB: other request → AUTO_REJECTED |
| 18 | accept: false, second PENDING request exists for same group | 200 REJECTED + DB: other request stays PENDING |
| 19 | No token | 4xx |
| 20 | Student JWT | 403 |

### Technical Notes

* Uses `SpringBootTest`, `MockMvc`, and real JWT tokens issued via `JWTService`.
* Repositories are used directly for DB-level assertions on state transitions (group status, advisor assignment, cascade AUTO_REJECT).
* Database is cleaned before each test via `@BeforeEach` to ensure full isolation.
* Runs against H2 in-memory DB via `@TestPropertySource(locations = "classpath:test.properties")`.

---

## CoordinatorGroupOverrideIntegrationTest

Black-box integration tests for coordinator group override operations (DFD 2.6).
All 15 tests pass green against the full Spring Boot context and H2 in-memory DB.

### Endpoints Covered

* `PATCH /api/coordinator/groups/{groupId}/members` (ADD / REMOVE)
* `PATCH /api/coordinator/groups/{groupId}/disband`

### Test Cases

#### PATCH /api/coordinator/groups/{groupId}/members — ADD

| Order | Scenario | Expected |
|-------|----------|----------|
| 1 | Valid student force-added | 200 + DB: membership count +1 |
| 2 | Force-add auto-denies competing invitations | 200 + DB: all PENDING invitations for that student → AUTO_DENIED |
| 3 | Group at max capacity (members + pending invitations ≥ maxTeamSize) | 400 |
| 4 | Student already in a group | 400 |

#### PATCH /api/coordinator/groups/{groupId}/members — REMOVE

| Order | Scenario | Expected |
|-------|----------|----------|
| 5 | Regular MEMBER force-removed | 200 + DB: membership count -1 |
| 6 | TEAM_LEADER removal blocked | 400 + DB: count unchanged |
| 7 | Student not in group | 404 |

#### PATCH /api/coordinator/groups/{groupId}/disband

| Order | Scenario | Expected |
|-------|----------|----------|
| 8 | Successful disband | 200 + DB: status=DISBANDED, memberships hard-deleted, outbound invitations AUTO_DENIED |
| 9 | Already disbanded | 400 |
| 10 | Non-existent group | 404 |

#### RBAC — Non-Coordinator roles

| Order | Scenario | Expected |
|-------|----------|----------|
| 11 | Professor token on members endpoint | 403 |
| 12 | Admin token on members endpoint | 403 |
| 13 | No token on members endpoint | 4xx |
| 14 | Professor token on disband | 403 |
| 15 | Admin token on disband | 403 |

### Technical Notes

* Uses `SpringBootTest`, `MockMvc`, and real JWT tokens issued via `JWTService`.
* Repositories are used directly for DB-level assertions (membership counts, invitation statuses, group status).
* Database is cleaned before each test via `@BeforeEach` to ensure full isolation.
* Runs against H2 in-memory DB via `@TestPropertySource(locations = "classpath:test.properties")`.
