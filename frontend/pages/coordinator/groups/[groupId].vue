<script setup lang="ts">
	import { AlertCircle, ArrowLeft, RefreshCw, UserCheck, UserMinus, Users } from "lucide-vue-next";
	import type { CoordinatorAdvisor } from "~/composables/useApiClient";
	import type { GroupDetailResponse } from "~/types/group";

	definePageMeta({
		middleware: "auth",
		roles: ["Coordinator"],
	});

	const route = useRoute();
	const {
		getAuthToken,
		fetchCoordinatorGroup,
		fetchCoordinatorAdvisors,
		assignCoordinatorAdvisor,
		removeCoordinatorAdvisor,
	} = useApiClient();

	const groupId = computed(() => String(route.params.groupId || ""));
	const group = ref<GroupDetailResponse | null>(null);
	const advisors = ref<CoordinatorAdvisor[]>([]);
	const selectedAdvisorId = ref("");
	const loading = ref(true);
	const advisorsLoading = ref(false);
	const submitting = ref(false);
	const errorMessage = ref("");
	const advisorMessage = ref("");
	const removeConfirmationArmed = ref(false);

	const selectedAdvisor = computed(() =>
		advisors.value.find((advisor) => advisor.advisorId === selectedAdvisorId.value) || null
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
				name: currentGroup.advisorName || "Assigned advisor",
				mail: currentGroup.advisorMail || "",
				currentGroupCount: 0,
				capacity: 0,
				atCapacity: false,
			};
		}

		return (
			advisors.value.find((advisor) => advisor.advisorId === currentGroup.advisorId) ||
			selectedAdvisor.value ||
			{
				advisorId: currentGroup.advisorId,
				name: currentGroup.advisorName || "Assigned advisor",
				mail: currentGroup.advisorMail || "",
				currentGroupCount: 0,
				capacity: 0,
				atCapacity: false,
			}
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

	async function loadGroup() {
		errorMessage.value = "";

		try {
			const token = getAuthToken();
			if (!token) {
				throw new Error("Authentication required. Please log in again.");
			}

			group.value = await fetchCoordinatorGroup(groupId.value, token);
			selectedAdvisorId.value = group.value.advisorId || "";
		} catch (error: unknown) {
			const apiError = error as { message?: string };
			group.value = null;
			errorMessage.value = apiError.message || "We couldn't load this group.";
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
				advisorName: advisor?.name || null,
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
				advisorName: null,
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

	onMounted(refreshPage);
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-5xl space-y-6">
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
              Review group status and manage advisor assignment.
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
        v-else-if="errorMessage"
        class="rounded-2xl border border-red-300 bg-red-50 p-6 shadow-sm dark:border-red-800 dark:bg-red-950/40"
      >
        <div class="flex items-start gap-3">
          <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
          <div>
            <h2 class="text-lg font-semibold text-red-900 dark:text-red-100">
              Unable to load group
            </h2>
            <p class="mt-2 text-sm text-red-800 dark:text-red-300">
              {{ errorMessage }}
            </p>
          </div>
        </div>
      </section>

      <template v-else-if="group">
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
                <dd class="mt-1 text-sm font-medium text-slate-900 dark:text-white">
                  {{ group.jiraBound ? "Connected" : "Not connected" }}
                </dd>
              </div>
              <div>
                <dt class="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  GitHub
                </dt>
                <dd class="mt-1 text-sm font-medium text-slate-900 dark:text-white">
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
