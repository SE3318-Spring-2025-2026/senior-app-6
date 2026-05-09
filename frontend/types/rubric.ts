export type GradingType = "Binary" | "Soft";

export interface GradingCriterion {
  criterionName: string;
  gradingType: GradingType;
  weight: number;
}

export interface CreateRubricRequest {
  deliverableId: string;
  criteria: GradingCriterion[];
}

export interface RubricCriterionResponse {
  id?: string;
  criterionName: string;
  gradingType: GradingType;
  weight: number;
}

export interface SubmitGradeEntry {
  criterionId: string;
  selectedGrade: string;
}

export interface RubricGradeSubmitResponse {
  baseDeliverableGrade: number;
}

export interface ExistingGradeEntry {
  criterionId: string;
  selectedGrade: string;
}

export interface ExistingGradesResponse {
  grades: ExistingGradeEntry[];
}
