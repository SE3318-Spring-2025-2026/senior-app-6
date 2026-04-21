/**
 * Reusable API client composable for making authenticated requests to the backend
 * Note: LoginResponse and User types are defined globally in types/*.d.ts
 */

import { useAuthStore } from '~/stores/auth';
import type { GroupDetailResponse, CreateGroupResponse, CreateGroupRequest } from '~/types/group';
import type { AdvisorRequestItem, AdvisorRequestDetail, AdvisorRespondResponse } from '~/types/advisor';

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
  weightPercentage: number;
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

export interface StudentGroup
{
	id: string;
	name: string;
	advisorApproved: boolean;
	committeeId?: string | null;
}

export interface CoordinatorGroupSummary {
  id: string;
  groupName: string;
  termId?: string;
  status: string;
  memberCount: number;
  jiraBound: boolean;
  githubBound: boolean;
}

export interface CoordinatorGroupMemberActionRequest {
  studentId: string;
  action: "ADD" | "REMOVE";
}

export type AdvisorRequestStatus =
  | "PENDING"
  | "ACCEPTED"
  | "REJECTED"
  | "AUTO_REJECTED"
  | "CANCELLED";

export interface AdvisorCapacityResponse {
  advisorId: string;
  name: string;
  mail: string;
  currentGroupCount: number;
  capacity: number;
  atCapacity?: boolean | null;
}

export interface AdvisorRequestResponse {
  requestId: string;
  groupId?: string;
  advisorId?: string;
  advisorName?: string;
  status: AdvisorRequestStatus;
  sentAt?: string;
  respondedAt?: string | null;
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

export interface CoordinatorAdvisor {
  advisorId: string;
  name: string;
  mail: string;
  currentGroupCount: number;
  capacity: number;
  atCapacity: boolean;
}

export interface AdvisorOverrideResponse {
  groupId: string;
  status: "ADVISOR_ASSIGNED" | "TOOLS_BOUND";
  advisorId: string | null;
}

export interface SanitizationReport {
  disbandedCount: number;
  autoRejectedRequestCount?: number;
  rejectedRequestCount?: number;
  triggeredAt: string;
}

export interface BindJiraRequest {
  jiraSpaceUrl: string;
  jiraEmail: string;
  jiraProjectKey: string;
  jiraApiToken: string;
}

export interface BindGithubRequest {
  githubOrgName: string;
  githubPat: string;
}

export interface ProfessorCommitteeGroup {
  id: string;
  groupName: string;
  memberCount: number;
  advisorName?: string;
}

export interface ProfessorCommitteeDeliverable {
  id: string;
  name: string;
  type: "Proposal" | "SoW" | "Demonstration";
  submissionDeadline: string;
  reviewDeadline: string;
}

export interface ProfessorCommitteeRubricCriterion {
  id: string;
  criterionName: string;
  gradingType: "Binary" | "Soft";
  weight: number;
}

export interface ProfessorCommittee {
  id: string;
  name: string;
  role: "ADVISOR" | "JURY";
  groups: ProfessorCommitteeGroup[];
  deliverables?: ProfessorCommitteeDeliverable[];
  rubricCriteria?: ProfessorCommitteeRubricCriterion[];
}

export interface BindToolResponse {
  groupId: string;
  status: GroupDetailResponse["status"];
  jiraSpaceUrl?: string | null;
  jiraProjectKey?: string | null;
  githubOrgName?: string | null;
  jiraBound: boolean;
  githubBound: boolean;
}

export type InvitationStatus = "PENDING" | "ACCEPTED" | "DECLINED" | "AUTO_DENIED" | "CANCELLED";

export interface StudentSearchResult {
  studentId: string;
  githubUsername: string | null;
}

export interface SentGroupInvitation {
  invitationId: string;
  groupId: string;
  targetStudentId: string;
  status: InvitationStatus;
  sentAt: string;
  respondedAt?: string | null;
}

export interface GroupInvitation {
  invitationId: string;
  groupId: string;
  groupName: string;
  teamLeaderStudentId: string;
  status: InvitationStatus;
  sentAt: string;
  respondedAt?: string;
}

export interface RespondInvitationResponse {
  invitationId: string;
  status: InvitationStatus;
  respondedAt: string;
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

  async function fetchRubric(deliverableId: string, token?: string): Promise<RubricCriterionResponse[]> {
    return apiCall<RubricCriterionResponse[]>(`/coordinator/deliverables/${encodeURIComponent(deliverableId)}/rubric`, "GET", undefined, token);
  }

