# Sprint 4 Frontend Smoke Test Report

**Date:** 2026-05-08  
**Branch:** `feature/issue258-frontend-smoke-tests`  
**Issue:** #258  
**Tester:** Bilge Akar  

---

## Environment

| Component | Value |
|-----------|-------|
| Frontend | Nuxt 3 — `http://localhost:3000` |
| Backend | Spring Boot — `http://localhost:8080` |
| Database | MySQL 8.0 (`spm` schema) |
| Backend start | `./mvnw spring-boot:run` |
| Frontend start | `npm run dev` (from `frontend/`) |

### Test accounts

| Role | Email | Password |
|------|-------|----------|
| Coordinator | `coordinator@test.com` | `test` |
| Professor | `professor@test.com` | `test` |
| Admin | `test@test.com` | `test` |

> Students authenticate via GitHub OAuth only — no password login available.

---

## Setup notes

- Stop any standalone Tomcat on port 8080 (`brew services stop tomcat`) before starting the backend.
- MySQL must be running on port 3306 with an `spm` database. The `data.sql` seed file runs automatically on startup.
- Before testing, ensure `active_term_id` is set: `PATCH /api/coordinator/system-config` with `{"activeTermId":"2026-SPRING","maxTeamSize":5}`.

---

## Results Summary

| Feature | Status | Notes |
|---------|--------|-------|
| S4-03 Advisor capacity inputs | PASS | |
| S4-04 Sprint monitor | PASS | |
| S4-06 Schedule window | PASS | Was missing locally — required `git merge origin/red/sprint4` |
| P7-06 Committee grading panel | PASS | |
| P7-07 Final grade dashboard | PASS | |
| S4-09b Settings page | PASS | Minor: inline alerts instead of toasts |

---

## Feature Checklists

### S4-03 — Advisor Capacity Inputs

**Route:** `/coordinator/advisors` (Coordinator)  
**Route:** `/admin/register-professor` (Admin)

| Check | Result |
|-------|--------|
| GET advisor list loads correctly | PASS |
| Inline edit button opens input pre-filled with current capacity | PASS |
| Save with valid value (1–20) updates row immediately | PASS |
| Save with value > 20 shows validation error, no API call | PASS |
| Cancel discards changes | PASS |
| Admin registration with custom capacity (e.g. 10) → 201 | PASS |
| `PATCH /coordinator/advisors/{id}/capacity` with 21 → 400 | PASS |

---

### S4-04 — Sprint Monitor

**Route:** `/coordinator/sprint-monitor` (Coordinator)

| Check | Result |
|-------|--------|
| Sprint selector loads all sprints from `GET /coordinator/sprints` | PASS |
| Selecting a sprint loads overview table | PASS |
| Trigger Refresh disabled when no sprint selected | PASS |
| Trigger Refresh calls `POST /coordinator/sprints/{id}/refresh?force=true` | PASS |
| Refresh success banner shows `groupsProcessed / issuesFetched / aiValidationsRun` | PASS |
| AI PASS count badge: emerald color | PASS |
| AI WARN count badge: amber color | PASS |
| AI FAIL count badge: red color | PASS |
| Grade column shows `A/B` format when submitted, `—` when not | PASS |

---

### S4-06 — Schedule Window

**Route:** `/coordinator/schedule` (Coordinator)  
**Dashboard link:** "Schedule Windows" nav item

> **Note:** `schedule.vue` was merged in PR #288 into `red/sprint4` but not yet in the local working branch at test time. Fixed by running `git merge origin/red/sprint4`.

| Check | Result |
|-------|--------|
| Page loads schedule windows from `GET /coordinator/schedule-windows` | PASS |
| GROUP_CREATION window with `isActive: true` shows **OPEN** badge (green) | PASS |
| Window with `id: null` shows **NOT SET** badge (slate) | PASS |
| Window with `id` set and `isActive: false` shows **CLOSED** badge (red) | PASS |
| Edit button opens modal with `datetime-local` date pickers | PASS |
| Modal validation: close time must be after open time | PASS |
| Confirm dialog fires before `DELETE /coordinator/schedule-windows/{id}` | PASS |
| `POST /coordinator/schedule-windows` upsert → 201 | PASS |
| `DELETE /coordinator/schedule-windows/{id}` → 204 | PASS |
| Page auto-refreshes every 30s via `setInterval` | PASS |

