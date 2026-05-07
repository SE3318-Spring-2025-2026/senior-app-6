export type StudentDeliverableSubmissionStatus = "NOT_SUBMITTED" | "SUBMITTED";

export interface StudentDeliverable {
  id: string;
  name: string;
  type: "Proposal" | "SoW" | "Demonstration";
  submissionDeadline: string;
  reviewDeadline: string;
  weight: number;
  submissionStatus: StudentDeliverableSubmissionStatus;
  submissionId: string | null;
}

export interface SubmissionCreateResponse {
  submissionId: string;
  deliverableId: string;
  groupId: string;
  submittedAt: string;
  revisionNumber: number;
  isRevision: boolean;
}

export interface SubmissionResponse {
  submissionId: string;
  deliverableId: string;
  groupId: string;
  markdownContent: string;
  submittedAt: string;
  updatedAt: string | null;
  revisionNumber: number;
  isRevision: boolean;
}

export interface RubricMappingEntry {
  criterionId: string;
  sectionKey: string;
  sectionStart: number;
  sectionEnd: number;
}

export interface SaveRubricMappingsRequest {
  mappings: RubricMappingEntry[];
}

export interface SaveRubricMappingsResponse {
  submissionId: string;
  mappingCount: number;
}

/** Local (UI-only) mapping entry, not yet persisted */
export interface LocalMappingEntry {
  localId: string;
  criterionId: string;
  criterionName: string;
  sectionKey: string;
  sectionStart: number;
  sectionEnd: number;
}

export interface RubricMappingsResponse {
  submissionId: string;
  mappings: RubricMappingEntry[];
}

export interface SubmissionComment {
  id: string;
  submissionId: string;
  reviewerId: string;
  reviewerEmail: string;
  commentText: string;
  createdAt: string;
}

export interface CreateSubmissionCommentRequest {
  commentText: string;
}
