# P5 Dev Setup Guide

## Prerequisites

- At least one group in `ADVISOR_ASSIGNED` status with both tools bound

---

## 1. JIRA Setup

### Create a workspace

1. Sign up at [atlassian.net](https://www.atlassian.com) (free).
2. Create a new **Scrum** project. Note the **project key** (e.g. `SCRUM`).
3. Create an **active sprint** on the board with at least 2–3 issues.
   - Each issue needs a **story points** estimate (custom field `customfield_10016`).
   - Set an assignee on each issue.

### Generate an API token

1. Go to `https://id.atlassian.com/manage-profile/security/api-tokens`.
2. Click **Create API token**, name it anything.
3. Copy the token — you won't see it again.

### What the backend expects

| `BindJiraRequest` field | Example value |
|------------------------|---------------|
| `jiraSpaceUrl` | `https://yourworkspace.atlassian.net` |
| `jiraEmail` | `you@example.com` (the account that owns the token) |
| `jiraProjectKey` | `SCRUM` |
| `jiraApiToken` | `ATATT3xFfGF0...` |
| `jiraTokenExpiresAt` | `2027-01-01` (optional) |

The backend uses `Basic base64(email:token)` auth — **not Bearer**.

---

## 2. GitHub Setup

### Create an org and repo

1. Create a free GitHub organization (or use a personal account's org).
2. Create a repo inside the org (e.g. `your-org-repo-name`).
3. Create branches named after JIRA issue keys. The backend matches branches using:
   - `SCRUM-1-my-feature` ← **recommended** (key + hyphen prefix)
   - `SCRUM-1/my-feature` (key + slash prefix)
   - `feature/SCRUM-1` (ends with key)
4. Merge at least one PR from a matching branch into `main` (closed + merged state).
   - **Only PRs merged into `main` are picked up by the pipeline** — open or merged-into-other-branch PRs are ignored.
   - Add at least one **review comment** on the PR so the AI has something to analyze.

### Generate a PAT

1. GitHub → **Settings → Developer settings → Personal access tokens → Tokens (classic)**.
2. Scopes required: `repo` + `read:org`.
3. Copy the token.

### What the backend expects

| `BindGithubRequest` field | Example value |
|--------------------------|---------------|
| `githubOrgName` | `your-org-name` |
| `githubRepoName` | `your-org-repo-name` |
| `githubPat` | `ghp_xxxxxxxxxxxx` |

---

## 3. Gemini API Key

1. Get a free key at [https://aistudio.google.com/apikey](https://aistudio.google.com/apikey).
2. Store it via the admin API LLM set ui — **do not** put it in `application.properties`:

```http
PUT /api/admin/llm-config
Authorization: Bearer <admin-jwt>
Content-Type: application/json

{ "apiKey": "AIzaSy..." }
```

3. Verify:

```http
GET /api/admin/llm-config
Authorization: Bearer <admin-jwt>
```

Expected: `{ "configured": true, "maskedKey": "AIza****" }`

The key is stored AES-256 encrypted in `system_config` table. `application.properties` controls model selection only (see below).

---

## 4. `application.properties` Minimum Config

Copy `application.properties.example` to `application.properties` and fill in your DB credentials, JWT secret, and encryption key as documented in the example file.

For P5 specifically, the only non-obvious properties are the LLM settings:

| Property | Notes |
|----------|-------|
| `llm.api.base-url` | Gemini base URL — already set in the example file, do not change |
| `llm.api.model.pr-review` | Model used for PR review validation (default: `gemini-2.5-flash-lite`) |
| `llm.api.model.diff-match` | Model used for diff matching (default: `gemini-2.5-flash-lite`) |
| `llm.api.timeout-seconds` | AI call timeout in seconds (default: `15`) |

The **Gemini API key itself is not stored here** — it goes through the admin UI (see section 3).

---

## 5. Triggering the Pipeline

Once a group has JIRA + GitHub bound and an advisor assigned:

```http
POST /api/coordinator/sprints/{sprintId}/refresh?force=true
Authorization: Bearer <coordinator-jwt>
```

Expected response:

```json
{
  "sprintId": "...",
  "groupsProcessed": 1,
  "issuesFetched": 3,
  "aiValidationsRun": 2,
  "triggeredAt": "2026-05-01T10:00:00"
}
```

`aiValidationsRun` < `issuesFetched` is normal — only issues with a **merged PR** get AI validation.

Then check the overview:

```http
GET /api/coordinator/sprints/{sprintId}/overview
Authorization: Bearer <coordinator-jwt>
```

---

## 6. What Each AI Result Means

| Result | Meaning |
|--------|---------|
| `PASS` | AI validated the PR review / diff |
| `WARN` | AI found the review shallow or diff weakly related |
| `FAIL` | AI rejected the PR review or diff |
| `SKIPPED` | No PR found, or JIRA description was null |
| `PENDING` | Not yet processed by the pipeline |

---

## 7. Seed Data Notes

`data.sql` does **not** seed `sprint_tracking_log` or `scrum_grade` rows.
For a meaningful local test, use the UI to set everything up:

1. Log in as **coordinator** → create a sprint covering today's date.
2. Log in as **student** → create a group, bind JIRA and GitHub via the Tools page.
3. Log in as **professor** → accept the advisor request for the group.
4. Log in as **coordinator** → trigger the pipeline refresh from the Sprint Monitor page (or via the refresh endpoint in step 5).

This populates `sprint_tracking_log` with live data. Once rows exist, the advisor grading panel
(`/professor/sprint`) will show the group and allow grade submission.

---
