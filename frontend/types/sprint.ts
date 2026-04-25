export interface Sprint {
  id: string;
  startDate: string;
  endDate: string;
  storyPointTarget?: number | null;
}

export interface CreateSprintRequest {
  startDate: string;
  endDate: string;
}
