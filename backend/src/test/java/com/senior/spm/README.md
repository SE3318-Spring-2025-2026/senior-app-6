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
