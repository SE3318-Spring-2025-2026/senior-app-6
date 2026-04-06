

> **Note:** The SP column here is a per-endpoint difficulty score used for planning, not GitHub issue story points. P2 issue total = 29 SP; P3 issue total = 15 SP.

## Process-2 Endpoint Summary Table

| # | Method | Path | Auth | Sub-process | Issue | SP | Difficulty |
|---|--------|------|------|-------------|-------|----|------------|
| 1 | POST | `/api/groups` | Student | 2.1 | API-01 | 3 | Medium |
| 2 | GET | `/api/groups/my` | Student | 2.1 | API-01 | 1 | Easy |
| 3 | GET | `/api/students/search?q=` | Student | 2.2 | API-02 | 2 | Easy |
| 4 | POST | `/api/groups/{groupId}/invitations` | Student (TEAM_LEADER) | 2.2 | API-02 | 3 | Medium |
| 5 | GET | `/api/groups/{groupId}/invitations` | Student (TEAM_LEADER) | 2.2 | API-02 | 1 | Easy |
| 6 | DELETE | `/api/invitations/{invitationId}` | Student (TEAM_LEADER) | 2.2 | API-02 | 2 | Easy |
| 7 | GET | `/api/invitations/pending` | Student | 2.3 | API-03 | 1 | Easy |
| 8 | PATCH | `/api/invitations/{invitationId}/respond` | Student (invitee) | 2.3 | API-03 | 5 | Hard |
| 9 | POST | `/api/groups/{groupId}/jira` | Student (TEAM_LEADER) | 2.4 | API-04 | 5 | Hard |
| 10 | POST | `/api/groups/{groupId}/github` | Student (TEAM_LEADER) | 2.5 | API-05 | 5 | Hard |
| 11 | GET | `/api/coordinator/groups` | Staff (Coordinator) | 2.6 | API-06 | 2 | Easy |
| 12 | GET | `/api/coordinator/groups/{groupId}` | Staff (Coordinator) | 2.6 | API-06 | 1 | Easy |
| 13 | PATCH | `/api/coordinator/groups/{groupId}/members` | Staff (Coordinator) | 2.6 | API-06 | 3 | Medium |
| 14 | PATCH | `/api/coordinator/groups/{groupId}/disband` | Staff (Coordinator) | 2.6 | API-06 | 3 | Medium |

**Total SP: 37**


--- 

## Process-3 Endpoint Summary Table

| # | Method | Path | Auth | Sub-process | Issue | SP | Difficulty |
|---|--------|------|------|-------------|-------|----|------------|
| 1 | GET | `/api/advisors` | Student | 3.1 | P3-API-01 | 2 | Easy |
| 2 | POST | `/api/groups/{groupId}/advisor-request` | Student (TEAM_LEADER) | 3.1 | P3-API-01 | 5 | Hard |
| 3 | GET | `/api/groups/{groupId}/advisor-request` | Student (member) | 3.1 | P3-API-01 | 1 | Easy |
| 4 | DELETE | `/api/groups/{groupId}/advisor-request` | Student (TEAM_LEADER) | 3.1 | P3-API-01 | 2 | Easy |
| 5 | GET | `/api/advisor/requests` | Staff (Professor) | 3.2 | P3-API-02 | 2 | Easy |
| 6 | GET | `/api/advisor/requests/{requestId}` | Staff (Professor) | 3.2 | P3-API-02 | 2 | Easy |
| 7 | PATCH | `/api/advisor/requests/{requestId}/respond` | Staff (Professor) | 3.3 | P3-API-03 | 5 | Hard |
| 8 | POST | `/api/coordinator/sanitize` | Staff (Coordinator) | 3.4 | P3-API-04 | 3 | Medium |
| 9 | GET | `/api/coordinator/advisors` | Staff (Coordinator) | 3.5 | P3-API-05 | 1 | Easy |
| 10 | PATCH | `/api/coordinator/groups/{groupId}/advisor` | Staff (Coordinator) | 3.5 | P3-API-05 | 3 | Medium |

**Total SP: 26**