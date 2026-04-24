export interface CommitteeProfessorAssignment {
  professorId: string;
  role: "ADVISOR" | "JURY";
}

export interface StudentGroup {
  id: string;
  name: string;
  advisorApproved: boolean;
  committeeId?: string | null;
}

export interface Committee {
  id: string;
  committeeName?: string;
  name?: string;
  termId?: string;
  deliverableId?: string;
  groups?: StudentGroup[];
}

export interface CommitteeDetail {
  id: string;
  committeeName?: string;
  termId?: string;
  professors: CommitteeProfessorAssignment[];
  groupIds: string[];
}

export interface CreateCommitteeRequest {
  committeeName: string;
  termId: string;
  deliverableId?: string;
  name?: string;
}

export interface AssignCommitteeProfessorsRequest {
  professors: CommitteeProfessorAssignment[];
}