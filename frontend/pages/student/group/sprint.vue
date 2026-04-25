<script setup lang="ts">
import { AlertCircle, ArrowLeft, GitMerge, RefreshCw } from "lucide-vue-next";
import type { ActiveSprintResponse, SprintTrackingIssue } from "~/types/sprint";

definePageMeta({
  middleware: "auth",
  roles: ["Student"],
});

type PageState = "loading" | "no-sprint" | "loaded" | "error";

const { getAuthToken, fetchMyGroup, fetchActiveSprint, fetchSprintTracking } = useApiClient();

const state = ref<PageState>("loading");
const sprint = ref<ActiveSprintResponse | null>(null);
const issues = ref<SprintTrackingIssue[]>([]);
const errorMessage = ref("");

const sprintDateRange = computed(() => {
  if (!sprint.value) return "";
  const fmt = (d: string) =>
    new Intl.DateTimeFormat("en-US", { dateStyle: "medium" }).format(new Date(d));
  return `${fmt(sprint.value.startDate)} – ${fmt(sprint.value.endDate)}`;
});

async function loadPage() {
  state.value = "loading";
  errorMessage.value = "";
  issues.value = [];
  sprint.value = null;

  const token = getAuthToken();
  if (!token) {
    errorMessage.value = "Authentication required. Please log in again.";
    state.value = "error";
    return;
  }

  try {
    const [group, activeSprint] = await Promise.all([
      fetchMyGroup(token),
      fetchActiveSprint(token),
    ]);

    sprint.value = activeSprint;

    const tracking = await fetchSprintTracking(group.id, activeSprint.id, token);
    issues.value = tracking.issues ?? [];
    state.value = "loaded";
  } catch (err: unknown) {
    const apiError = err as { status?: number; message?: string };
    if (apiError.status === 404) {
      state.value = "no-sprint";
      return;
    }
    errorMessage.value = apiError.message ?? "We couldn't load sprint data right now.";
    state.value = "error";
  }
}

onMounted(loadPage);
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-5xl space-y-6">
      <NuxtLink
        to="/student/group"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-700 transition hover:text-slate-900 dark:text-slate-300 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to Group Hub
      </NuxtLink>

      <!-- Page header -->
      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div>
            <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
              Sprint Monitor
            </h1>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Track story completions and AI validation results for the active sprint.
            </p>
          </div>

          <button
            type="button"
            class="inline-flex items-center gap-2 self-start rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600"
            @click="loadPage"
          >
            <RefreshCw class="h-4 w-4" />
            Refresh
          </button>
        </div>
      </header>

      <!-- Loading skeleton -->
      <section
        v-if="state === 'loading'"
        class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800"
      >
        <div class="animate-pulse space-y-4">
          <div class="h-6 w-48 rounded bg-slate-200 dark:bg-slate-700"></div>
          <div class="h-4 w-72 rounded bg-slate-200 dark:bg-slate-700"></div>
          <div class="h-48 rounded-xl bg-slate-100 dark:bg-slate-900"></div>
        </div>
      </section>

      <!-- Error state -->
      <section
        v-else-if="state === 'error'"
        class="rounded-2xl border border-red-300 bg-red-50 p-6 shadow-sm dark:border-red-800 dark:bg-red-950/40"
      >
        <div class="flex items-start gap-3">
          <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
          <div>
            <h2 class="text-lg font-semibold text-red-900 dark:text-red-100">
              Unable to load sprint data
            </h2>
            <p class="mt-2 text-sm text-red-800 dark:text-red-300">
              {{ errorMessage }}
            </p>
          </div>
        </div>
      </section>

      <!-- No active sprint -->
      <section
        v-else-if="state === 'no-sprint'"
        class="rounded-2xl border border-slate-200 bg-white p-8 shadow-sm dark:border-slate-700 dark:bg-slate-800"
      >
        <div class="flex flex-col items-center gap-3 text-center">
          <div class="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 dark:bg-slate-700">
            <GitMerge class="h-6 w-6 text-slate-500 dark:text-slate-400" />
          </div>
          <h2 class="text-lg font-semibold text-slate-900 dark:text-white">
            No active sprint found
          </h2>
          <p class="max-w-sm text-sm text-slate-600 dark:text-slate-400">
            There is no sprint currently running. Check back once a sprint has been started by the coordinator.
          </p>
        </div>
      </section>

      <!-- Loaded -->
      <template v-else-if="state === 'loaded' && sprint">
        <!-- Sprint header card -->
        <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <p class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
            Active Sprint
          </p>
          <dl class="mt-4 grid gap-4 sm:grid-cols-3">
            <div>
              <dt class="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                Dates
              </dt>
              <dd class="mt-1 text-sm font-medium text-slate-900 dark:text-white">
                {{ sprintDateRange }}
              </dd>
            </div>
            <div>
              <dt class="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                Story Point Target
              </dt>
              <dd class="mt-1 text-sm font-medium text-slate-900 dark:text-white">
                {{ sprint.storyPointTarget ?? "—" }}
              </dd>
            </div>
            <div>
              <dt class="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                Days Remaining
              </dt>
              <dd class="mt-1 text-sm font-medium text-slate-900 dark:text-white">
                {{ sprint.daysRemaining != null ? sprint.daysRemaining : "—" }}
              </dd>
            </div>
          </dl>
        </section>

        <!-- Issue tracking table -->
        <section class="rounded-2xl border border-slate-200 bg-white shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <div class="border-b border-slate-200 px-6 py-4 dark:border-slate-700">
            <h2 class="text-base font-semibold text-slate-900 dark:text-white">
              Issue Tracking
            </h2>
          </div>

          <!-- Empty state -->
          <div
            v-if="issues.length === 0"
            class="px-6 py-12 text-center"
          >
            <p class="text-sm text-slate-500 dark:text-slate-400">
              Tracking data will be available after the sprint ends.
            </p>
          </div>

          <!-- Table -->
          <div v-else class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-900/50">
                  <th class="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                    Issue Key
                  </th>
                  <th class="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                    Assignee
                  </th>
                  <th class="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                    Story Points
                  </th>
                  <th class="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                    PR Merged
                  </th>
                  <th class="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                    AI PR Review
                  </th>
                  <th class="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                    AI Diff Match
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100 dark:divide-slate-700/60">
                <tr
                  v-for="issue in issues"
                  :key="issue.issueKey"
                  class="transition-colors hover:bg-slate-50 dark:hover:bg-slate-700/30"
                >
                  <td class="px-6 py-4 font-medium text-slate-900 dark:text-white">
                    {{ issue.issueKey }}
                  </td>
                  <td class="px-6 py-4 text-slate-700 dark:text-slate-300">
                    {{ issue.assignee || "—" }}
                  </td>
                  <td class="px-6 py-4 text-slate-700 dark:text-slate-300">
                    {{ issue.storyPoints }}
                  </td>
                  <td class="px-6 py-4">
                    <span
                      class="inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium whitespace-nowrap"
                      :class="issue.prMerged
                        ? 'bg-emerald-100 text-emerald-800 border-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-300 dark:border-emerald-800'
                        : 'bg-slate-100 text-slate-500 border-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:border-slate-700'"
                    >
                      {{ issue.prMerged ? "Merged" : "Not merged" }}
                    </span>
                  </td>
                  <td class="px-6 py-4">
                    <AiResultBadge :result="issue.aiPrReview" />
                  </td>
                  <td class="px-6 py-4">
                    <AiResultBadge :result="issue.aiDiffMatch" />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>
    </div>
  </main>
</template>