  async function updateRubric(deliverableId: string, criteria: GradingCriterion[], token?: string): Promise<RubricCriterionResponse[]> {
    return apiCall<RubricCriterionResponse[]>(
      `/coordinator/deliverables/${encodeURIComponent(deliverableId)}/rubric`,
      "POST",
      { criteria: criteria },
      token
    );
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
	): Promise<void>
	{
		return apiCall<void>(
			`/coordinator/committees/${encodeURIComponent(committeeId)}/groups`,
			"POST",
			{ groupIds },
			token);
	}

  async function createGroup(data: CreateGroupRequest, token?: string): Promise<CreateGroupResponse> {
    return apiCall<CreateGroupResponse>("/groups", "POST", data, token);
  }

  async function fetchMyGroup(token?: string): Promise<GroupDetailResponse> {
    return apiCall<GroupDetailResponse>("/groups/my", "GET", undefined, token);
  }

  async function fetchAvailableAdvisors(token?: string): Promise<AdvisorCapacityResponse[]> {
    return apiCall<AdvisorCapacityResponse[]>("/advisors", "GET", undefined, token);
  }

  async function sendAdvisorRequest(
    groupId: string,
    advisorId: string,
    token?: string
  ): Promise<AdvisorRequestResponse> {
    return apiCall<AdvisorRequestResponse>(
      `/groups/${encodeURIComponent(groupId)}/advisor-request`,
      "POST",
      { advisorId },
      token
    );
  }

  async function fetchAdvisorRequest(
    groupId: string,
    token?: string
  ): Promise<AdvisorRequestResponse> {
    return apiCall<AdvisorRequestResponse>(
      `/groups/${encodeURIComponent(groupId)}/advisor-request`,
      "GET",
      undefined,
      token
    );
  }

  async function cancelAdvisorRequest(
    groupId: string,
    token?: string
  ): Promise<AdvisorRequestResponse> {
    return apiCall<AdvisorRequestResponse>(
      `/groups/${encodeURIComponent(groupId)}/advisor-request`,
      "DELETE",
      undefined,
      token
    );
  }

  async function fetchCoordinatorGroups(
    token?: string,
    termId?: string
  ): Promise<CoordinatorGroupSummary[]> {
    const query = termId ? `?termId=${encodeURIComponent(termId)}` : "";
    return apiCall<CoordinatorGroupSummary[]>(`/coordinator/groups${query}`, "GET", undefined, token);
  }

  async function fetchCoordinatorGroupDetail(
    groupId: string,
    token?: string
  ): Promise<GroupDetailResponse> {
    return apiCall<GroupDetailResponse>(
      `/coordinator/groups/${encodeURIComponent(groupId)}`,
      "GET",
      undefined,
      token
    );
  }

  async function fetchCoordinatorAdvisors(token?: string): Promise<CoordinatorAdvisor[]> {
    return apiCall<CoordinatorAdvisor[]>("/coordinator/advisors", "GET", undefined, token);
  }

  async function assignCoordinatorAdvisor(
    groupId: string,
    advisorId: string,
    token?: string
  ): Promise<AdvisorOverrideResponse> {
    return apiCall<AdvisorOverrideResponse>(
      `/coordinator/groups/${encodeURIComponent(groupId)}/advisor`,
      "PATCH",
      { action: "ASSIGN", advisorId },
      token
    );
  }

  async function bindJiraTool(
    groupId: string,
    payload: BindJiraRequest,
    token?: string
  ): Promise<BindToolResponse> {
    return apiCall<BindToolResponse>(
      `/groups/${encodeURIComponent(groupId)}/jira`,
      "POST",
      payload,
      token
    );
  }

  async function removeCoordinatorAdvisor(
    groupId: string,
    token?: string
  ): Promise<AdvisorOverrideResponse> {
    return apiCall<AdvisorOverrideResponse>(
      `/coordinator/groups/${encodeURIComponent(groupId)}/advisor`,
      "PATCH",
      { action: "REMOVE" },
      token
    );
  }

  async function runCoordinatorSanitization(
    force: boolean,
    token?: string
  ): Promise<SanitizationReport> {
    return apiCall<SanitizationReport>(
      "/coordinator/sanitize",
      "POST",
      force ? { force: true } : undefined,
      token
    );
  }

  async function updateCoordinatorGroupMembers(
    groupId: string,
    payload: CoordinatorGroupMemberActionRequest,
    token?: string
  ): Promise<GroupDetailResponse> {
    return apiCall<GroupDetailResponse>(
      `/coordinator/groups/${encodeURIComponent(groupId)}/members`,
      "PATCH",
      payload,
      token
    );
  }

  async function disbandCoordinatorGroup(
    groupId: string,
    token?: string
  ): Promise<GroupDetailResponse> {
    return apiCall<GroupDetailResponse>(
      `/coordinator/groups/${encodeURIComponent(groupId)}/disband`,
      "PATCH",
      undefined,
      token
    );
  }

