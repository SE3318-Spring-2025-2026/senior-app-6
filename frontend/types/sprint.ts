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
