import type { GroupDetailResponse } from "./group";

export type InvitationStatus =
  | "PENDING"
  | "ACCEPTED"
  | "DECLINED"
  | "AUTO_DENIED"
  | "CANCELLED";

export interface InvitationResponse {
  invitationId: string;
  groupId: string;
  targetStudentId?: string;
  status: InvitationStatus;
  sentAt: string;
  respondedAt?: string | null;
  groupName?: string;
  teamLeaderStudentId?: string;
}

export type GroupInvitation = InvitationResponse;
export type SentGroupInvitation = InvitationResponse;

export type InvitationActionResponse = GroupDetailResponse | InvitationResponse;

export function isGroupDetailResponse(response: InvitationActionResponse): response is GroupDetailResponse {
  return !('invitationId' in response);
}
