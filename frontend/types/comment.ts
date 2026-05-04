export interface SubmissionComment {
  id: string;
  submissionId: string;
  authorId: string;
  authorName: string;
  authorRole?: "Professor" | "Coordinator" | "Admin";
  content: string;
  sectionReference?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface CreateSubmissionCommentRequest {
  content: string;
  sectionReference?: string | null;
}

export interface SubmissionCommentResponse {
  commentId: string;
  submissionId: string;
  authorId: string;
  authorName?: string;
  content: string;
  sectionReference?: string | null;
  createdAt: string;
}
