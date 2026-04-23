<script setup lang="ts">
	import { AlertCircle, ArrowLeft, Crown, Mail, RefreshCw, Send, ShieldAlert, Users, UserRoundPlus, Wrench, XCircle } from "lucide-vue-next";
	import type { AdvisorCapacityResponse, AdvisorRequestResponse } from "~/types/advisor";
	import type { GroupDetailResponse, MemberResponse } from "~/types/group";
	import { useAuthStore } from "~/stores/auth";
	import { usePendingInvitations } from "~/composables/usePendingInvitations";

	definePageMeta({
		middleware: "auth",
		roles: ["Student"],
	});

	type HubState = "loading" | "no-group" | "leader" | "member" | "error";

	const {
		getAuthToken,
		fetchMyGroup,
		fetchAvailableAdvisors,
		sendAdvisorRequest,
		fetchAdvisorRequest,
		cancelAdvisorRequest,
	} = useApiClient();
	const authStore = useAuthStore();

	const state = ref<HubState>("loading");
	const group = ref<GroupDetailResponse | null>(null);
	const errorMessage = ref("");
	const advisors = ref<AdvisorCapacityResponse[]>([]);
	const activeAdvisorRequest = ref<AdvisorRequestResponse | null>(null);
	const advisorPanelLoading = ref(false);
	const advisorListLoading = ref(false);
	const advisorError = ref("");
	const advisorSuccess = ref("");
	const sendingAdvisorId = ref<string | null>(null);
	const cancellingPendingRequest = ref(false);

	const currentStudent = computed(() => (
		authStore.userInfo?.userType === "Student" ? authStore.userInfo : null
	));

	const currentMembership = computed<MemberResponse | null>(() => {
		if (!group.value || !currentStudent.value) {
			return null;
		}

		return (
			group.value.members.find((member) =>
				member.studentId === currentStudent.value?.id ||
				member.studentId === currentStudent.value?.studentId
			) || null
		);
	});

	const createdAtLabel = computed(() => {
		if (!group.value?.createdAt) {
			return "Creation date unavailable";
		}

		return new Intl.DateTimeFormat("en-US", {
			dateStyle: "medium",
			timeStyle: "short",
		}).format(new Date(group.value.createdAt));
	});

	const leaderText = computed(() => {
		const leader = group.value?.members.find((member) => member.role === "TEAM_LEADER");
		return leader?.studentId || "Team leader";
	});

	const isLeader = computed(() => state.value === "leader");
	const hasPendingAdvisorRequest = computed(() => activeAdvisorRequest.value?.status === "PENDING");
	// True only when the group actually has an advisor currently assigned.
	// If the coordinator removed the advisor, advisorId becomes null even if the
	// latest request still carries status ACCEPTED — that's the stale case.
	const isAdvisorAssigned = computed(() => !!group.value?.advisorId);
	const isAcceptedRequestStale = computed(() =>
		activeAdvisorRequest.value?.status === "ACCEPTED" && !isAdvisorAssigned.value,
	);
	const canSendAdvisorRequest = computed(() => (
		isLeader.value &&
		group.value?.status === "TOOLS_BOUND" &&
		!hasPendingAdvisorRequest.value &&
		!cancellingPendingRequest.value
	));

	function parseErrorMessage(error: unknown, fallback: string): string {
		return (
			error &&
			typeof error === "object" &&
			"message" in error &&
			typeof (error as { message?: string }).message === "string"
		)
			? (error as { message: string }).message
			: fallback;
	}

	function formatDateTime(value?: string | null): string {
		if (!value) {
			return "Not available";
		}

		const parsed = new Date(value);
		if (Number.isNaN(parsed.getTime())) {
			return "Invalid date";
		}

		return new Intl.DateTimeFormat("en-US", {
			dateStyle: "medium",
			timeStyle: "short",
		}).format(parsed);
	}

	function formatAdvisorRequestStatus(status?: string): string {
		if (!status) {
			return "UNKNOWN";
		}

		return status
			.replaceAll("_", " ")
			.toLowerCase()
			.replace(/\b\w/g, (char) => char.toUpperCase());
	}

	function advisorRequestStatusClass(status?: string): string {
		if (status === "PENDING") {
			return "border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200";
		}
		if (status === "ACCEPTED") {
			return "border-emerald-300 bg-emerald-50 text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-200";
		}
		if (status === "CANCELLED") {
			return "border-slate-300 bg-slate-50 text-slate-900 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-200";
		}
		return "border-rose-300 bg-rose-50 text-rose-900 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-200";
	}

	function resetAdvisorContext() {
		advisors.value = [];
		activeAdvisorRequest.value = null;
		advisorError.value = "";
		advisorSuccess.value = "";
		advisorPanelLoading.value = false;
		advisorListLoading.value = false;
		sendingAdvisorId.value = null;
		cancellingPendingRequest.value = false;
	}

	async function loadAdvisorRequest(groupId: string, token: string) {
		try {
			activeAdvisorRequest.value = await fetchAdvisorRequest(groupId, token);
		} catch (error: unknown) {
			const apiError = error as { status?: number };
			if (apiError.status === 404) {
				activeAdvisorRequest.value = null;
				return;
			}
			throw error;
		}
	}

	async function loadAvailableAdvisors(token: string) {
		advisorListLoading.value = true;
		try {
			advisors.value = await fetchAvailableAdvisors(token);
		} finally {
			advisorListLoading.value = false;
		}
	}

	async function loadAdvisorContext(groupId: string, token: string) {
		advisorPanelLoading.value = true;
		advisorError.value = "";
		advisorSuccess.value = "";
		try {
			await loadAdvisorRequest(groupId, token);
			if (isLeader.value) {
				await loadAvailableAdvisors(token);
			} else {
				advisors.value = [];
				advisorListLoading.value = false;
			}
		} catch (error: unknown) {
			advisorError.value = parseErrorMessage(error, "We couldn't load advisor data right now.");
		} finally {
			advisorPanelLoading.value = false;
		}
	}

	async function loadGroup() {
		state.value = "loading";
		errorMessage.value = "";
		advisorError.value = "";
		advisorSuccess.value = "";

		try {
			const token = getAuthToken();
			if (!token) {
				throw new Error("Authentication required. Please log in again.");
			}

			const response = await fetchMyGroup(token);
			group.value = response;

			if (currentMembership.value?.role === "TEAM_LEADER") {
				state.value = "leader";
			} else {
				state.value = "member";
			}

			await loadAdvisorContext(response.id, token);
		} catch (error: unknown) {
			const apiError = error as { status?: number; message?: string };

			if (apiError.status === 404) {
				group.value = null;
				state.value = "no-group";
				resetAdvisorContext();
				return;
			}

			group.value = null;
			state.value = "error";
			errorMessage.value = apiError.message || "We couldn't load your group right now.";
			resetAdvisorContext();
		}
	}

	async function handleSendAdvisorRequest(advisor: AdvisorCapacityResponse) {
		if (!group.value || !canSendAdvisorRequest.value) {
			return;
		}

		const token = getAuthToken();
		if (!token) {
			advisorError.value = "Authentication required. Please log in again.";
			return;
		}

		advisorError.value = "";
		advisorSuccess.value = "";
		sendingAdvisorId.value = advisor.advisorId;

		try {
			const response = await sendAdvisorRequest(group.value.id, advisor.advisorId, token);
			activeAdvisorRequest.value = {
				...response,
				advisorId: advisor.advisorId,
				advisorName: advisor.name || advisor.mail,
				status: "PENDING",
				sentAt: response.sentAt || new Date().toISOString(),
			};
			advisorSuccess.value = `Request sent to ${advisor.name || advisor.mail}.`;
		} catch (error: unknown) {
			advisorError.value = parseErrorMessage(error, "Failed to send advisor request.");
		} finally {
			sendingAdvisorId.value = null;
		}
	}

	async function handleCancelAdvisorRequest() {
		if (!group.value || !activeAdvisorRequest.value || activeAdvisorRequest.value.status !== "PENDING") {
			return;
		}

		const token = getAuthToken();
		if (!token) {
			advisorError.value = "Authentication required. Please log in again.";
			return;
		}

		const previousRequest = activeAdvisorRequest.value;
		advisorError.value = "";
		advisorSuccess.value = "";
		cancellingPendingRequest.value = true;

		// Optimistic update: free the UI immediately for a new request flow.
		activeAdvisorRequest.value = null;

		try {
			await cancelAdvisorRequest(group.value.id, token);
			advisorSuccess.value = "Pending request canceled. You can send a new request now.";
		} catch (error: unknown) {
			activeAdvisorRequest.value = previousRequest;
			advisorError.value = parseErrorMessage(error, "Failed to cancel the pending request.");
		} finally {
			cancellingPendingRequest.value = false;
		}
	}

	const { fetchInvitations, count: pendingInvitationCount } = usePendingInvitations();

	onMounted(async () => {
		await Promise.all([loadGroup(), fetchInvitations()]);
	});
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-5xl space-y-6">
      <NuxtLink
        to="/student/dashboard"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-700 transition hover:text-slate-900 dark:text-slate-300 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to Dashboard
      </NuxtLink>

      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div>
            <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
              Group Hub
            </h1>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Check your team status, review the roster, and see what comes next.
            </p>
          </div>

          <div class="flex items-center gap-2 self-start">
            <NuxtLink
              v-if="group"
              to="/student/group/invitations"
              class="relative inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600"
            >
              <Mail class="h-4 w-4" />
              Invitations
              <span
                v-if="pendingInvitationCount > 0"
                class="absolute -right-1.5 -top-1.5 inline-flex h-5 min-w-[1.25rem] items-center justify-center rounded-full bg-blue-600 px-1 text-xs font-semibold text-white dark:bg-blue-500"
              >
                {{ pendingInvitationCount }}
              </span>
            </NuxtLink>

            <NuxtLink
              v-if="isLeader"
              to="/student/group/tools"
              class="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600"
            >
              <Wrench class="h-4 w-4" />
              Tool Binding
            </NuxtLink>

            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600"
              @click="loadGroup"
            >
              <RefreshCw class="h-4 w-4" />
              Refresh
            </button>
          </div>
        </div>
      </header>

      <section
        v-if="state === 'loading'"
        class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800"
      >
        <div class="animate-pulse space-y-4">
          <div class="h-6 w-48 rounded bg-slate-200 dark:bg-slate-700"></div>
          <div class="h-4 w-full rounded bg-slate-200 dark:bg-slate-700"></div>
          <div class="h-4 w-2/3 rounded bg-slate-200 dark:bg-slate-700"></div>
          <div class="h-32 rounded-xl bg-slate-100 dark:bg-slate-900"></div>
        </div>
      </section>

      <section
        v-else-if="state === 'error'"
        class="rounded-2xl border border-red-300 bg-red-50 p-6 shadow-sm dark:border-red-800 dark:bg-red-950/40"
      >
        <div class="flex items-start gap-3">
          <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
          <div>
            <h2 class="text-lg font-semibold text-red-900 dark:text-red-100">
              Unable to load your group
            </h2>
            <p class="mt-2 text-sm text-red-800 dark:text-red-300">
              {{ errorMessage }}
            </p>
          </div>
        </div>
      </section>

      <template v-else-if="state === 'no-group'">
        <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
            <div class="flex items-start gap-4">
              <div class="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-amber-100 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300">
                <UserRoundPlus class="h-6 w-6" />
              </div>
              <div>
                <h2 class="text-xl font-semibold text-slate-900 dark:text-white">
                  You are not in a group yet
                </h2>
                <p class="mt-2 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
                  Create a new group to become the team leader and start building your project roster for the active term.
                </p>
              </div>
            </div>

            <NuxtLink
              to="/student/group/create"
              class="inline-flex items-center justify-center rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
            >
              Create a Group
            </NuxtLink>
          </div>
        </section>

        <NuxtLink
          to="/student/group/invitations"
          class="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:hover:bg-slate-700/60"
        >
          <div class="flex items-center gap-3">
            <div class="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-blue-100 dark:bg-blue-950/50">
              <Mail class="h-5 w-5 text-blue-700 dark:text-blue-300" />
            </div>
            <div>
              <p class="font-semibold text-slate-900 dark:text-white">Group Invitations</p>
              <p class="text-sm text-slate-600 dark:text-slate-400">
                Review invitations from team leaders
              </p>
            </div>
          </div>
          <span
            v-if="pendingInvitationCount > 0"
            class="inline-flex h-6 min-w-[1.5rem] items-center justify-center rounded-full bg-blue-600 px-1.5 text-xs font-semibold text-white dark:bg-blue-500"
          >
            {{ pendingInvitationCount }}
          </span>
          <span v-else class="text-sm text-slate-400 dark:text-slate-500">No pending</span>
        </NuxtLink>
      </template>

      <template v-else-if="group">
        <section class="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
          <article class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
            <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <p class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
                  Current Group
                </p>
                <h2 class="mt-2 text-2xl font-semibold text-slate-900 dark:text-white">
                  {{ group.groupName }}
                </h2>
              </div>
              <UiGroupStatusBadge :status="group.status" />
            </div>

            <dl class="mt-6 grid gap-4 rounded-xl bg-slate-50 p-4 dark:bg-slate-900/60 sm:grid-cols-2">
              <div>
                <dt class="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  Created
                </dt>
                <dd class="mt-1 text-sm font-medium text-slate-900 dark:text-white">
                  {{ createdAtLabel }}
                </dd>
              </div>
              <div>
                <dt class="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  Team Leader
                </dt>
                <dd class="mt-1 text-sm font-medium text-slate-900 dark:text-white">
                  {{ leaderText }}
                </dd>
              </div>
              <div>
                <dt class="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  Jira
                </dt>
                <dd class="mt-1 text-sm font-medium text-slate-900 dark:text-white">
                  {{ group.jiraBound ? "Connected" : "Not connected yet" }}
                </dd>
              </div>
              <div>
                <dt class="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  GitHub
                </dt>
                <dd class="mt-1 text-sm font-medium text-slate-900 dark:text-white">
                  {{ group.githubBound ? "Connected" : "Not connected yet" }}
                </dd>
              </div>
            </dl>
          </article>

          <article
            :class="[
              'rounded-2xl border p-6 shadow-sm',
              state === 'leader'
                ? 'border-blue-200 bg-blue-50 dark:border-blue-800 dark:bg-blue-950/30'
                : 'border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950/30'
            ]"
          >
            <div class="flex items-start gap-3">
              <Crown
                v-if="state === 'leader'"
                class="mt-0.5 h-5 w-5 shrink-0 text-blue-700 dark:text-blue-300"
              />
              <ShieldAlert
                v-else
                class="mt-0.5 h-5 w-5 shrink-0 text-amber-700 dark:text-amber-300"
              />
              <div>
                <h2
                  :class="[
                    'text-lg font-semibold',
                    state === 'leader' ? 'text-blue-950 dark:text-blue-100' : 'text-amber-950 dark:text-amber-100'
                  ]"
                >
                  Advisor Requests
                </h2>
                <p
                  :class="[
                    'mt-2 text-sm',
                    state === 'leader' ? 'text-blue-900 dark:text-blue-200' : 'text-amber-900 dark:text-amber-200'
                  ]"
                >
                  <span v-if="state === 'leader'">
                    Browse available advisors, send a request, and manage the active request for your group.
                  </span>
                  <span v-else>
                    Only the team leader can send or cancel requests. You can monitor request status here.
                  </span>
                </p>
              </div>
            </div>

            <div
              v-if="advisorPanelLoading"
              class="mt-5 rounded-xl border border-slate-200 bg-white/80 p-4 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-900/50 dark:text-slate-400"
            >
              Loading advisor data...
            </div>

            <template v-else>
              <div
                v-if="advisorError"
                class="mt-5 rounded-xl border border-red-300 bg-red-50 p-4 text-sm text-red-800 dark:border-red-800 dark:bg-red-950/40 dark:text-red-300"
              >
                {{ advisorError }}
              </div>

              <div
                v-if="advisorSuccess"
                class="mt-5 rounded-xl border border-emerald-300 bg-emerald-50 p-4 text-sm text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300"
              >
                {{ advisorSuccess }}
              </div>

              <div
                v-if="isAcceptedRequestStale"
                class="mt-5 rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200"
              >
                Your previously assigned advisor was removed by the coordinator. You can send a new request below.
              </div>

              <div
                v-else-if="activeAdvisorRequest"
                class="mt-5 rounded-xl border border-slate-200 bg-white/80 p-4 dark:border-slate-700 dark:bg-slate-900/50"
              >
                <div class="flex items-center justify-between gap-3">
                  <p class="text-sm font-semibold text-slate-900 dark:text-white">
                    Latest Request
                  </p>
                  <span
                    class="rounded-full border px-2.5 py-1 text-xs font-semibold"
                    :class="advisorRequestStatusClass(activeAdvisorRequest.status)"
                  >
                    {{ formatAdvisorRequestStatus(activeAdvisorRequest.status) }}
                  </span>
                </div>
                <p class="mt-3 text-sm text-slate-700 dark:text-slate-300">
                  Advisor:
                  <span class="font-semibold text-slate-900 dark:text-white">
                    {{ activeAdvisorRequest.advisorName || activeAdvisorRequest.advisorId || "Unknown advisor" }}
                  </span>
                </p>
                <p class="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  Sent: {{ formatDateTime(activeAdvisorRequest.sentAt) }}
                </p>
                <p
                  v-if="activeAdvisorRequest.respondedAt"
                  class="mt-1 text-xs text-slate-500 dark:text-slate-400"
                >
                  Responded: {{ formatDateTime(activeAdvisorRequest.respondedAt) }}
                </p>

                <button
                  v-if="state === 'leader' && activeAdvisorRequest.status === 'PENDING'"
                  type="button"
                  class="mt-4 inline-flex items-center justify-center gap-2 rounded-lg border border-red-300 bg-white px-3.5 py-2 text-sm font-medium text-red-700 transition hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-red-700 dark:bg-slate-900 dark:text-red-300 dark:hover:bg-red-950/40"
                  :disabled="cancellingPendingRequest"
                  @click="handleCancelAdvisorRequest"
                >
                  <XCircle class="h-4 w-4" />
                  {{ cancellingPendingRequest ? "Canceling..." : "Cancel Request" }}
                </button>
              </div>

              <div
                v-else-if="!isAcceptedRequestStale"
                class="mt-5 rounded-xl border border-slate-200 bg-white/80 p-4 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-900/50 dark:text-slate-400"
              >
                No advisor request has been submitted yet.
              </div>

              <template v-if="state === 'leader'">
                <div
                  v-if="group.status === 'FORMING' || group.status === 'TOOLS_PENDING'"
                  class="mt-5 rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200"
                >
                  Your group must be in
                  <span class="font-semibold">TOOLS_BOUND</span>
                  status before requesting an advisor.
                </div>

                <div
                  v-else-if="group.status === 'ADVISOR_ASSIGNED'"
                  class="mt-5 rounded-xl border border-green-200 bg-green-50 p-4 text-sm text-green-900 dark:border-green-800 dark:bg-green-950/40 dark:text-green-200"
                >
                  Your group already has an assigned advisor.
                </div>

                <div
                  v-else-if="hasPendingAdvisorRequest"
                  class="mt-5 rounded-xl border border-blue-200 bg-white/80 p-4 text-sm text-slate-700 dark:border-blue-800 dark:bg-slate-900/50 dark:text-slate-300"
                >
                  You already have a pending request. Cancel it first to send another one.
                </div>

                <div v-else class="mt-5 space-y-3">
                  <p class="text-sm font-semibold text-slate-900 dark:text-white">
                    Available Advisors
                  </p>

                  <div
                    v-if="advisorListLoading"
                    class="rounded-xl border border-slate-200 bg-white/80 p-4 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-900/50 dark:text-slate-400"
                  >
                    Loading advisor list...
                  </div>

                  <div
                    v-else-if="advisors.length === 0"
                    class="rounded-xl border border-slate-200 bg-white/80 p-4 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-900/50 dark:text-slate-400"
                  >
                    No advisors currently have available capacity.
                  </div>

                  <div v-else class="space-y-3">
                    <div
                      v-for="advisor in advisors"
                      :key="advisor.advisorId"
                      class="rounded-xl border border-slate-200 bg-white/80 p-4 dark:border-slate-700 dark:bg-slate-900/50"
                    >
                      <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                          <p class="text-sm font-semibold text-slate-900 dark:text-white">
                            {{ advisor.name || advisor.mail }}
                          </p>
                          <p class="mt-1 text-xs text-slate-600 dark:text-slate-400">
                            {{ advisor.mail }} • {{ advisor.currentGroupCount }}/{{ advisor.capacity }} groups
                          </p>
                        </div>
                        <button
                          type="button"
                          class="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-3.5 py-2 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
                          :disabled="sendingAdvisorId !== null || cancellingPendingRequest"
                          @click="handleSendAdvisorRequest(advisor)"
                        >
                          <Send class="h-4 w-4" />
                          {{ sendingAdvisorId === advisor.advisorId ? "Sending..." : "Send Request" }}
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </template>
            </template>
          </article>
        </section>

        <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <div class="mb-4 flex items-center gap-2">
            <Users class="h-5 w-5 text-slate-700 dark:text-slate-300" />
            <h2 class="text-lg font-semibold text-slate-900 dark:text-white">
              Group Members
            </h2>
          </div>

          <UiMemberList :members="group.members" />
        </section>

      </template>
    </div>
  </main>
</template>
