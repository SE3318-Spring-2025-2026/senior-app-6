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
  id: string;
  startDate: string;
  endDate: string;
  storyPointTarget?: number | null;
  daysRemaining?: number | null;
}

export type AiResult = "PASS" | "WARN" | "FAIL" | null;

export interface SprintTrackingIssue {
  issueKey: string;
  assignee: string;
  storyPoints: number;
  prMerged: boolean;
  aiPrReview: AiResult;
  aiDiffMatch: AiResult;
}

export interface SprintTrackingResponse {
  issues: SprintTrackingIssue[];
}
