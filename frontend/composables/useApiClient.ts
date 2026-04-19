/**
 * Reusable API client composable for making authenticated requests to the backend
 * Note: LoginResponse and User types are defined globally in types/*.d.ts
 */

import { useAuthStore } from '~/stores/auth';

export interface ApiError {
  status: number;
  message: string;
}

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

export interface GradingCriterion {
  criterionName: string;
  weight: number;
  gradingType: "Binary" | "Soft";
}

export interface CreateRubricRequest {
  deliverableId: string;
  criteria: GradingCriterion[];
}

export interface RubricCriterionResponse {
  id: string;
  criterionName: string;
  gradingType: "Binary" | "Soft";
  weight: number;
}

export interface Committee {
  id: string;
  name: string;
  groups?: StudentGroup[];
}

export interface StudentGroup {
  id: string;
  name: string;
  advisorApproved: boolean;
  committeeId?: string | null;
}

export interface GithubLoginRequest {
  code: string;
  studentId: string;
}

export interface GithubLoginResponse {
  token: string;
  userInfo: {
    id: string;
    githubUsername: string;
    role: string;
  };
}

async function apiCall<T>(
  endpoint: string,
  method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE" = "GET",
  body?: unknown,
  token?: string
): Promise<T> {
  const config = useRuntimeConfig();
  const baseUrl = config.public.apiBaseUrl;

  if (!baseUrl) {
    throw { status: 500, message: "API base URL is not configured" } as ApiError;
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
        message: errorData.message || errorData.error || `HTTP ${response.status}`,
      } as ApiError;
    }

    const text = await response.text();
    return (text ? JSON.parse(text) : null) as T;
  } catch (error) {
    if (error instanceof TypeError) {
      throw { status: 0, message: `Network error: ${error.message}` } as ApiError;
    }
    throw error;
  }
}

