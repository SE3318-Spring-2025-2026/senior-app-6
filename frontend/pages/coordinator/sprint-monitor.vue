<script setup lang="ts">
import { AlertCircle, ArrowLeft, Loader2, RefreshCw } from "lucide-vue-next";
import type {
  Sprint,
  SprintGroupOverview,
  SprintOverviewResult,
  SprintRefreshResult,
} from "~/types/sprint";

definePageMeta({
  middleware: "auth",
  roles: ["Coordinator"],
});

const {
  getAuthToken,
  fetchSprints,
  triggerSprintRefresh,
  fetchSprintOverview,
} = useApiClient();

const sprints = ref<Sprint[]>([]);
const selectedSprintId = ref("");
const overview = ref<SprintOverviewResult | null>(null);
const refreshStats = ref<SprintRefreshResult | null>(null);

const isPageLoading = ref(true);
const isOverviewLoading = ref(false);
const isRefreshing = ref(false);

const pageError = ref<string | null>(null);
const actionError = ref<string | null>(null);

const hasGroups = computed(() => (overview.value?.groups?.length ?? 0) > 0);

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Invalid date";
  return date.toLocaleDateString("en-US", {
    weekday: "short",
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Unavailable";
  return date.toLocaleString("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

function gradeText(group: SprintGroupOverview): string {
  if (!group.gradeSubmitted || !group.pointA_grade || !group.pointB_grade) return "—";
  return `${group.pointA_grade}/${group.pointB_grade}`;
}

async function loadOverviewForSelectedSprint() {
  if (!selectedSprintId.value) {
    overview.value = null;
    return;
  }

  isOverviewLoading.value = true;
  actionError.value = null;

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required");
    overview.value = await fetchSprintOverview(selectedSprintId.value, token);
  } catch (err: unknown) {
    const message =
      err && typeof err === "object" && "message" in err
        ? String(err.message)
        : "Failed to load sprint overview.";
    actionError.value = message;
    overview.value = null;
  } finally {
    isOverviewLoading.value = false;
  }
}

async function handleRefresh() {
  if (!selectedSprintId.value || isRefreshing.value) return;

  isRefreshing.value = true;
  actionError.value = null;

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required");

    refreshStats.value = await triggerSprintRefresh(selectedSprintId.value, true, token);
    await loadOverviewForSelectedSprint();
  } catch (err: unknown) {
    const message =
      err && typeof err === "object" && "message" in err
        ? String(err.message)
        : "Failed to refresh sprint tracking.";
    actionError.value = message;
  } finally {
    isRefreshing.value = false;
  }
}

async function loadPageData() {
  isPageLoading.value = true;
  pageError.value = null;
  actionError.value = null;

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required");

    const sprintData = await fetchSprints(token);
    sprints.value = sprintData;

    if (sprintData.length > 0) {
      selectedSprintId.value = sprintData[0].id;
      await loadOverviewForSelectedSprint();
    } else {
      selectedSprintId.value = "";
      overview.value = null;
    }
  } catch (err: unknown) {
    const message =
      err && typeof err === "object" && "message" in err
        ? String(err.message)
        : "Failed to load sprint monitor.";
    pageError.value = message;
  } finally {
    isPageLoading.value = false;
  }
}

watch(selectedSprintId, async (nextId, prevId) => {
  if (!nextId || nextId === prevId) return;
  refreshStats.value = null;
  await loadOverviewForSelectedSprint();
});

onMounted(loadPageData);
</script>

