# Contribution Guidelines

This document covers everything you need to know to contribute to the Senior Project Management (SPM) platform — from setting up your environment to getting a PR merged.

---

## Getting Started

Before writing any code, make sure your local environment is running correctly.

> See **[`deploy.md`](./deploy.md)** for the full local setup guide, including the required tech stack, database setup, backend and frontend run instructions.


---

## The Default Rule: Open a Pull Request

**If you implemented something, open a PR. Full stop.**

Every issue, feature, fix, and refactor goes through a pull request — not directly to `main`.

Direct pushes to `main` are the **exception**. See Section 1 for the narrow cases where it is allowed.

---

## 1. Branch & Push Policy

### Pull Request Required

A pull request is **mandatory** for:

- Any implementation tied to a GitHub issue
- New endpoints, services, entities, or repository methods
- Changes to existing business logic or validation rules
- Database schema changes (new columns, tables, constraints)
- Any modification to `SecurityConfig`, `GlobalExceptionHandler`, or `application.properties`
- Frontend page or component additions
- Anything that touches code owned by another team member


---

## 2. Pull Request Rules

### Opening a PR

- Link the PR to its issue: `Resolves #<issue-number>` in the PR body.
- Write a short description of what was implemented and any notable decisions made.
- Branch naming: `feature/issue-<number>-<short-description>`
- Request reviews from at least 3 teammates when you open the PR.

### Review Requirements

- **Authors must not approve their own pull requests** — no exceptions.
- Any team member may review and leave comments.
- **A PR requires 3 approving reviews before it can be merged.**
- After 3 approvals and zero unresolved comments, only the team lead performs the merge.

### Review Criteria

Reviewers must check all of the following before approving:

| Area | What to verify |
|------|---------------|
| **Correctness** | Does the implementation do what the linked issue describes? |
| **API contract** | Does it match `docs/openapi.yaml` — paths, methods, request/response shapes, status codes? |
| **Business rules** | Does it follow the project's established architectural decisions? No undocumented deviations. |
| **Security** | Is the endpoint's role guard wired in `SecurityConfig`? No plaintext secrets in code or logs. |
| **Error handling** | Are failure cases handled explicitly? No silent exception swallowing. |
| **Tests** | Are there tests for new service methods and endpoints? Do existing tests still pass? |
| **Build** | Does the PR compile cleanly? No introduced compile errors or broken imports. |

### Review Etiquette

- Leave specific, actionable comments
- Mark suggestions clearly: `nit:` or `suggestion:` for non-blocking feedback.
- The author is responsible for responding to all comments before requesting re-review.
- Resolved threads should be marked resolved by the reviewer who raised them, not the author.

---

## 3. Commit Message Convention

| Prefix | When to use |
|--------|-------------|
| `feat:` | New feature or endpoint |
| `fix:` | Bug fix |
| `refactor:` | Code restructure with no behavior change |
| `chore:` | Config, dependency, or tooling change |
| `docs:` | Documentation only |
| `test:` | Tests only |
| `QA:` | Postman collections, test data, QA scripts |

Example: `feat: implement group invitation endpoint (#42)`

---

## 4. Code Quality Expectations

- Every new service method must handle failure cases explicitly — no silent exception swallowing.
- Established architectural decisions are finalized. Do not deviate without consulting the team lead first.
- Do not add endpoints to controllers without a corresponding entry in `docs/openapi.yaml`.
- Do not introduce new dependencies without team lead approval.


---

## 5. What Blocks a Merge

A PR will not be merged if any of the following are true:

- Fewer than 3 approving reviews
- Any unresolved review comments
- The author has not responded to feedback
- The implementation deviates from `docs/openapi.yaml` without justification
- The PR breaks the build or introduces a compile error
- `SecurityConfig` not updated for newly added protected endpoints

---

