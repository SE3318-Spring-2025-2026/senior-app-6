import type { GroupStatus } from "./group";
import type { DeliverableType } from "./deliverable";
import type { GradingType } from "./rubric";

export type ProfessorRole = "ADVISOR" | "JURY";

export interface CommitteeProfessorAssignment {
  professorId: string;
  role: ProfessorRole;
}

export interface CommitteeProfessorResponse {
  id: string;
  professorId: string;
  professorName: string;
  professorMail: string;
  role: ProfessorRole;
}

export interface CommitteeGroupResponse {
  id: string;
  groupId: string;
  groupName: string;
  status: GroupStatus;
}

export interface CommitteeSummaryResponse {
  id: string;
  committeeName: string;
  termId: string;
  deliverableId: string;
}

export interface CommitteeDetailResponse {
  id: string;
  committeeName: string;
  termId: string;
  deliverableId: string;
  professors: CommitteeProfessorResponse[];
  groups: CommitteeGroupResponse[];
}

export interface CreateCommitteeRequest {
  committeeName: string;
  termId: string;
  deliverableId: string;
}

export interface AddProfessorsToCommitteeRequest {
  professors: CommitteeProfessorAssignment[];
}

export interface AddGroupsToCommitteeRequest {
  groupIds: string[];
}

export interface ProfessorCommitteeGroup {
  groupId: string;
  groupName: string;
  status: GroupStatus;
  submissionId?: string | null;
}

export interface ProfessorCommitteeRubricCriterion {
  criterionName: string;
  gradingType: GradingType;
  weight: number;
}

export interface CommitteeSubmissionSummary {
  submissionId: string;
  deliverableId: string;
  groupId: string;
  groupName: string;
  deliverableName: string;
  submittedAt: string;
  commentCount: number;
}

export interface ProfessorCommittee {
  committeeId: string;
  committeeName: string;
  termId?: string;
  professorRole: ProfessorRole;
  deliverableId?: string;
  deliverableName?: string;
  deliverableType?: DeliverableType;
  submissionDeadline?: string;
  reviewDeadline?: string;
  deliverableWeight?: number;
  groups: ProfessorCommitteeGroup[];
  rubrics?: ProfessorCommitteeRubricCriterion[];
}

export interface CommitteeSubmission {
  submissionId: string;
  deliverableId: string;
  groupId: string;
  groupName: string;
  deliverableName: string;
  submittedAt: string;
  commentCount: number;
}
