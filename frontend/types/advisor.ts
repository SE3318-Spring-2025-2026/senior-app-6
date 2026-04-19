import type { GroupDetailResponse, GroupStatus } from './group';

export interface AdvisorRequestItem {
  requestId: string;
  groupId: string;
  groupName: string;
  termId: string;
  memberCount: number;
  sentAt: string;
}

export interface AdvisorRequestDetail {
  requestId: string;
  group: GroupDetailResponse;
  sentAt: string;
}

export interface AdvisorRespondRequest {
  accept: boolean;
}

export interface AdvisorRespondResponse {
  requestId: string;
  status: "ACCEPTED" | "REJECTED";
  groupId: string;
  groupStatus: GroupStatus;
}


