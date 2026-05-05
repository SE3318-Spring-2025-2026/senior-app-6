export type StudentDeliverableSubmissionStatus = "NOT_SUBMITTED" | "SUBMITTED";

export interface StudentDeliverable {
  id: string;
  name: string;
  type: "Proposal" | "SoW" | "Demonstration";
  submissionDeadline: string;
  reviewDeadline: string;
  weight: number;
  submissionStatus: StudentDeliverableSubmissionStatus;
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
  sectionReference: string;
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
  sectionReference: string;
}

export interface RubricMappingsResponse {
  submissionId: string;
  mappings: RubricMappingEntry[];
}