---

### P7-06 — Committee Grading Panel

**Route:** `/committee/submissions/:submissionId/grade` (Professor)

> A test submission was inserted directly into MySQL for this test (no student GitHub OAuth available).

| Check | Result |
|-------|--------|
| Submission document renders in left panel | PASS |
| Rubric criteria load from `GET /coordinator/deliverables/{id}/rubric` | PASS |
| `Binary` criterion shows only S and F options | PASS |
| `Soft` criterion shows A, B, C, D, F options | PASS |
| Submit button disabled until all criteria have a selection | PASS |
| Partial submit (`POST /submissions/{id}/grade`) → 400 with missing criterion ID | PASS |
| Full submit → returns `baseDeliverableGrade` displayed as `100.00` | PASS |
| 403 from API → `router.back()` (no hang or blank screen) | PASS (code path) |
| Left/right panel toggles work | PASS |

---

### P7-07 — Final Grade Dashboard

**Routes:**  
- `/coordinator/grades` (Coordinator)  
- `/student/group/grade` (Student)

| Check | Result |
|-------|--------|
| Coordinator table loads via groups → group detail → grade fetch | PASS |
| Students with no grade show `—` in all numeric columns | PASS |
| Calculate button calls `GET /students/{id}/grade/calculate` and updates row | PASS |
| Group filter dropdown filters table rows | PASS (code review) |
| Clicking column header toggles sort asc/desc | PASS (code review) |
| Non-coordinator accessing `/coordinator/grades` → redirected to `/forbidden` | PASS (auth middleware) |
| Student view shows own deliverable breakdown | PASS |
| Student `baseGrade` per deliverable displayed | PASS |
| `GET /students/{id}/grade` returns 204 → displayed as `—` (not error) | PASS |

---

### S4-09b — Settings Page

**Route:** `/coordinator/settings` (Coordinator)

| Check | Result |
|-------|--------|
| Empty Active Term ID shows client-side error, no API call | PASS |
| Max Team Size < 1 shows client-side error, no API call | PASS |
| `PATCH /coordinator/system-config` with valid data → 200 | PASS |
| Success message appears after save | PASS |
| CSV file with 11-digit IDs parses correctly | PASS |
| Preview shows first 10 IDs as chips | PASS (code review) |
| `POST /coordinator/students/upload` with valid IDs → 201 | PASS |
| Upload with empty list → 400 | PASS |
| Clear button resets file and parsed IDs | PASS (code review) |
| Error/success feedback visible after each action | PASS |

---

## Findings

### Backend gap — `activeTermId` accepts empty string

`PATCH /api/coordinator/system-config` accepts `{"activeTermId":""}` with HTTP 200.  
There is no `@NotBlank` constraint on `UpdateSystemConfigRequest.activeTermId`.  
This silently wipes the `active_term_id` row in `system_config`, causing a 500 on schedule window requests.

**Workaround for demo:** Ensure config is set before demo. Long-term: add `@NotBlank` to the request DTO.

### UX inconsistency — Settings page alerts vs toasts

The settings page uses persistent inline alerts (`v-if="configSuccess"`) rather than the auto-dismissing toast pattern from `tools.vue` (`showToast(msg, type)` with 3s timeout).  
Not demo-blocking but inconsistent with the rest of the app.

---

## How to run the smoke tests manually

```bash
# 1. Start MySQL (if not running)
sudo launchctl load /Library/LaunchDaemons/com.oracle.oss.mysql.mysqld.plist

# 2. Start backend
cd backend
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.datasource.password=<your-password>"

# 3. Start frontend
cd frontend
npm install
npm run dev

# 4. Open http://localhost:3000 and log in as coordinator@test.com / test
```
