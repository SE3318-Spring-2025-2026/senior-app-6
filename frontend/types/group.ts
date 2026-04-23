export type GroupStatus =
  | "FORMING"
  | "TOOLS_PENDING"
  | "ADVISOR_ASSIGNED"
  | "TOOLS_BOUND"
  | "DISBANDED"

export type GroupMemberRole = "TEAM_LEADER" | "MEMBER"

export interface GroupMember {
  id?: string
  studentId?: string
  fullName?: string
  joinedAt?: string
  role: GroupMemberRole
}

export interface CreateGroupRequest {
  groupName: string
}

export interface GroupDetailResponse {
  id: string
  groupName: string
  termId?: string
  status: GroupStatus
  advisorId?: string | null
  advisorName?: string | null
  advisorMail?: string | null
  members: GroupMember[]
  createdAt?: string
  jiraSpaceUrl?: string
  jiraEmail?: string
  jiraProjectKey?: string
  jiraBound?: boolean
  githubOrgName?: string
  githubBound?: boolean
  githubRepoName?: string
  githubTokenValid?: boolean | null
  githubPatExpiresAt?: string | null
  jiraTokenValid?: boolean | null
  jiraTokenExpiresAt?: string | null
}

export interface CreateGroupResponse {
  id: string
  groupName: string
  termId?: string
  status: GroupStatus
  members: GroupMember[]
  createdAt?: string
}

export interface BindGithubRequest {
  githubOrgName: string;
  githubPat: string;
  githubRepoName: string;
}

export interface BindJiraRequest {
  jiraSpaceUrl: string;
  jiraEmail: string;
  jiraProjectKey: string;
  jiraApiToken: string;
  jiraTokenExpiresAt?: string;
}
