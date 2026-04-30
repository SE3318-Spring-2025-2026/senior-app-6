Coordinator QA Tests
Description

This Postman collection is designed to test the Coordinator workflow, including student upload, deliverable management, sprint creation, and publish operations.

The tests validate API behavior under different scenarios, including authorization, input validation, and business logic rules.

Test Strategy
Postman Tests → End-to-end API validation
Focus areas:
HTTP status codes
Authorization (RBAC)
Input validation
Business logic
Covered Scenarios
1. Authentication
Login – Coordinator
Login – Admin
2. Student Upload
Unauthenticated (401)
Forbidden (403)
Success (201)
Duplicate IDs (400)
Invalid Format (400)
Empty List (400)
Existing ID (400)
3. Deliverable Management
Unauthorized (403)
Success (201)
Reversed Deadlines (400)
Missing Field (400)
4. Deliverable Weight
Unauthorized (403)
Success (200)
Invalid ID (404)
5. Rubric
Unauthorized (403)
Success (201)
Invalid Deliverable ID (404)
Invalid Grading Type
6. Sprint & Publish Flow

Sprint
Unauthorized (403)
Success (201)
End Before Start (400)
Sprint Mapping
Unauthorized (403)
Success (201)
Exceeds 100% (400)

Publish
Unauthorized (403)
Success (200)
Incomplete Config (400)
Missing Students (400)
Missing Sprints (400)
Missing Deliverables (400)

Key Validations
 Correct HTTP status codes are returned
 Role-based access control (RBAC) is enforced
 Input validation (format, duplicates, empty inputs)
 Business rules are properly validated (deadlines, mapping limits, publish requirements)

Notes
Tests cover both happy path and error scenarios
A real coordinator workflow is simulated end-to-end
All tests were successfully executed and passed

QA Summary
Total scenarios tested: 20+
Coverage: Auth, Student Upload, Deliverable, Sprint, Publish
All tests passed successfully
API behavior validated as expected

## How to Use

Import the following Postman collections:

1. coordinator-qa-tests.postman_collection.json
2. coordinator-qa-tests-part2.postman_collection.json

Set environment variable:
baseUrl = http://localhost:8080

Run both collections using Postman Runner.

---

## P2/P3 — Group & Advisor  

Collection: `P2-P3-GroupAdvisor_postman_collection.json`

Covers the full P2/P3 flow end-to-end: group creation, invitations (cancel + accept paths), tool binding (JIRA/GitHub), coordinator member overrides, advisor request lifecycle (send, accept, reject, cancel), coordinator advisor override, sanitization trigger, and RBAC negative tests.

Required env vars: `baseUrl`, `coordinatorMail`, `coordinatorPassword`, `professorMail`, `professorPassword`, `studentToken`, `student2Token`, `student1StudentNumber`, `student2StudentNumber`, `jiraSpaceUrl`, `jiraProjectKey`, `jiraApiToken`, `githubOrgName`, `githubPat`

---

## P5 — Sprint Tracking  Tests

Collection: `P5-SprintTracking_postman_collection.json`

Covers P5 sprint tracking pipeline: admin LLM config, sprint discovery (student + advisor + coordinator views), coordinator sprint refresh (force/no-force, 404), sprint overview, advisor group summaries, per-group tracking detail (advisor + student views), scrum grade submit/upsert/get, validation errors, and RBAC guards (403/401).

Required env vars: `baseUrl`, `coordinatorMail`, `coordinatorPassword`, `professorMail`, `professorPassword`, `adminMail`, `adminPassword`, `studentToken`, `llmApiKey`, `sprintId`, `advisorGroupId`

Run with Newman:
```
newman run P5-SprintTracking_postman_collection.json -e SPM-Local.postman_environment.json
```