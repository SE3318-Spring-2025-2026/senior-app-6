export type GroupStatus =
  | "FORMING"
  | "TOOLS_PENDING"
  | "ADVISOR_ASSIGNED"
  | "TOOLS_BOUND"
  | "DISBANDED"

export type GroupMemberRole = "TEAM_LEADER" | "MEMBER"

export interface MemberResponse {
  studentId: string
  role: GroupMemberRole
  joinedAt: string
}

export type GroupMember = MemberResponse;

export interface CreateGroupRequest {
  groupName: string
}

export interface GroupDetailResponse {
  id: string
  groupName: string
  termId: string
  status: GroupStatus
  createdAt: string
  jiraSpaceUrl?: string | null
  jiraEmail?: string | null
  jiraProjectKey?: string | null
  jiraBound: boolean
  githubOrgName?: string | null
  githubBound: boolean
  members: MemberResponse[]
  advisorId?: string | null
  advisorMail?: string | null
}

export interface GroupSummaryResponse {
  id: string
  groupName: string
  termId: string
  status: GroupStatus
  memberCount: number
  jiraBound: boolean
  githubBound: boolean
}

export interface CoordinatorGroupMemberActionRequest {
  studentId: string
  action: "ADD" | "REMOVE"
}
