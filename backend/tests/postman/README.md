# Coordinator QA Tests

## Description
Postman collection for testing Coordinator setup flow.

## Covered Scenarios

### Student Upload
- Unauthorized (403)
- Success (201)
- Duplicate IDs (400)
- Invalid Format (400)
- Empty List (400)
- Existing ID (400)

### Deliverable
- Unauthorized (403)
- Success (201)
- Reversed Deadlines (400)
- Missing Field (400)

## Limitations
Rubric and deliverable weight endpoints were not available in the current backend implementation during testing, so they are not included in this collection.