  async function bindGithubTool(
    groupId: string,
    payload: BindGithubRequest,
    token?: string
  ): Promise<BindToolResponse> {
    return apiCall<BindToolResponse>(
      `/groups/${encodeURIComponent(groupId)}/github`,
      "POST",
      payload,
      token
    );
  }

  /**
   * Fetch all pending invitations for the authenticated student
   */
  async function fetchPendingInvitations(token?: string): Promise<GroupInvitation[]> {
    return apiCall<GroupInvitation[]>("/invitations/pending", "GET", undefined, token);
  }

  /**
   * Respond to an invitation (accept or decline)
   */
  async function respondToInvitation(
    invitationId: string,
    response: "ACCEPT" | "DECLINE",
    token?: string
  ): Promise<RespondInvitationResponse> {
    return apiCall<RespondInvitationResponse>(
      `/invitations/${encodeURIComponent(invitationId)}/respond`,
      "PATCH",
      { accept: response === "ACCEPT" },
      token
    );
  }

  async function fetchAdvisorRequests(token?: string): Promise<AdvisorRequestItem[]> {
    return apiCall<AdvisorRequestItem[]>("/advisor/requests", "GET", undefined, token);
  }

  async function fetchAdvisorRequestDetail(requestId: string, token?: string): Promise<AdvisorRequestDetail> {
    return apiCall<AdvisorRequestDetail>(`/advisor/requests/${encodeURIComponent(requestId)}`, "GET", undefined, token);
  }

  async function respondToAdvisorRequest(requestId: string, accept: boolean, token?: string): Promise<AdvisorRespondResponse> {
    return apiCall<AdvisorRespondResponse>(`/advisor/requests/${encodeURIComponent(requestId)}/respond`, "PATCH", { accept }, token);
  }

  /**
   * Search for ungrouped students by studentId substring (min 3 chars)
   */
  async function searchStudents(q: string, token?: string): Promise<StudentSearchResult[]> {
    return apiCall<StudentSearchResult[]>(`/students/search?q=${encodeURIComponent(q)}`, "GET", undefined, token);
  }

  /**
   * Send a group invitation to a target student (Team Leader only)
   */
  async function sendGroupInvitation(
    groupId: string,
    targetStudentId: string,
    token?: string
  ): Promise<SentGroupInvitation> {
    return apiCall<SentGroupInvitation>(
      `/groups/${encodeURIComponent(groupId)}/invitations`,
      "POST",
      { targetStudentId },
      token
    );
  }

  /**
   * Fetch all outbound invitations sent by the group (Team Leader only)
   */
  async function fetchGroupInvitations(groupId: string, token?: string): Promise<SentGroupInvitation[]> {
    return apiCall<SentGroupInvitation[]>(
      `/groups/${encodeURIComponent(groupId)}/invitations`,
      "GET",
      undefined,
      token
    );
  }

  /**
   * Cancel a pending outbound invitation (Team Leader only)
   */
  async function cancelGroupInvitation(invitationId: string, token?: string): Promise<SentGroupInvitation> {
    return apiCall<SentGroupInvitation>(
      `/invitations/${encodeURIComponent(invitationId)}`,
      "DELETE",
      undefined,
      token
    );
  }

  async function fetchProfessorCommittees(token?: string): Promise<ProfessorCommittee[]> {
    return apiCall<ProfessorCommittee[]>("/professors/me/committees", "GET", undefined, token);
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
    fetchRubric,
    updateRubric,
    createSprintDeliverableMapping,
    publishConfig,
    registerProfessor,
    fetchCommittees,
    fetchCommittee,
    fetchUnassignedGroups,
    assignGroupsToCommittee,
    createGroup,
    fetchMyGroup,
    fetchAvailableAdvisors,
    sendAdvisorRequest,
    fetchAdvisorRequest,
    cancelAdvisorRequest,
    fetchCoordinatorGroups,
    fetchCoordinatorGroupDetail,
    fetchCoordinatorAdvisors,
    assignCoordinatorAdvisor,
    removeCoordinatorAdvisor,
    runCoordinatorSanitization,
    updateCoordinatorGroupMembers,
    disbandCoordinatorGroup,
    bindJiraTool,
    bindGithubTool,
    fetchPendingInvitations,
    respondToInvitation,
    fetchAdvisorRequests,
    fetchAdvisorRequestDetail,
    respondToAdvisorRequest,
    searchStudents,
    sendGroupInvitation,
    fetchGroupInvitations,
    cancelGroupInvitation,
    fetchProfessorCommittees,
  };
}
