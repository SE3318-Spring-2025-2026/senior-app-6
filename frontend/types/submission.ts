export interface StudentDeliverable {
  id: string;
  name: string;
  type: "Proposal" | "SoW" | "Demonstration";
  submissionDeadline: string;
  reviewDeadline: string;
  submissionStatus: "Not Submitted" | "Submitted" | "Graded";
}

export interface SubmissionCreateResponse {
  submissionId: string;
  deliverableId: string;
  groupId: string;
  status: string;
  submittedAt: string;
}

export interface SubmissionResponse {
  submissionId: string;
  deliverableId: string;
  groupId: string;
  content: string;
  submittedAt: string;
  revisedAt: string | null;
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
  mappings: RubricMappingItem[];
}