export function useApiClient() {
  function getAuthToken(): string | null {
    if (import.meta.server) return null;
    const authStore = useAuthStore();
    return authStore.token;
  }

  async function loginFaculty(email: string, password: string): Promise<LoginResponse> {
    return apiCall<LoginResponse>("/auth/login", "POST", { mail: email, password });
  }

  /**
   * Start GitHub OAuth flow for student login.
   * Stores studentId in sessionStorage, generates CSRF state, and redirects to GitHub.
   */
  function initiateGithubOAuth(studentId: string) {
    const config = useRuntimeConfig();
    const clientId = config.public.githubClientId;
    const redirectUri = config.public.githubRedirectUri;

    if (!clientId) {
      throw new Error("GitHub Client ID is not configured");
    }

    // Generate random state for CSRF protection
    const state = crypto.randomUUID();
    sessionStorage.setItem("github_oauth_state", state);
    sessionStorage.setItem("github_student_id", studentId);

    const params = new URLSearchParams({
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "read:user",
      state,
    });

    window.location.href = `https://github.com/login/oauth/authorize?${params.toString()}`;
  }

  /**
   * Complete GitHub OAuth: send code + studentId to backend.
   * Backend exchanges code for access token, fetches GitHub username, and returns JWT.
   */
  async function completeGithubLogin(
    code: string,
    studentId: string
  ): Promise<GithubLoginResponse> {
    return apiCall<GithubLoginResponse>("/auth/github", "POST", {
      code,
      studentId,
    });
  }

  /**
   * Direct GitHub login with access token (if backend already has the token).
   */
  async function loginWithGithub(
    accessToken: string,
    username: string,
    studentId: string
  ): Promise<GithubLoginResponse> {
    return apiCall<GithubLoginResponse>("/auth/github", "POST", {
      accessToken,
      username,
      studentId,
    });
  }

  async function createDeliverable(
    deliverable: CreateDeliverableRequest,
    token?: string
  ): Promise<Deliverable> {
    return apiCall<Deliverable>("/coordinator/deliverables", "POST", deliverable, token);
  }

  async function updateDeliverable(
    deliverableId: string,
    data: UpdateDeliverableRequest,
    token?: string
  ): Promise<Deliverable> {
    return apiCall<Deliverable>(`/coordinator/deliverables/${encodeURIComponent(deliverableId)}`, "PATCH", data, token);
  }

  async function createSprint(
    sprint: CreateSprintRequest,
    token?: string
  ): Promise<Sprint> {
    return apiCall<Sprint>("/coordinator/sprints", "POST", sprint, token);
  }

  async function updateSprintTarget(
    sprintId: string,
    target: number,
    token?: string
  ): Promise<void> {
    return apiCall<void>(`/coordinator/sprints/${encodeURIComponent(sprintId)}/target`, "PATCH", { storyPointTarget: target }, token);
  }

  async function uploadStudents(studentIds: string[], token?: string): Promise<void> {
    return apiCall<void>("/coordinator/students/upload", "POST", { studentIds }, token);
  }

  async function validateResetToken(token: string): Promise<void> {
    return apiCall<void>(`/auth/reset-password?token=${encodeURIComponent(token)}`, "GET");
  }

  async function resetPassword(token: string, newPassword: string): Promise<void> {
    return apiCall<void>("/auth/reset-password", "POST", { token, newPassword });
  }

  async function fetchDeliverables(token?: string): Promise<Deliverable[]> {
    return apiCall<Deliverable[]>("/coordinator/deliverables", "GET", undefined, token);
  }

  async function fetchSprints(token?: string): Promise<Sprint[]> {
    return apiCall<Sprint[]>("/coordinator/sprints", "GET", undefined, token);
  }

  async function createRubric(data: CreateRubricRequest, token?: string): Promise<void> {
    return apiCall<void>(`/coordinator/deliverables/${encodeURIComponent(data.deliverableId)}/rubric`, "POST", { criteria: data.criteria }, token);
  }

  async function fetchRubric(deliverableId: string, token?: string): Promise<RubricCriterionResponse[]> {
    return apiCall<RubricCriterionResponse[]>(`/coordinator/deliverables/${encodeURIComponent(deliverableId)}/rubric`, "GET", undefined, token);
  }

  async function updateRubric(deliverableId: string, criteria: GradingCriterion[], token?: string): Promise<RubricCriterionResponse[]> {
    const payload = criteria.map(c => ({
      criterionName: c.criterionName,
      gradingType: c.gradingType,
      weightPercentage: c.weight,
    }));
    return apiCall<RubricCriterionResponse[]>(`/coordinator/deliverables/${encodeURIComponent(deliverableId)}/rubric`, "PUT", payload, token);
  }

  async function createSprintDeliverableMapping(
    sprintId: string,
    deliverableId: string,
    contributionPercentage: number,
    token?: string
  ): Promise<void> {
    return apiCall<void>(
      `/coordinator/sprints/${encodeURIComponent(sprintId)}/deliverable-mapping`,
      "POST",
      { deliverableId, contributionPercentage },
      token
    );
  }

  async function publishConfig(token?: string): Promise<void> {
    return apiCall<void>("/coordinator/publish", "POST", undefined, token);
  }

  async function registerProfessor(mail: string, token?: string): Promise<{ resetToken: string }> {
    return apiCall<{ resetToken: string }>("/admin/register-professor", "POST", { mail }, token);
  }

  async function fetchCommittees(token?: string): Promise<Committee[]> {
    return apiCall<Committee[]>("/coordinator/committees", "GET", undefined, token);
  }

  async function fetchCommittee(committeeId: string, token?: string): Promise<Committee> {
    return apiCall<Committee>(`/coordinator/committees/${encodeURIComponent(committeeId)}`, "GET", undefined, token);
  }

  async function fetchUnassignedGroups(token?: string): Promise<StudentGroup[]> {
    return apiCall<StudentGroup[]>("/coordinator/groups/unassigned", "GET", undefined, token);
  }

  async function assignGroupsToCommittee(
    committeeId: string,
    groupIds: string[],
    token?: string
  ): Promise<void> {
    return apiCall<void>(
      `/coordinator/committees/${encodeURIComponent(committeeId)}/groups`,
      "POST",
      { groupIds },
      token
    );
  }

  return {
    getAuthToken,
    loginFaculty,
    initiateGithubOAuth,
    completeGithubLogin,
    loginWithGithub,
    createDeliverable,
    updateDeliverable,
    createSprint,
    updateSprintTarget,
    uploadStudents,
    validateResetToken,
    resetPassword,
    fetchDeliverables,
    fetchSprints,
    createRubric,
    fetchRubric,
    updateRubric,
    createSprintDeliverableMapping,
    publishConfig,
    registerProfessor,
    fetchCommittees,
    fetchCommittee,
    fetchUnassignedGroups,
    assignGroupsToCommittee,
  };
}
