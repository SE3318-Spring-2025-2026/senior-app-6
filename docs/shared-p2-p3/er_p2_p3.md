# ER Diagram — P0/P1 (existing) + P2 + P3

---

## Diagram 1 — Existing Tables (Blue Team: P0 / P1)

> **Note:** Diagram 1 is preserved for context only. Blue Team PKs are actually UUID (BINARY(16)), not bigint — see Diagram 2 for the corrected representation used by Red Team.

```mermaid
erDiagram

    staff_user {
        bigint id PK
        varchar mail
        varchar password_hash
        enum role
        tinyint first_login
    }

    student {
        bigint id PK
        varchar github_username
        varchar student_id
    }

    password_reset_token {
        bigint id PK
        varchar token
        tinyint used
        datetime created_at
        datetime expires_at
        bigint staff_id FK
    }

    sprint {
        bigint id PK
        date start_date
        date end_date
        int story_point_target
    } 



    rubric_criterion {
        bigint id PK
        varchar criterion_name
        enum grading_type
        decimal weight
        bigint deliverable_id FK
    }

    deliverable {
        bigint id PK
        varchar name
        enum type
        decimal weight
        datetime submission_deadline
        datetime review_deadline
    }

    sprint_deliverable_mapping {
        bigint id PK
        decimal contribution_percentage
        bigint deliverable_id FK
        bigint sprint_id FK
        bigint student_id FK
    }

    staff_user              ||--o{ password_reset_token      : "has"
    deliverable             ||--o{ rubric_criterion           : "has"
    sprint                  ||--o{ sprint_deliverable_mapping : "mapped in"
    student                 ||--o{ sprint_deliverable_mapping : "mapped in"
    deliverable             ||--o{ sprint_deliverable_mapping : "mapped in"

```

---

## Diagram 2 — New Tables (P2: Group Creation + P3: Advisor Association)

> `staff_user` and `student` are carried over from P0/P1 (shown here as anchors only).

```mermaid
erDiagram

    %% ── Anchors from P0/P1 (Blue Team) ──
    %% Blue Team stores UUIDs as BINARY(16) in MySQL — Java type is UUID
    staff_user {
        uuid id PK "stored as BINARY(16)"
        enum role "COORDINATOR | PROFESSOR | ADMIN"
        int advisor_capacity "added by Red Team for P3 — default 5"
    }

    student {
        uuid id PK "stored as BINARY(16) — internal UUID, not the studentId string"
        varchar student_id "university ID string e.g. 23070006036"
        varchar github_username
    }

    %% ── P2 ──
    schedule_window {
        uuid id PK
        varchar term_id
        enum type "GROUP_CREATION | ADVISOR_ASSOCIATION"
        datetime opens_at
        datetime closes_at
    }

    project_group {
        uuid id PK
        varchar group_name
        varchar term_id
        enum status "FORMING | TOOLS_PENDING | TOOLS_BOUND | ADVISOR_ASSIGNED | DISBANDED"
        datetime created_at
        varchar jira_space_url
        varchar jira_project_key
        varchar encrypted_jira_token "length=1024"
        varchar github_org_name
        varchar encrypted_github_pat "length=1024"
        uuid advisor_id FK "NULLABLE — set on ADVISOR_ASSIGNED"
        bigint version "Optimistic Lock — @Version"
    }

    group_membership {
        uuid id PK
        uuid group_id FK
        uuid student_id FK "UNIQUE — one active group per student (uq_gm_student)"
        enum role "TEAM_LEADER | MEMBER"
        datetime joined_at
    }

    group_invitation {
        uuid id PK
        uuid group_id FK
        uuid invitee_student_id FK
        enum status "PENDING | ACCEPTED | DECLINED | AUTO_DENIED | CANCELLED"
        datetime sent_at
        datetime responded_at "NULLABLE"
    }

    %% ── P3 ──
    advisor_request {
        uuid id PK
        uuid group_id FK
        uuid advisor_id FK "FK to staff_user.id"
        enum status "PENDING | ACCEPTED | REJECTED | AUTO_REJECTED | CANCELLED"
        datetime sent_at
        datetime responded_at "NULLABLE"
    }

    %% ── Relationships (declared top-to-bottom to guide vertical layout) ──
    staff_user          ||--o{ project_group      : "advises (nullable)"
    student             ||--o{ group_membership   : "joins"
    student             ||--o{ group_invitation   : "receives"

    project_group       ||--o{ group_membership   : "has members"
    project_group       ||--o{ group_invitation   : "sends"
    project_group       ||--o{ advisor_request    : "submits"

    staff_user          ||--o{ advisor_request    : "receives"
```

---

## Diagram 3 — Shared Config Table (owned by Red Team)

> Red Team creates and seeds this table. Blue Team adds their own config keys as rows — no separate table needed.

```mermaid
erDiagram
    system_config {
        varchar config_key PK "e.g. active_term_id, max_team_size"
        varchar config_value "e.g. 2024-FALL, 5"
    }
```

---

## Notes

| Entity | Process | Key constraint |
|---|---|---|
| `schedule_window` | P2 + P3 | No FK to term table — `term_id` string stamped by backend via `TermConfigService` |
| `group_membership` | P2 | TWO unique constraints: `uq_gm_group_student (groupId, studentId)` + `uq_gm_student (studentId)` |
| `group_invitation` | P2 | One PENDING per student per group — service-layer 409 |
| `advisor_request` | P3 | One active PENDING per group — service-layer 409 |
| `project_group.advisor_id` | P3 | NULLABLE FK to `staff_user`; set on `ADVISOR_ASSIGNED` only |
| `project_group.version` | P3 | `@Version` Optimistic Lock — prevents sanitization race condition |
| `staff_user.advisor_capacity` | P3 | Column added to existing table; default 5; enforced at service layer |
| `system_config` | P2+P3 | **Red Team table** — seeded with `active_term_id` and `max_team_size`; Blue Team adds their own rows |
