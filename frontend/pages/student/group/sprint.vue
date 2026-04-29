<script setup lang="ts">
import { AlertCircle, ArrowLeft, ChevronDown, ChevronRight, ExternalLink, GitMerge, RefreshCw } from "lucide-vue-next";
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
const expandedIssueKey = ref<string | null>(null);

// ── Date range label ────────────────────────────────────────────────────────
const sprintDateRange = computed(() => {
  if (!sprint.value) return "";
  const fmt = (d: string) =>
    new Intl.DateTimeFormat("en-US", { dateStyle: "medium" }).format(new Date(d));
  return `${fmt(sprint.value.startDate)} – ${fmt(sprint.value.endDate)}`;
});

// ── Progress bar computeds ───────────────────────────────────────────────────
const timeElapsedPct = computed(() => {
  if (!sprint.value) return 0;
  const start = new Date(sprint.value.startDate).getTime();
  const end = new Date(sprint.value.endDate).getTime();
  const now = Date.now();
  if (end <= start) return 0;
  return Math.min(100, Math.max(0, ((now - start) / (end - start)) * 100));
});

const completedSP = computed(() =>
  issues.value.filter((i) => i.prMerged).reduce((sum, i) => sum + i.storyPoints, 0),
);

const workDonePct = computed(() => {
  const target = sprint.value?.storyPointTarget;
  if (!target || target <= 0) return 0;
  return Math.min(100, Math.max(0, (completedSP.value / target) * 100));
});

// ── Row toggle ───────────────────────────────────────────────────────────────
function hasDetail(issue: SprintTrackingIssue): boolean {
  return !!(
    issue.prNumber ||
    issue.branchName ||
    issue.prUrl ||
    issue.aiPrReviewNote ||
    issue.aiDiffMatchNote
  );
}

function toggleRow(issueKey: string) {
  expandedIssueKey.value = expandedIssueKey.value === issueKey ? null : issueKey;
}