<template>
  <main
    class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8"
  >
    <div class="mx-auto w-full max-w-6xl space-y-6">
      <NuxtLink
        to="/coordinator/dashboard"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to Dashboard
      </NuxtLink>

      <header
        class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg"
      >
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
          Sprint Monitoring
        </h1>
        <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
          Trigger tracking refresh and review per-group sprint overview.
        </p>
      </header>

      <section
        v-if="pageError"
        class="flex items-start gap-3 rounded-2xl border border-red-300 bg-red-50 p-4 text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300"
      >
        <AlertCircle class="mt-0.5 h-5 w-5 shrink-0" />
        <p class="text-sm">{{ pageError }}</p>
      </section>

      <section
        v-else-if="isPageLoading"
        class="flex items-center justify-center rounded-2xl border border-slate-200 bg-white p-12 dark:border-slate-700 dark:bg-slate-800"
      >
        <Loader2 class="h-6 w-6 animate-spin text-blue-600 dark:text-blue-400" />
        <span class="ml-3 text-sm text-slate-600 dark:text-slate-400">Loading sprint monitor...</span>
      </section>

      <template v-else>
        <section
          class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800"
        >
          <div class="grid gap-4 md:grid-cols-[1fr_auto] md:items-end">
            <label class="block space-y-1.5">
              <span class="text-sm font-medium text-slate-700 dark:text-slate-300">Sprint</span>
              <select
                v-model="selectedSprintId"
                :disabled="sprints.length === 0 || isOverviewLoading || isRefreshing"
                class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400"
              >
                <option value="" disabled>Select a sprint</option>
                <option v-for="sprint in sprints" :key="sprint.id" :value="sprint.id">
                  {{ formatDate(sprint.startDate) }} - {{ formatDate(sprint.endDate) }}
                </option>
              </select>
            </label>

            <button
              type="button"
              :disabled="!selectedSprintId || isRefreshing"
              class="inline-flex items-center justify-center rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
              @click="handleRefresh"
            >
              <Loader2 v-if="isRefreshing" class="mr-2 h-4 w-4 animate-spin" />
              <RefreshCw v-else class="mr-2 h-4 w-4" />
              {{ isRefreshing ? "Refreshing..." : "Trigger Refresh" }}
            </button>
          </div>

          <div
            v-if="refreshStats"
            class="mt-4 rounded-xl border border-emerald-300 bg-emerald-50 p-4 dark:border-emerald-800 dark:bg-emerald-950/40"
          >
            <p class="text-sm font-semibold text-emerald-900 dark:text-emerald-200">Refresh completed</p>
            <p class="mt-1 text-sm text-emerald-800 dark:text-emerald-300">
              Groups: {{ refreshStats.groupsProcessed }} · Issues: {{ refreshStats.issuesFetched }} · AI Validations:
              {{ refreshStats.aiValidationsRun }}
            </p>
            <p class="mt-1 text-xs text-emerald-700 dark:text-emerald-400">
              Triggered at: {{ formatDateTime(refreshStats.triggeredAt) }}
            </p>
          </div>

          <div
            v-if="actionError"
            class="mt-4 flex items-start gap-2 rounded-lg border border-red-300 bg-red-50 p-3 dark:border-red-800 dark:bg-red-950/50"
          >
            <AlertCircle class="mt-0.5 h-4 w-4 shrink-0 text-red-600 dark:text-red-400" />
            <p class="text-sm text-red-700 dark:text-red-400">{{ actionError }}</p>
          </div>
        </section>

        <section
          class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800"
        >
          <h2 class="text-lg font-semibold text-slate-900 dark:text-white">Overview</h2>

          <div v-if="isOverviewLoading" class="mt-4 flex items-center text-sm text-slate-600 dark:text-slate-400">
            <Loader2 class="mr-2 h-4 w-4 animate-spin" />
            Loading overview...
          </div>

          <p v-else-if="!selectedSprintId" class="mt-4 text-sm text-slate-600 dark:text-slate-400">
            Select a sprint to see overview details.
          </p>

          <p v-else-if="!hasGroups" class="mt-4 text-sm text-slate-600 dark:text-slate-400">
            No group overview data available for this sprint.
          </p>

          <div v-else class="mt-4 overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-700">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-700/50">
                  <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                    Group
                  </th>
                  <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                    Advisor
                  </th>
                  <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                    Issues
                  </th>
                  <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                    Merged PRs
                  </th>
                  <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                    AI PASS
                  </th>
                  <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                    AI WARN
                  </th>
                  <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                    AI FAIL
                  </th>
                  <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                    Grade
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100 dark:divide-slate-700">
                <tr
                  v-for="group in overview?.groups ?? []"
                  :key="group.groupId"
                  class="hover:bg-slate-50 dark:hover:bg-slate-700"
                >
                  <td class="px-3 py-2 font-medium text-slate-900 dark:text-white">{{ group.groupName }}</td>
                  <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ group.advisorEmail || "—" }}</td>
                  <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ group.totalIssues }}</td>
                  <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ group.mergedPRs }}</td>
                  <td class="px-3 py-2">
                    <span class="rounded-full bg-emerald-100 px-2 py-1 text-xs font-medium text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300">
                      {{ group.aiPassCount }}
                    </span>
                  </td>
                  <td class="px-3 py-2">
                    <span class="rounded-full bg-amber-100 px-2 py-1 text-xs font-medium text-amber-700 dark:bg-amber-900/40 dark:text-amber-300">
                      {{ group.aiWarnCount }}
                    </span>
                  </td>
                  <td class="px-3 py-2">
                    <span class="rounded-full bg-red-100 px-2 py-1 text-xs font-medium text-red-700 dark:bg-red-900/40 dark:text-red-300">
                      {{ group.aiFailCount }}
                    </span>
                  </td>
                  <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ gradeText(group) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>
    </div>
  </main>
</template>
