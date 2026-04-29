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

export type AiValidationResult = "PENDING" | "PASS" | "WARN" | "FAIL" | "SKIPPED";
export type ScrumGradeValue = "A" | "B" | "C" | "D" | "F";

export interface ActiveSprintResponse {
  sprintId: string;
  startDate: string;
  endDate: string;
  storyPointTarget: number | null;
  daysRemaining: number;
}

export interface PerStudentSummary {
  assigneeGithubUsername: string;
  completedPoints: number;
  aiValidationStatus: AiValidationResult;
}

export interface AdvisorGroupSprintSummaryResponse {
  groupId: string;
  groupName: string;
  totalIssues: number;
  mergedPRs: number;
  aiPassCount: number;
  aiWarnCount: number;
  aiFailCount: number;
  aiPendingCount: number;
  aiSkippedCount: number;
  gradeSubmitted: boolean;
  pointA_grade: ScrumGradeValue | null;
  pointB_grade: ScrumGradeValue | null;
  perStudentSummary: PerStudentSummary[];
}

export interface TrackingIssueResponse {
  issueKey: string;
  assigneeGithubUsername: string | null;
  storyPoints: number | null;
  prNumber: number | null;
  prMerged: boolean | null;
  aiPrResult: AiValidationResult;
  aiDiffResult: AiValidationResult;
}

export interface SprintTrackingResponse {
  groupId: string;
  sprintId: string;
  fetchedAt: string | null;
  issues: TrackingIssueResponse[];
  perStudentSummary?: PerStudentSummary[] | null;
}

export interface SubmitScrumGradeRequest {
  pointA_grade: ScrumGradeValue;
  pointB_grade: ScrumGradeValue;
}

export interface ScrumGradeResponse {
  gradeId: string;
  groupId: string;
  sprintId: string;
  pointA_grade: ScrumGradeValue;
  pointB_grade: ScrumGradeValue;
  advisorId: string;
  gradedAt: string;
  updatedAt?: string | null;
}
