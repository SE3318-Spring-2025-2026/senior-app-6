import type { GroupStatus, MemberResponse } from './group';

export interface RequestGroupDetail {
  id: string;
  groupName: string;
  termId: string;
  status: GroupStatus;
  members: MemberResponse[];
}

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
  group: RequestGroupDetail;
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

export type AdvisorRequestStatus =
  | "PENDING"
  | "ACCEPTED"
  | "REJECTED"
  | "AUTO_REJECTED"
  | "CANCELLED";

export interface AdvisorCapacityResponse {
  advisorId: string;
  name: string;
  mail: string;
  currentGroupCount: number;
  capacity: number;
  atCapacity?: boolean | null;
}

export interface AdvisorRequestResponse {
  requestId: string;
  groupId?: string;
  advisorId?: string;
  advisorName?: string;
  status: AdvisorRequestStatus;
  sentAt?: string;
  respondedAt?: string | null;
}

export interface AdvisorOverrideResponse {
  groupId: string;
  status: GroupStatus;
  advisorId: string | null;
}
