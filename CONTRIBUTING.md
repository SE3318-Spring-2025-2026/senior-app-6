# Red Team — Contribution Guidelines

This document defines the workflow rules for all contributions to the Red Team's scope of the Senior Project Management (SPM) platform. All red team members are expected to follow these guidelines without exception.

---

## The Default Rule: Open a Pull Request

**If you implemented something, open a PR. Full stop.**

This is the expected workflow for 95% of all work. Every issue, every feature, every endpoint, every fix goes through a pull request — not directly to `main`.

Direct pushes to `main` are the **exception**, not the norm. See Section 1 for the narrow cases where it is allowed.

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

### Exception: Direct Push to `main`
This is only allowed for changes that are genuinely trivial. A change qualifies **only if it meets all of the following**:

- Touches 1–2 files with zero logic changes (e.g. fixing a typo, updating a comment, reformatting)
- Has no effect on any API contract, business rule, or database schema
- Is verifiable by reading it — no running or testing needed

If you are unsure whether your change qualifies, it does not qualify. Open a PR.

---

## 2. Pull Request Rules

### Opening a PR
- Link the PR to its issue using `Resolves #<issue-number>` in the PR body.
- Write a short description of what was implemented and any notable decisions made.
- Branch naming: `feature/issue-<number>-<short-description>`

### Review Requirements
- **Authors must not approve their own pull requests** — no exceptions.
- Any team member may review and leave comments on an open PR.
- **Only the team lead may give the final approval and perform the merge.**
- A PR must have at least one approving review before it can be merged.

### Review Etiquette
- Leave specific, actionable comments — avoid vague feedback like "fix this."
- If a comment is a suggestion rather than a blocker, mark it clearly (e.g. `nit:` or `suggestion:`).
- The author is responsible for responding to all comments before requesting re-review.
- Resolved threads should be marked resolved by the reviewer who raised them, not the author.

---

## 3. Commit Message Convention

Use the following prefixes for all commit messages:

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

- Every new service method must handle its failure cases explicitly — no silent swallowing of exceptions.
- The architectural decisions and business rules for P2/P3 are finalized. Do not deviate from them without consulting the team lead first. If you are unsure about a rule, ask before implementing.
- `termId` is never accepted from the frontend. Always resolve via `TermConfigService.getActiveTermId()`.
- Do not add endpoints to controllers without a corresponding entry in `docs/openapi.yaml`.

---

## 5. Issue Workflow

1. Pick up an issue assigned to you, or claim an unassigned one by commenting on it.
2. Create a feature branch from the latest `main`.
3. Implement, test locally, then open a PR.
4. Address all review comments.
5. Team lead approves and merges.
6. Close the issue if not automatically closed by the merge.

---

## 6. What Blocks a Merge

The team lead will not merge a PR if any of the following are true:

- The PR has unresolved review comments
- The author has not responded to feedback
- The implementation deviates from the spec in `docs/openapi.yaml` without justification
- The PR breaks the build or introduces a compile error

---

*These guidelines apply to all Red Team members. Questions or proposed changes to this document should be raised with the team lead.*
