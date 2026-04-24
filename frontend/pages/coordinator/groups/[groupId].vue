<script setup lang="ts">
	import { z } from "zod";
	import {
		AlertCircle,
		ArrowLeft,
		GitBranch,
		RefreshCw,
		Server,
		ShieldAlert,
		UserCheck,
		UserMinus,
		UserPlus,
		Users
	} from "lucide-vue-next";
	import type { GroupDetailResponse, MemberResponse } from "~/types/group";
	import type { AdvisorCapacityResponse } from "~/types/advisor";

	definePageMeta({
		middleware: "auth",
		roles: ["Coordinator"],
	});

	const route = useRoute();
	const {
		getAuthToken,
		fetchCoordinatorGroupDetail,
		fetchCoordinatorAdvisors,
		assignCoordinatorAdvisor,
		removeCoordinatorAdvisor,
		updateCoordinatorGroupMembers,
		disbandCoordinatorGroup,
	} = useApiClient();

	const groupId = computed(() => String(route.params.groupId || ""));
	const group = ref<GroupDetailResponse | null>(null);
	const advisors = ref<AdvisorCapacityResponse[]>([]);
	const selectedAdvisorId = ref("");
	const loading = ref(true);
	const advisorsLoading = ref(false);
	const submitting = ref(false);
	const fetchError = ref<string | null>(null);
	const advisorMessage = ref("");
	const removeConfirmationArmed = ref(false);

	const addStudentId = ref("");
	const addSubmitting = ref(false);
	const removeSubmittingStudentId = ref<string | null>(null);
	const disbandSubmitting = ref(false);
	const showDisbandConfirm = ref(false);
	const actionError = ref<string | null>(null);
	const actionMessage = ref("");

	const addMemberSchema = z.object({
		studentId: z
			.string()
			.trim()
			.min(1, "Student ID is required.")
			.regex(/^[0-9]{11}$/, "Student ID must be exactly 11 digits.")
	});

	const selectedAdvisor = computed(() =>
		advisors.value.find((advisor: AdvisorCapacityResponse) => advisor.advisorId === selectedAdvisorId.value) || null
	);

	const hasAssignedAdvisor = computed(() =>
		Boolean(group.value?.advisorId) || group.value?.status === "ADVISOR_ASSIGNED"
	);

	const assignedAdvisor = computed(() => {
		const currentGroup = group.value;
		if (!currentGroup || (!currentGroup.advisorId && !hasAssignedAdvisor.value)) {
			return null;
		}

		if (!currentGroup.advisorId) {
			return {
				advisorId: "",
				name: currentGroup.advisorMail || "Assigned advisor", // Replaced advisorName with advisorMail
				mail: currentGroup.advisorMail || "",
				currentGroupCount: 0,
				capacity: 0,
				atCapacity: false,
			};
		}

		return (
			advisors.value.find((advisor: AdvisorCapacityResponse) => advisor.advisorId === currentGroup.advisorId) ||
			selectedAdvisor.value ||
			{
				advisorId: currentGroup.advisorId,
				name: currentGroup.advisorMail || "Assigned advisor", // Replaced advisorName with advisorMail
				mail: currentGroup.advisorMail || "",
				currentGroupCount: 0,
				capacity: 0,
				atCapacity: false,
			}
		);
	});

	const isDisbanded = computed(() => group.value?.status === "DISBANDED");
	const canEditMembers = computed(() => !isDisbanded.value && !disbandSubmitting.value);

	const createdAtLabel = computed(() => {
		if (!group.value?.createdAt) {
			return "Creation date unavailable";
		}

		return new Intl.DateTimeFormat("en-US", {
			dateStyle: "medium",
			timeStyle: "short",
		}).format(new Date(group.value.createdAt));
	});

	async function loadGroup() {
		fetchError.value = null;
		actionError.value = null;
		actionMessage.value = "";

		try {
			const token = getAuthToken();
			if (!token) {
				throw new Error("Authentication required. Please log in again.");
			}

			group.value = await fetchCoordinatorGroupDetail(groupId.value, token);
			selectedAdvisorId.value = group.value.advisorId || "";
		} catch (error: unknown) {
			const apiError = error as { message?: string };
			group.value = null;
			fetchError.value = apiError.message || "We couldn't load this group.";
		}
	}

	async function loadAdvisors() {
		advisorsLoading.value = true;
		advisorMessage.value = "";

		try {
			const token = getAuthToken();
			if (!token) {
				throw new Error("Authentication required. Please log in again.");
			}

			advisors.value = await fetchCoordinatorAdvisors(token);
		} catch (error: unknown) {
			const apiError = error as { message?: string };
			advisorMessage.value = apiError.message || "Advisors could not be loaded.";
		} finally {
			advisorsLoading.value = false;
		}
	}

	async function refreshPage() {
		loading.value = true;
		await Promise.all([loadGroup(), loadAdvisors()]);
		loading.value = false;
	}

	async function handleAssignAdvisor() {
		if (!group.value || !selectedAdvisorId.value) {
			return;
		}

		submitting.value = true;
		advisorMessage.value = "";

		try {
			const token = getAuthToken();
			if (!token) {
				throw new Error("Authentication required. Please log in again.");
			}

			const response = await assignCoordinatorAdvisor(group.value.id, selectedAdvisorId.value, token);
			const advisor = selectedAdvisor.value;

			group.value = {
				...group.value,
				status: response.status,
				advisorId: response.advisorId,
				advisorMail: advisor?.mail || null,
			};
			removeConfirmationArmed.value = false;
			advisorMessage.value = "Advisor assigned. Group status updated without a reload.";
		} catch (error: unknown) {
			const apiError = error as { message?: string };
			advisorMessage.value = apiError.message || "Advisor assignment failed.";
		} finally {
			submitting.value = false;
		}
	}

	async function handleRemoveAdvisor() {
		if (!group.value) {
			return;
		}

		if (!removeConfirmationArmed.value) {
			removeConfirmationArmed.value = true;
			advisorMessage.value = "Click Confirm Removal to remove the advisor from this group.";
			return;
		}

		submitting.value = true;
		advisorMessage.value = "";

		try {
			const token = getAuthToken();
			if (!token) {
				throw new Error("Authentication required. Please log in again.");
			}

			const response = await removeCoordinatorAdvisor(group.value.id, token);
			group.value = {
				...group.value,
				status: response.status,
				advisorId: null,
				advisorMail: null,
			};
			selectedAdvisorId.value = "";
			removeConfirmationArmed.value = false;
			advisorMessage.value = "Advisor removed. Group status returned to Tools Bound.";
		} catch (error: unknown) {
			const apiError = error as { message?: string };
			advisorMessage.value = apiError.message || "Advisor removal failed.";
		} finally {
			submitting.value = false;
		}
	}

	async function handleAddMember() {
		actionError.value = null;
		actionMessage.value = "";

		const result = addMemberSchema.safeParse({ studentId: addStudentId.value });
		if (!result.success) {
			actionError.value = result.error.flatten().fieldErrors.studentId?.[0] || "Invalid Student ID.";
			return;
		}

		addSubmitting.value = true;
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required. Please log in.");

			const updatedGroup = await updateCoordinatorGroupMembers(
				groupId.value,
				{ studentId: result.data.studentId, action: "ADD" },
				token
			);

			group.value = updatedGroup;
			addStudentId.value = "";
			actionMessage.value = "Member added successfully.";
		} catch (error) {
			actionError.value =
				error && typeof error === "object" && "message" in error
					? String(error.message)
					: "Failed to add member";
		} finally {
			addSubmitting.value = false;
		}
	}

	async function handleRemoveMember(member: MemberResponse) {
		if (!member.studentId) {
			actionError.value = "Member student ID is missing.";
			return;
		}

		actionError.value = null;
		actionMessage.value = "";
		removeSubmittingStudentId.value = member.studentId;

		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required. Please log in.");

			const updatedGroup = await updateCoordinatorGroupMembers(
				groupId.value,
				{ studentId: member.studentId, action: "REMOVE" },
				token
			);

			group.value = updatedGroup;
			actionMessage.value = "Member removed successfully.";
		} catch (error) {
			actionError.value =
				error && typeof error === "object" && "message" in error
					? String(error.message)
					: "Failed to remove member";
		} finally {
			removeSubmittingStudentId.value = null;
		}
	}

	async function handleConfirmDisband() {
		actionError.value = null;
		actionMessage.value = "";
		disbandSubmitting.value = true;

		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required. Please log in.");

			const updatedGroup = await disbandCoordinatorGroup(groupId.value, token);
			group.value = updatedGroup;
			showDisbandConfirm.value = false;
			actionMessage.value = "Group has been disbanded.";
		} catch (error) {
			actionError.value =
				error && typeof error === "object" && "message" in error
					? String(error.message)
					: "Failed to disband group";
		} finally {
			disbandSubmitting.value = false;
		}
	}

	onMounted(refreshPage);
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-6xl space-y-6">
      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div>
            <NuxtLink
              to="/coordinator/dashboard"
              class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-950 dark:text-slate-400 dark:hover:text-white"
            >
              <ArrowLeft class="h-4 w-4" />
              Coordinator Dashboard
            </NuxtLink>
            <h1 class="mt-4 text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
              {{ group?.groupName || "Group Detail" }}
            </h1>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Manage group members, advisor assignment, and group status.
            </p>
          </div>

          <button
            type="button"
            class="inline-flex items-center justify-center gap-2 self-start rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600"
            :disabled="loading"
            @click="refreshPage"
          >
            <RefreshCw class="h-4 w-4" />
            Refresh
          </button>
        </div>
      </header>

      <section
        v-if="loading"
        class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800"
      >
        <div class="animate-pulse space-y-4">
          <div class="h-6 w-48 rounded bg-slate-200 dark:bg-slate-700"></div>
          <div class="h-4 w-full rounded bg-slate-200 dark:bg-slate-700"></div>
          <div class="h-32 rounded-xl bg-slate-100 dark:bg-slate-900"></div>
        </div>
      </section>

      <section
        v-else-if="fetchError"
        class="rounded-2xl border border-red-300 bg-red-50 p-6 shadow-sm dark:border-red-800 dark:bg-red-950/40"
      >
        <div class="flex items-start gap-3">
          <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
          <div>
            <h2 class="text-lg font-semibold text-red-900 dark:text-red-100">
              Unable to load group
            </h2>
            <p class="mt-2 text-sm text-red-800 dark:text-red-300">
              {{ fetchError }}
            </p>
          </div>
        </div>
      </section>

      <template v-else-if="group">
        <!-- Group info + Advisor management -->
        <section class="grid gap-6 lg:grid-cols-[1.2fr_1fr]">
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
                  Term
                </dt>
                <dd class="mt-1 text-sm font-medium text-slate-900 dark:text-white">
                  {{ group.termId || "Unavailable" }}
                </dd>
              </div>
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
                  Jira
                </dt>
                <dd class="mt-1 flex items-center gap-1.5 text-sm font-medium text-slate-900 dark:text-white">
                  <Server class="h-3.5 w-3.5 text-slate-500 dark:text-slate-400" />
                  {{ group.jiraBound ? "Connected" : "Not connected" }}
                </dd>
              </div>
              <div>
                <dt class="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  GitHub
                </dt>
                <dd class="mt-1 flex items-center gap-1.5 text-sm font-medium text-slate-900 dark:text-white">
                  <GitBranch class="h-3.5 w-3.5 text-slate-500 dark:text-slate-400" />
                  {{ group.githubBound ? "Connected" : "Not connected" }}
                </dd>
              </div>
            </dl>
          </article>

          <article class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
            <div class="flex items-start gap-3">
              <UserCheck class="mt-0.5 h-5 w-5 shrink-0 text-slate-700 dark:text-slate-300" />
              <div>
                <h2 class="text-lg font-semibold text-slate-900 dark:text-white">
                  Advisor Management
                </h2>
                <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
                  Select any advisor, including advisors currently at capacity.
                </p>
              </div>
            </div>

            <div class="mt-5 rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/60">
              <p class="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                Assigned Advisor
              </p>
              <p class="mt-2 text-sm font-semibold text-slate-950 dark:text-white">
                {{ assignedAdvisor?.name || "No advisor assigned" }}
              </p>
              <p v-if="assignedAdvisor?.mail" class="mt-1 text-xs text-slate-600 dark:text-slate-400">
                {{ assignedAdvisor.mail }}
              </p>
            </div>

            <label class="mt-5 block text-sm font-medium text-slate-700 dark:text-slate-200" for="advisor-select">
              Advisor
            </label>
            <select
              id="advisor-select"
              v-model="selectedAdvisorId"
              class="mt-2 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200 disabled:cursor-not-allowed disabled:bg-slate-100 dark:border-slate-600 dark:bg-slate-900 dark:text-white dark:focus:border-blue-400 dark:focus:ring-blue-900"
              :disabled="advisorsLoading || submitting"
            >
              <option value="">
                {{ advisorsLoading ? "Loading advisors..." : "Select an advisor" }}
              </option>
              <option
                v-for="advisor in advisors"
                :key="advisor.advisorId"
                :value="advisor.advisorId"
              >
                {{ advisor.name }} ({{ advisor.currentGroupCount }}/{{ advisor.capacity }}){{ advisor.atCapacity ? " - At capacity" : "" }}
              </option>
            </select>

            <p
              v-if="selectedAdvisor?.atCapacity"
              class="mt-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200"
            >
              This advisor is at capacity, but coordinator override can still assign them.
            </p>

            <div class="mt-5 flex flex-col gap-3 sm:flex-row">
              <button
                type="button"
                class="inline-flex items-center justify-center rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-70 dark:bg-blue-700 dark:hover:bg-blue-600"
                :disabled="!selectedAdvisorId || submitting"
                @click="handleAssignAdvisor"
              >
                Assign Advisor
              </button>
              <button
                type="button"
                class="inline-flex items-center justify-center gap-2 rounded-lg border border-red-300 px-4 py-2 text-sm font-medium text-red-700 transition hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-70 dark:border-red-800 dark:text-red-300 dark:hover:bg-red-950/30"
                :disabled="!hasAssignedAdvisor || submitting"
                @click="handleRemoveAdvisor"
              >
                <UserMinus class="h-4 w-4" />
                {{ removeConfirmationArmed ? "Confirm Removal" : "Remove Advisor" }}
              </button>
            </div>

            <p
              v-if="advisorMessage"
              class="mt-4 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
            >
              {{ advisorMessage }}
            </p>
          </article>
        </section>

        <!-- Members with add/remove -->
        <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <div class="mb-4 flex items-center gap-2">
            <Users class="h-5 w-5 text-slate-700 dark:text-slate-300" />
            <h2 class="text-lg font-semibold text-slate-900 dark:text-white">
              Group Members
            </h2>
          </div>

          <div class="mb-4 rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/50">
            <label class="block space-y-2">
              <span class="text-sm font-medium text-slate-700 dark:text-slate-300">Add student by ID</span>
              <div class="flex flex-col gap-2 sm:flex-row">
                <input
                  v-model="addStudentId"
                  type="text"
                  maxlength="11"
                  :disabled="!canEditMembers || addSubmitting"
                  placeholder="Enter 11-digit student ID"
                  class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-slate-500 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-slate-400"
                />
                <button
                  type="button"
                  :disabled="!canEditMembers || addSubmitting"
                  class="inline-flex items-center justify-center gap-1 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
                  @click="handleAddMember"
                >
                  <UserPlus class="h-4 w-4" />
                  {{ addSubmitting ? "Adding..." : "Add Member" }}
                </button>
              </div>
            </label>
          </div>

          <UiMemberList
            :members="group.members"
            :removable="canEditMembers && !removeSubmittingStudentId"
            @remove="handleRemoveMember"
          />
        </section>

        <!-- Disband -->
        <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <h2 class="mb-4 text-lg font-semibold text-slate-900 dark:text-white">Danger Zone</h2>
          <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p class="text-sm text-slate-600 dark:text-slate-400">
              Disbanding removes all members and auto-denies pending invitations. This cannot be undone.
            </p>
            <button
              type="button"
              :disabled="isDisbanded || disbandSubmitting"
              class="inline-flex shrink-0 items-center justify-center gap-2 rounded-lg border border-red-300 bg-red-50 px-4 py-2.5 text-sm font-medium text-red-700 transition hover:bg-red-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-red-800 dark:bg-red-950/40 dark:text-red-300 dark:hover:bg-red-900/40"
              @click="showDisbandConfirm = true"
            >
              <ShieldAlert class="h-4 w-4" />
              {{ isDisbanded ? "Already Disbanded" : "Disband Group" }}
            </button>
          </div>
        </section>

        <!-- Action feedback -->
        <div
          v-if="actionError"
          class="rounded-lg border border-red-300 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300"
        >
          {{ actionError }}
        </div>

        <div
          v-if="actionMessage"
          class="rounded-lg border border-emerald-300 bg-emerald-50 p-4 text-sm text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-300"
        >
          {{ actionMessage }}
        </div>
      </template>
    </div>

    <!-- Disband confirm modal -->
    <div
      v-if="showDisbandConfirm"
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4"
    >
      <div class="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-xl dark:border-slate-700 dark:bg-slate-800">
        <h3 class="text-lg font-semibold text-slate-900 dark:text-white">
          Confirm Disband
        </h3>
        <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
          Are you sure you want to disband <span class="font-semibold">{{ group?.groupName }}</span>?
          This action cannot be undone.
        </p>
        <div class="mt-5 flex gap-2">
          <button
            type="button"
            :disabled="disbandSubmitting"
            class="flex-1 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600"
            @click="showDisbandConfirm = false"
          >
            Cancel
          </button>
          <button
            type="button"
            :disabled="disbandSubmitting"
            class="flex-1 rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-red-700 dark:hover:bg-red-600"
            @click="handleConfirmDisband"
          >
            {{ disbandSubmitting ? "Disbanding..." : "Confirm Disband" }}
          </button>
        </div>
      </div>
    </div>
  </main>
</template>
