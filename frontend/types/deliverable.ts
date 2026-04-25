export type DeliverableType = "Proposal" | "SoW" | "Demonstration";

export interface Deliverable {
  id: string;
  name: string;
  type: DeliverableType;
  submissionDeadline: string;
  reviewDeadline: string;
  weight?: number | null;
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