// ── Data loading ─────────────────────────────────────────────────────────────
async function loadPage() {
  state.value = "loading";
  errorMessage.value = "";
  issues.value = [];
  sprint.value = null;
  expandedIssueKey.value = null;

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

    const tracking = await fetchSprintTracking(group.id, activeSprint.sprintId, token);
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

        <!-- Sprint status progress bars -->
        <section
          v-if="issues.length > 0"
          class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg"
        >
          <h2 class="text-base font-semibold text-slate-900 dark:text-white">
            Sprint Status
          </h2>

          <div class="mt-5 space-y-5">
            <!-- Time elapsed -->
            <div>
              <div class="mb-2 flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
                <span>Time elapsed</span>
                <span class="font-medium text-slate-700 dark:text-slate-300">
                  {{ Math.round(timeElapsedPct) }}%
                </span>
              </div>
              <div class="h-2.5 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-700">
                <div
                  class="h-full rounded-full bg-gradient-to-r from-blue-500 to-blue-400 transition-all duration-500"
                  :style="{ width: `${timeElapsedPct}%` }"
                ></div>
              </div>
              <div class="mt-1.5 flex justify-between text-xs text-slate-400 dark:text-slate-500">
                <span>{{ new Intl.DateTimeFormat("en-US", { dateStyle: "medium" }).format(new Date(sprint.startDate)) }}</span>
                <span>{{ new Intl.DateTimeFormat("en-US", { dateStyle: "medium" }).format(new Date(sprint.endDate)) }}</span>
              </div>
            </div>

            <!-- Work done -->
            <div>
              <div class="mb-2 flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
                <span>Work done</span>
                <span class="font-medium text-slate-700 dark:text-slate-300">
                  {{ completedSP }} / {{ sprint.storyPointTarget ?? "?" }} SP
                </span>
              </div>
              <div class="h-2.5 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-700">
                <div
                  class="h-full rounded-full bg-gradient-to-r from-emerald-500 to-emerald-400 transition-all duration-500"
                  :style="{ width: `${workDonePct}%` }"
                ></div>
              </div>
              <div class="mt-1.5 flex justify-between text-xs text-slate-400 dark:text-slate-500">
                <span>0 SP</span>
                <span>{{ sprint.storyPointTarget ?? "?" }} SP target</span>
              </div>
            </div>
          </div>
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
                  <!-- expand toggle column -->
                  <th class="w-10 px-3 py-3"></th>
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
                <template
                  v-for="issue in issues"
                  :key="issue.issueKey"
                >
                  <!-- Main row -->
                  <tr
                    class="transition-colors"
                    :class="[
                      hasDetail(issue) ? 'cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-700/30' : 'hover:bg-slate-50 dark:hover:bg-slate-700/30',
                      expandedIssueKey === issue.issueKey ? 'bg-slate-50 dark:bg-slate-700/20' : '',
                    ]"
                    @click="hasDetail(issue) ? toggleRow(issue.issueKey) : undefined"
                  >
                    <!-- chevron -->
                    <td class="w-10 px-3 py-4 text-slate-400 dark:text-slate-500">
                      <template v-if="hasDetail(issue)">
                        <ChevronDown
                          v-if="expandedIssueKey === issue.issueKey"
                          class="h-4 w-4"
                        />
                        <ChevronRight v-else class="h-4 w-4" />
                      </template>
                    </td>
                    <td class="px-6 py-4 font-medium text-slate-900 dark:text-white">
                      {{ issue.issueKey }}
                    </td>
                    <td class="px-6 py-4 text-slate-700 dark:text-slate-300">
                      {{ issue.assigneeGithubUsername || "—" }}
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
                      <AiResultBadge :result="issue.aiPrResult ?? 'PENDING'" />
                    </td>
                    <td class="px-6 py-4">
                      <AiResultBadge :result="issue.aiDiffResult ?? 'PENDING'" />
                    </td>
                  </tr>

                  <!-- Expanded detail row -->
                  <tr
                    v-if="expandedIssueKey === issue.issueKey && hasDetail(issue)"
                    class="bg-slate-50 dark:bg-slate-900/40"
                  >
                    <td colspan="7" class="px-6 pb-5 pt-4">
                      <div class="grid gap-4 sm:grid-cols-2">
                        <!-- PR info -->
                        <div class="rounded-xl border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-800">
                          <p class="mb-3 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                            Pull Request
                          </p>
                          <dl class="space-y-2 text-sm">
                            <div v-if="issue.prNumber" class="flex justify-between gap-2">
                              <dt class="text-slate-500 dark:text-slate-400">PR Number</dt>
                              <dd class="font-medium text-slate-900 dark:text-white">#{{ issue.prNumber }}</dd>
                            </div>
                            <div v-if="issue.branchName" class="flex justify-between gap-2">
                              <dt class="text-slate-500 dark:text-slate-400">Branch</dt>
                              <dd class="max-w-[14rem] truncate font-mono text-xs font-medium text-slate-900 dark:text-white">
                                {{ issue.branchName }}
                              </dd>
                            </div>
                            <div v-if="issue.prUrl" class="flex justify-between gap-2">
                              <dt class="text-slate-500 dark:text-slate-400">Link</dt>
                              <dd>
                                <a
                                  :href="issue.prUrl"
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  class="inline-flex items-center gap-1 text-blue-600 hover:underline dark:text-blue-400"
                                  @click.stop
                                >
                                  Open PR
                                  <ExternalLink class="h-3 w-3" />
                                </a>
                              </dd>
                            </div>
                            <p v-if="!issue.prNumber && !issue.branchName && !issue.prUrl" class="text-slate-400 dark:text-slate-500">
                              No PR information available.
                            </p>
                          </dl>
                        </div>

                        <!-- AI findings -->
                        <div class="rounded-xl border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-800">
                          <p class="mb-3 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                            AI Findings
                          </p>
                          <div class="space-y-3 text-sm">
                            <div v-if="issue.aiPrReviewNote">
                              <p class="mb-1 text-xs font-medium text-slate-500 dark:text-slate-400">
                                PR Review
                              </p>
                              <p class="text-slate-700 dark:text-slate-300">
                                {{ issue.aiPrReviewNote }}
                              </p>
                            </div>
                            <div v-if="issue.aiDiffMatchNote">
                              <p class="mb-1 text-xs font-medium text-slate-500 dark:text-slate-400">
                                Diff Match
                              </p>
                              <p class="text-slate-700 dark:text-slate-300">
                                {{ issue.aiDiffMatchNote }}
                              </p>
                            </div>
                            <p v-if="!issue.aiPrReviewNote && !issue.aiDiffMatchNote" class="text-slate-400 dark:text-slate-500">
                              AI notes are not available yet.
                            </p>
                          </div>
                        </div>
                      </div>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </div>
        </section>
      </template>
    </div>
  </main>
</template>
