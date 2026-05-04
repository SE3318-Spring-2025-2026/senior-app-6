export type DeliverableType = "Proposal" | "SoW" | "Demonstration";
export type SubmissionStatus = "SUBMITTED" | "NOT_SUBMITTED";

export interface Deliverable {
  id: string;
  name: string;
  type: DeliverableType;
  submissionDeadline: string;
  reviewDeadline: string;
  weight?: number | null;
  submissionStatus?: SubmissionStatus | null;
}

export interface CreateDeliverableRequest {
  name: string;
  type: DeliverableType;
  submissionDeadline: string;
  reviewDeadline: string;
}

export interface UpdateDeliverableRequest {
  name?: string;
  type?: DeliverableType;
  submissionDeadline?: string;
  reviewDeadline?: string;
  weight?: number;
}

export interface CreateDeliverableSubmissionRequest {
  markdownContent: string;
}

export interface DeliverableSubmissionResponse {
  submissionId: string;
  groupId: string;
  deliverableId: string;
  submittedAt: string;
  revisionNumber: number;
  revision: boolean;
}

export interface DeliverableSubmissionDetailResponse {
  submissionId: string;
  groupId: string;
  deliverableId: string;
  markdownContent: string;
  submittedAt: string;
  updatedAt?: string | null;
  revisionNumber: number;
  revision: boolean;
}
