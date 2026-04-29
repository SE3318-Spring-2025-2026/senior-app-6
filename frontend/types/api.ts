export interface ApiError {
  status: number;
  message: string;
}

export interface LlmConfigResponse {
  configured: boolean;
  maskedKey: string | null;
}
