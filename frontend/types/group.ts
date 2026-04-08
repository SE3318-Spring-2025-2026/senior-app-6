export type GroupStatus =
  | "FORMING"
  | "UNADVISED"
  | "ADVISOR_PENDING"
  | "ADVISED"
  | "TOOLS_BOUND"
  | "COMMITTEE_ASSIGNED"
  | "DISBANDED"

export type GroupMemberRole = "TEAM_LEADER" | "MEMBER"

export interface GroupMember {
  id: string
  studentId?: string
  fullName: string
  role: GroupMemberRole
}

export interface GroupDetailResponse {
  id: string
  groupName: string
  status: GroupStatus
  members: GroupMember[]
}