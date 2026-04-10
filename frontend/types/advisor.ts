export interface AdvisorRequestItem {
  requestId: string;
  groupId: string;
  groupName: string;
  termId: string;
  memberCount: number;
  sentAt: string;
}

export interface AdvisorRequestMember {
  studentId: string;
  role: "TEAM_LEADER" | "MEMBER";
  joinedAt: string;
}

export interface AdvisorRequestGroup {
  id: string;
  groupName: string;
  termId: string;
  status: string;
  members: AdvisorRequestMember[];
}

export interface AdvisorRequestDetail {
  requestId: string;
  group: AdvisorRequestGroup;
  sentAt: string;
}

export interface AdvisorRespondRequest {
  accept: boolean;
}

export interface AdvisorRespondResponse {
  requestId: string;
  status: "ACCEPTED" | "REJECTED";
  groupId?: string;
  groupStatus?: string;
}
