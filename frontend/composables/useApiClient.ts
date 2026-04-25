import { useAuthStore } from '~/stores/auth';
import type { ApiError } from '~/types/api';
import type { GithubLoginResponse, LoginResponse } from '~/types/auth';
import type {
  GroupDetailResponse,
  CreateGroupRequest,
  GroupSummaryResponse,
  CoordinatorGroupMemberActionRequest,
} from '~/types/group';
import type {
  AdvisorRequestItem,
  AdvisorRequestDetail,
  AdvisorRespondResponse,
  AdvisorCapacityResponse,
  AdvisorRequestResponse,
  AdvisorOverrideResponse,
} from '~/types/advisor';
import type {
  CommitteeSummaryResponse,
  CommitteeDetailResponse,
  CreateCommitteeRequest,
  AddProfessorsToCommitteeRequest,
  AddGroupsToCommitteeRequest,
  ProfessorCommittee,
	CommitteeProfessorAssignment,
} from '~/types/committee';
import type {
  Deliverable,
  CreateDeliverableRequest,
  UpdateDeliverableRequest,
} from '~/types/deliverable';
import type { Sprint, CreateSprintRequest, ActiveSprintResponse, SprintTrackingResponse } from '~/types/sprint';
import type { GradingCriterion, RubricCriterionResponse } from '~/types/rubric';
import type {
  GroupInvitation,
  SentGroupInvitation,
  InvitationActionResponse,
} from '~/types/invitation';
import type { StudentSearchResult } from '~/types/student';
import type { BindJiraRequest, BindGithubRequest, BindToolResponse } from '~/types/tools';
import type { SanitizationReport } from '~/types/sanitization';

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

  function initiateGithubOAuth(studentId: string) {
    const config = useRuntimeConfig();
    const clientId = config.public.githubClientId;
    const redirectUri = config.public.githubRedirectUri;

    if (!clientId) {
      throw new Error("GitHub Client ID is not configured");
    }

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

  async function completeGithubLogin(
    code: string,
    studentId: string
  ): Promise<GithubLoginResponse> {
    return apiCall<GithubLoginResponse>("/auth/github", "POST", {
      code,
      studentId,
    });
  }

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

  async function fetchActiveSprint(token?: string): Promise<ActiveSprintResponse> {
    return apiCall<ActiveSprintResponse>("/sprints/active", "GET", undefined, token);
  }

  async function fetchSprintTracking(
    groupId: string,
    sprintId: string,
    token?: string
  ): Promise<SprintTrackingResponse> {
    return apiCall<SprintTrackingResponse>(
      `/groups/${encodeURIComponent(groupId)}/sprints/${encodeURIComponent(sprintId)}/tracking`,
      "GET",
      undefined,
      token
    );
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

  async function createGroup(data: CreateGroupRequest, token?: string): Promise<GroupDetailResponse> {
    return apiCall<GroupDetailResponse>("/groups", "POST", data, token);
  }

  async function fetchMyGroup(token?: string): Promise<GroupDetailResponse> {
    return apiCall<GroupDetailResponse>("/groups/my", "GET", undefined, token);
  }

  async function fetchAvailableAdvisors(token?: string): Promise<AdvisorCapacityResponse[]> {
    return apiCall<AdvisorCapacityResponse[]>("/advisor", "GET", undefined, token);
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
  ): Promise<GroupSummaryResponse[]> {
    const query = termId ? `?termId=${encodeURIComponent(termId)}` : "";
    return apiCall<GroupSummaryResponse[]>(`/coordinator/groups${query}`, "GET", undefined, token);
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

  async function fetchCoordinatorAdvisors(token?: string): Promise<AdvisorCapacityResponse[]> {
    return apiCall<AdvisorCapacityResponse[]>("/coordinator/advisors", "GET", undefined, token);
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

  async function fetchPendingInvitations(token?: string): Promise<GroupInvitation[]> {
    return apiCall<GroupInvitation[]>("/invitations/pending", "GET", undefined, token);
  }

  async function respondToInvitation(
    invitationId: string,
    response: "ACCEPT" | "DECLINE",
    token?: string
  ): Promise<InvitationActionResponse> {
    return apiCall<InvitationActionResponse>(
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

  async function searchStudents(q: string, token?: string): Promise<StudentSearchResult[]> {
    return apiCall<StudentSearchResult[]>(`/students/search?q=${encodeURIComponent(q)}`, "GET", undefined, token);
  }

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

  async function fetchGroupInvitations(groupId: string, token?: string): Promise<SentGroupInvitation[]> {
    return apiCall<SentGroupInvitation[]>(
      `/groups/${encodeURIComponent(groupId)}/invitations`,
      "GET",
      undefined,
      token
    );
  }

  async function cancelGroupInvitation(invitationId: string, token?: string): Promise<SentGroupInvitation> {
    return apiCall<SentGroupInvitation>(
      `/invitations/${encodeURIComponent(invitationId)}`,
      "DELETE",
      undefined,
      token
    );
  }

  async function createCommittee(
    payload: CreateCommitteeRequest,
    token?: string
  ): Promise<CommitteeSummaryResponse> {
    return apiCall<CommitteeSummaryResponse>("/committees", "POST", payload, token);
  }

  async function fetchCommittees(termId?: string, token?: string): Promise<CommitteeSummaryResponse[]> {
    const query = termId ? `?termId=${encodeURIComponent(termId)}` : "";
    return apiCall<CommitteeSummaryResponse[]>(`/committees${query}`, "GET", undefined, token);
  }

  async function fetchCommitteeDetail(
    committeeId: string,
    token?: string
  ): Promise<CommitteeDetailResponse> {
    return apiCall<CommitteeDetailResponse>(
      `/committees/${encodeURIComponent(committeeId)}`,
      "GET",
      undefined,
      token
    );
  }

  async function addCommitteeProfessors(
    committeeId: string,
    payload: AddProfessorsToCommitteeRequest,
    token?: string
  ): Promise<void> {
    await apiCall<void>(
      `/committees/${encodeURIComponent(committeeId)}/professors`,
      "POST",
      payload,
      token
    );
  }

  async function addCommitteeGroups(
    committeeId: string,
    payload: AddGroupsToCommitteeRequest,
    token?: string
  ): Promise<void> {
    await apiCall<void>(
      `/committees/${encodeURIComponent(committeeId)}/groups`,
      "POST",
      payload,
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
    createCommittee,
    fetchCommittees,
    fetchCommittee: fetchCommitteeDetail,
    fetchCommitteeDetail,
    addCommitteeProfessors,
    addCommitteeGroups,
    fetchProfessorCommittees,
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
    fetchActiveSprint,
    fetchSprintTracking,
    fetchPendingInvitations,
    respondToInvitation,
    fetchAdvisorRequests,
    fetchAdvisorRequestDetail,
    respondToAdvisorRequest,
    searchStudents,
    sendGroupInvitation,
    fetchGroupInvitations,
    cancelGroupInvitation
  };
}
