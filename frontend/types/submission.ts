export interface SubmissionResponse {
  submissionId: string;
  deliverableId: string;
  groupId: string;
  content: string;
  submittedAt: string;
  revisedAt: string | null;
}

export interface RubricMappingItem {
  criterionId: string;
  sectionReference: string;
}

export interface RubricMappingsResponse {
  submissionId: string;
  mappings: RubricMappingItem[];
}
