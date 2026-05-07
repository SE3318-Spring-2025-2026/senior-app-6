export interface Sprint {
  id: string;
  startDate: string;
  endDate: string;
  storyPointTarget?: number | null;
}

export interface CreateSprintRequest {
  startDate: string;
  endDate: string;
}

export interface ActiveSprintResponse {
  sprintId: string;
  startDate: string;
  endDate: string;
  storyPointTarget?: number | null;
  daysRemaining?: number | null;
}

export type AiResult = "PASS" | "WARN" | "FAIL" | "PENDING" | "SKIPPED" | null;

export interface SprintTrackingIssue {
  issueKey: string;
  assigneeGithubUsername: string;
  storyPoints: number;
  prMerged: boolean;
  aiPrResult: AiResult;
  aiDiffResult: AiResult;
  prNumber?: number | null;
  branchName?: string | null;
  prUrl?: string | null;
  aiPrReviewNote?: string | null;
  aiDiffMatchNote?: string | null;
}

export interface SprintTrackingResponse {
  issues: SprintTrackingIssue[];
}

export type SprintGradeValue = "A" | "B" | "C" | "D" | "F";

export interface SprintRefreshResult {
  sprintId: string;
  groupsProcessed: number;
  issuesFetched: number;
  aiValidationsRun: number;
  triggeredAt: string;
}

export interface SprintGroupOverview {
  groupId: string;
  groupName: string;
  advisorEmail?: string | null;
  totalIssues: number;
  mergedPRs: number;
  aiPassCount: number;
  aiWarnCount: number;
  aiFailCount: number;
  aiPendingCount: number;
  aiSkippedCount: number;
  gradeSubmitted: boolean;
  pointA_grade: SprintGradeValue | null;
  pointB_grade: SprintGradeValue | null;
}

export interface SprintOverviewResult {
  sprintId: string;
  groups: SprintGroupOverview[];
}
