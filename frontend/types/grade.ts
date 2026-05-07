export interface DeliverableBreakdown {
  deliverableId: string;
  deliverableName: string;
  baseGrade: number | null;
  scrumScalar: number | null;
  reviewScalar: number | null;
  deliverableScalar: number | null;
  scaledGrade: number | null;
  weight: number | null;
  weightedContribution: number | null;
}

export interface FinalGradeResponse {
  studentId: string;
  groupId: string;
  deliverableBreakdown: DeliverableBreakdown[];
  weightedTotal: number | null;
  completionRatio: number | null;
  finalGrade: number | null;
  calculatedAt: string | null;
}
