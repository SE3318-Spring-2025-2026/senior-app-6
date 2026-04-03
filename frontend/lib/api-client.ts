/**
 * Reusable API client for making authenticated requests to the backend
 */

export interface ApiError {
  status: number;
  message: string;
}

export interface LoginRequest {
  mail: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  userInfo: {
    id: string;
    mail: string;
    role: string;
    firstLogin: boolean;
  };
}

export interface StudentLoginRequest {
  studentId: string;
}

// ==================== Coordinator Interfaces ====================

export interface Deliverable {
  id: string;
  name: string;
  type: "Proposal" | "SoW" | "Demonstration";
  submissionDeadline: string;
  reviewDeadline: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateDeliverableRequest {
  name: string;
  type: "Proposal" | "SoW" | "Demonstration";
  submissionDeadline: string;
  reviewDeadline: string;
}

export interface UpdateDeliverableRequest {
  submissionDeadline: string;
  reviewDeadline: string;
}

export interface Sprint {
  id: string;
  startDate: string;
  endDate: string;
  storyPointTarget?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateSprintRequest {
  startDate: string;
  endDate: string;
}

export interface UpdateSprintTargetRequest {
  storyPointTarget: number;
}

export interface GradingCriterion {
  criterionName: string;
  weight: number;
  gradingType: "Binary" | "Soft";
}

export interface CreateRubricRequest {
  deliverableId: string;
  criteria: GradingCriterion[];
}

export interface StudentUploadRequest {
  studentIds: string[];
}

/**
 * Make an authenticated API call to the backend
 * @param endpoint - API endpoint (e.g., '/auth/login')
 * @param method - HTTP method (GET, POST, etc.)
 * @param body - Request body for POST/PUT requests
 * @param token - JWT token for authentication
 */
async function apiCall<T>(
  endpoint: string,
  method: "GET" | "POST" | "PUT" | "DELETE" = "GET",
  body?: unknown,
  token?: string
): Promise<T> {
  const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;

  if (!baseUrl) {
    throw {
      status: 500,
      message: "API base URL is not configured",
    } as ApiError;
  }

  const url = `${baseUrl}${endpoint}`;
  const headers: HeadersInit = {
    "Content-Type": "application/json",
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  try {
    const response = await fetch(url, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw {
        status: response.status,
        message:
          errorData.message ||
          errorData.error ||
          `HTTP ${response.status}`,
      } as ApiError;
    }

    const data = await response.json();
    return data as T;
  } catch (error) {
    if (error instanceof TypeError) {
      throw {
        status: 0,
        message: `Network error: ${error.message}`,
      } as ApiError;
    }

    throw error;
  }
}

/**
 * Faculty/Admin login
 */
export async function loginFaculty(
  email: string,
  password: string
): Promise<LoginResponse> {
  return apiCall<LoginResponse>("/auth/login", "POST", {
    mail: email,
    password,
  });
}

/**
 * Student GitHub login (redirects to GitHub or returns session)
 */
export async function loginStudent(
  studentId: string
): Promise<{ redirectUrl?: string }> {
  return apiCall<{ redirectUrl?: string }>(
    "/auth/github/login",
    "POST",
    { studentId }
  );
}

// ==================== Coordinator API Functions ====================

/**
 * Create a new deliverable
 * @param deliverable - Deliverable creation data
 * @param token - JWT authentication token
 */
export async function createDeliverable(
  deliverable: CreateDeliverableRequest,
  token?: string
): Promise<Deliverable> {
  return apiCall<Deliverable>(
    "/coordinator/deliverables",
    "POST",
    deliverable,
    token
  );
}

/**
 * Update a deliverable's deadlines
 * @param deliverableId - The ID of the deliverable to update
 * @param data - Updated deadline data
 * @param token - JWT authentication token
 */
export async function updateDeliverable(
  deliverableId: string,
  data: UpdateDeliverableRequest,
  token?: string
): Promise<Deliverable> {
  return apiCall<Deliverable>(
    `/coordinator/deliverables/${deliverableId}`,
    "PATCH",
    data,
    token
  );
}

/**
 * Create a new sprint
 * @param sprint - Sprint creation data
 * @param token - JWT authentication token
 */
export async function createSprint(
  sprint: CreateSprintRequest,
  token?: string
): Promise<Sprint> {
  return apiCall<Sprint>("/coordinator/sprints", "POST", sprint, token);
}

/**
 * Update a sprint's story point target
 * @param sprintId - The ID of the sprint to update
 * @param target - The new story point target
 * @param token - JWT authentication token
 */
export async function updateSprintTarget(
  sprintId: string,
  target: number,
  token?: string
): Promise<void> {
  return apiCall<void>(
    `/coordinator/sprints/${sprintId}/target`,
    "PATCH",
    { storyPointTarget: target },
    token
  );
}

/**
 * Upload student IDs (batch registration)
 * @param studentIds - Array of student IDs to register
 * @param token - JWT authentication token
 */
export async function uploadStudents(
  studentIds: string[],
  token?: string
): Promise<void> {
  return apiCall<void>(
    "/coordinator/students/upload",
    "POST",
    { studentIds },
    token
  );
}

/**
 * Get all deliverables for the coordinator
 * @param token - JWT authentication token
 */
export async function getDeliverables(token?: string): Promise<Deliverable[]> {
  return apiCall<Deliverable[]>(
    "/coordinator/deliverables",
    "GET",
    undefined,
    token
  );
}

/**
 * Get all sprints for the coordinator
 * @param token - JWT authentication token
 */
export async function getSprints(token?: string): Promise<Sprint[]> {
  return apiCall<Sprint[]>("/coordinator/sprints", "GET", undefined, token);
}

/**
 * Update a deliverable's weight
 * @param deliverableId - The ID of the deliverable
 * @param weight - The new weight percentage
 * @param token - JWT authentication token
 */
export async function updateDeliverableWeight(
  deliverableId: string,
  weight: number,
  token?: string
): Promise<Deliverable> {
  return apiCall<Deliverable>(
    `/coordinator/deliverables/${deliverableId}/weight`,
    "PATCH",
    { weight },
    token
  );
}

/**
 * Create rubric for a deliverable
 * @param deliverableId - The ID of the deliverable
 * @param criteria - Array of grading criteria
 * @param token - JWT authentication token
 */
export async function createRubric(
  deliverableId: string,
  criteria: GradingCriterion[],
  token?: string
): Promise<void> {
  return apiCall<void>(
    `/coordinator/deliverables/${deliverableId}/rubric`,
    "POST",
    { criteria },
    token
  );
}

/**
 * Publish the coordinator configuration (lock in rules)
 * @param token - JWT authentication token
 */
export async function publishConfig(token?: string): Promise<void> {
  return apiCall<void>("/coordinator/publish-config", "POST", {}, token);
}

/**
 * Validate password reset token
 * @param token - The reset token from email
 */
export async function validateResetPasswordToken(
  token: string
): Promise<{ valid: boolean }> {
  return apiCall<{ valid: boolean }>(
    `/auth/reset-password?token=${encodeURIComponent(token)}`,
    "GET"
  );
}

/**
 * Set password with reset token
 * @param token - The reset token from email
 * @param newPassword - The new password to set
 */
export async function setPasswordWithToken(
  token: string,
  newPassword: string
): Promise<{ success: boolean }> {
  return apiCall<{ success: boolean }>("/auth/reset-password", "POST", {
    token,
    newPassword,
  });
}

/**
 * Get authentication token from localStorage
 */
export function getAuthToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem("authToken");
}

export default apiCall;
