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

export interface GroupDetailResponse {
  id: string
  groupName: string
  termId?: string
  status: GroupStatus
  members: GroupMember[]
  createdAt?: string
  jiraSpaceUrl?: string
  jiraProjectKey?: string
  jiraBound?: boolean
  githubOrgName?: string
  githubBound?: boolean
}
