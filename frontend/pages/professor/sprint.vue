<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import {
  AlertCircle,
  ArrowLeft,
  CalendarDays,
  ChevronDown,
  ChevronUp,
  Eye,
  EyeOff,
  Loader2,
  Save,
  Trophy,
} from "lucide-vue-next";
import type {
  ActiveSprintResponse,
  AdvisorGroupSprintSummaryResponse,
  ScrumGradeValue,
  SprintTrackingResponse,
} from "~/types/advisor";

definePageMeta({
  middleware: "auth",
  roles: ["Professor"],
});

const gradeOptions: ScrumGradeValue[] = ["A", "B", "C", "D", "F"];

const {
  getAuthToken,
  fetchAdvisorActiveSprint,
  fetchAdvisorSprintGroups,
  fetchAdvisorGroupTracking,
  submitAdvisorGroupGrade,
} = useApiClient();

const isPageLoading = ref(true);
const pageError = ref<string | null>(null);
const noActiveSprint = ref(false);
const activeSprint = ref<ActiveSprintResponse | null>(null);
const groups = ref<AdvisorGroupSprintSummaryResponse[]>([]);
const showDisbanded = ref(false);

const expandedGroupIds = ref<Set<string>>(new Set());
const trackingByGroup = reactive<Record<string, SprintTrackingResponse | null>>({});
const trackingLoadingByGroup = reactive<Record<string, boolean>>({});
const trackingErrorByGroup = reactive<Record<string, string | null>>({});

const gradeForms = reactive<Record<string, { pointA_grade: ScrumGradeValue; pointB_grade: ScrumGradeValue }>>({});
const gradeSavingByGroup = reactive<Record<string, boolean>>({});
const gradeErrorByGroup = reactive<Record<string, string | null>>({});
const gradeSuccessByGroup = reactive<Record<string, string | null>>({});

const hasGroups = computed(() => groups.value.length > 0);

function formatDate(dateString: string): string {
  return new Date(dateString).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function ensureGroupForm(group: AdvisorGroupSprintSummaryResponse) {
  if (!gradeForms[group.groupId]) {
    gradeForms[group.groupId] = {
      pointA_grade: group.pointA_grade ?? "C",
      pointB_grade: group.pointB_grade ?? "C",
    };
  }
}

async function loadPageData() {
  isPageLoading.value = true;
  pageError.value = null;
  noActiveSprint.value = false;

  try {
    const token = getAuthToken();
    if (!token) {
      throw new Error("Authentication required");
    }

    const sprint = await fetchAdvisorActiveSprint(token);
    activeSprint.value = sprint;

    const sprintGroups = await fetchAdvisorSprintGroups(sprint.sprintId, token, showDisbanded.value);
    groups.value = sprintGroups;

    for (const group of sprintGroups) {
      ensureGroupForm(group);
    }
  } catch (err: unknown) {
    const status = err && typeof err === "object" && "status" in err ? Number(err.status) : undefined;
    if (status === 404) {
      noActiveSprint.value = true;
      groups.value = [];
      activeSprint.value = null;
      return;
    }

    const message =
      err && typeof err === "object" && "message" in err
        ? String(err.message)
        : "Failed to load sprint dashboard.";
    pageError.value = message;
  } finally {
    isPageLoading.value = false;
  }
}

async function toggleGroup(groupId: string) {
  const next = new Set(expandedGroupIds.value);
  if (next.has(groupId)) {
    next.delete(groupId);
    expandedGroupIds.value = next;
    return;
  }

  next.add(groupId);
  expandedGroupIds.value = next;

  if (!trackingByGroup[groupId] && !trackingLoadingByGroup[groupId]) {
    await loadGroupTracking(groupId);
  }
}

async function loadGroupTracking(groupId: string) {
  if (!activeSprint.value) return;

  trackingLoadingByGroup[groupId] = true;
  trackingErrorByGroup[groupId] = null;

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required");

    const tracking = await fetchAdvisorGroupTracking(activeSprint.value.sprintId, groupId, token);
    trackingByGroup[groupId] = tracking;
  } catch (err: unknown) {
    const status = err && typeof err === "object" && "status" in err ? Number(err.status) : undefined;
    if (status === 404) {
      // Issue requires empty-state when tracking is not available yet.
      trackingByGroup[groupId] = {
        groupId,
        sprintId: activeSprint.value.sprintId,
        fetchedAt: null,
        issues: [],
        perStudentSummary: [],
      };
      return;
    }

    trackingErrorByGroup[groupId] =
      err && typeof err === "object" && "message" in err
        ? String(err.message)
        : "Failed to load tracking details.";
  } finally {
    trackingLoadingByGroup[groupId] = false;
  }
}

async function submitGrade(group: AdvisorGroupSprintSummaryResponse) {
  if (!activeSprint.value) return;

  const groupId = group.groupId;
  gradeSavingByGroup[groupId] = true;
  gradeErrorByGroup[groupId] = null;
  gradeSuccessByGroup[groupId] = null;

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required");

    const form = gradeForms[groupId];
    const response = await submitAdvisorGroupGrade(
      activeSprint.value.sprintId,
      groupId,
      {
        pointA_grade: form.pointA_grade,
        pointB_grade: form.pointB_grade,
      },
      token
    );

    group.pointA_grade = response.pointA_grade;
    group.pointB_grade = response.pointB_grade;
    group.gradeSubmitted = true;
    gradeSuccessByGroup[groupId] = "Grade saved.";
  } catch (err: unknown) {
    gradeErrorByGroup[groupId] =
      err && typeof err === "object" && "message" in err
        ? String(err.message)
        : "Failed to submit grade.";
  } finally {
    gradeSavingByGroup[groupId] = false;
  }
}

onMounted(loadPageData);
</script>

<template>
  <main
    class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8"
  >
    <div class="mx-auto w-full max-w-6xl space-y-6">
      <NuxtLink
        to="/professor/dashboard"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to Dashboard
      </NuxtLink>

      <header
        class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg"
      >
        <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
              Sprint Tracking & Grading
            </h1>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Review per-group tracking, AI validation results, and submit Point A / Point B grades.
            </p>
          </div>
          <button
            type="button"
            class="inline-flex shrink-0 items-center gap-2 rounded-lg border px-3 py-2 text-sm font-medium transition"
            :class="showDisbanded
              ? 'border-amber-400 bg-amber-50 text-amber-700 hover:bg-amber-100 dark:border-amber-600 dark:bg-amber-900/30 dark:text-amber-300 dark:hover:bg-amber-900/50'
              : 'border-slate-300 bg-white text-slate-600 hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600'"
            @click="showDisbanded = !showDisbanded; loadPageData()"
          >
            <Eye v-if="!showDisbanded" class="h-4 w-4" />
            <EyeOff v-else class="h-4 w-4" />
            {{ showDisbanded ? "Hide disbanded" : "Show disbanded" }}
          </button>
        </div>

        <div
          v-if="activeSprint"
          class="mt-4 grid gap-3 rounded-xl border border-slate-200 bg-slate-50/80 p-4 text-sm dark:border-slate-600 dark:bg-slate-900/50 md:grid-cols-3"
        >
          <div class="flex items-center gap-2 text-slate-700 dark:text-slate-300">
            <CalendarDays class="h-4 w-4 text-blue-600 dark:text-blue-400" />
            {{ formatDate(activeSprint.startDate) }} - {{ formatDate(activeSprint.endDate) }}
          </div>
          <div class="text-slate-700 dark:text-slate-300">
            Target SP:
            <span class="font-semibold text-slate-900 dark:text-white">
              {{ activeSprint.storyPointTarget ?? "-" }}
            </span>
          </div>
          <div class="flex items-center gap-2 text-slate-700 dark:text-slate-300">
            <Trophy class="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
            Days Remaining: <span class="font-semibold text-slate-900 dark:text-white">{{ activeSprint.daysRemaining }}</span>
          </div>
        </div>
      </header>

      <div v-if="isPageLoading" class="flex items-center justify-center rounded-2xl border border-slate-200 bg-white p-12 dark:border-slate-700 dark:bg-slate-800">
        <Loader2 class="h-6 w-6 animate-spin text-blue-600 dark:text-blue-400" />
        <span class="ml-3 text-sm text-slate-600 dark:text-slate-400">Loading sprint dashboard...</span>
      </div>

      <div
        v-else-if="pageError"
        class="flex items-start gap-3 rounded-2xl border border-red-300 bg-red-50 p-6 text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300"
      >
        <AlertCircle class="mt-0.5 h-5 w-5 shrink-0" />
        <div>
          <p class="font-medium">Failed to load sprint data</p>
          <p class="mt-1 text-sm">{{ pageError }}</p>
        </div>
      </div>

      <div
        v-else-if="noActiveSprint"
        class="rounded-2xl border border-dashed border-slate-300 bg-white py-16 text-center dark:border-slate-600 dark:bg-slate-800"
      >
        <p class="text-sm text-slate-600 dark:text-slate-400">No active sprint found.</p>
      </div>

      <div
        v-else-if="!hasGroups"
        class="rounded-2xl border border-dashed border-slate-300 bg-white py-16 text-center dark:border-slate-600 dark:bg-slate-800"
      >
        <p class="text-sm text-slate-600 dark:text-slate-400">No groups are assigned to you for this sprint.</p>
      </div>

      <section
        v-else
        v-for="group in groups"
        :key="group.groupId"
        class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800"
      >
        <button
          type="button"
          @click="toggleGroup(group.groupId)"
          class="flex w-full items-center justify-between gap-4 p-6 text-left transition hover:bg-slate-50 dark:hover:bg-slate-700"
        >
          <div>
            <h2 class="text-lg font-semibold text-slate-900 dark:text-white">{{ group.groupName }}</h2>
            <div class="mt-2 flex flex-wrap items-center gap-2 text-xs">
              <span class="rounded-full bg-slate-100 px-2 py-1 text-slate-700 dark:bg-slate-700 dark:text-slate-200">
                {{ group.totalIssues }} Issues
              </span>
              <span class="rounded-full bg-blue-100 px-2 py-1 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300">
                {{ group.mergedPRs }} Merged PRs
              </span>
              <span class="rounded-full bg-emerald-100 px-2 py-1 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300">
                Grade: {{ group.gradeSubmitted ? `${group.pointA_grade}/${group.pointB_grade}` : "Not Submitted" }}
              </span>
            </div>
          </div>
          <ChevronUp v-if="expandedGroupIds.has(group.groupId)" class="h-5 w-5 text-slate-400" />
          <ChevronDown v-else class="h-5 w-5 text-slate-400" />
        </button>

        <div v-if="expandedGroupIds.has(group.groupId)" class="border-t border-slate-100 px-6 pb-6 pt-5 dark:border-slate-700">
          <section class="rounded-xl border border-slate-200 p-4 dark:border-slate-600">
            <h3 class="text-sm font-semibold text-slate-900 dark:text-white">Submit Scrum Grade</h3>
            <div class="mt-3 grid gap-3 md:grid-cols-3">
              <label class="text-sm text-slate-600 dark:text-slate-300">
                Point A
                <select
                  v-model="gradeForms[group.groupId].pointA_grade"
                  class="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:ring-blue-900"
                >
                  <option v-for="grade in gradeOptions" :key="`a-${group.groupId}-${grade}`" :value="grade">{{ grade }}</option>
                </select>
              </label>

              <label class="text-sm text-slate-600 dark:text-slate-300">
                Point B
                <select
                  v-model="gradeForms[group.groupId].pointB_grade"
                  class="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:ring-blue-900"
                >
                  <option v-for="grade in gradeOptions" :key="`b-${group.groupId}-${grade}`" :value="grade">{{ grade }}</option>
                </select>
              </label>

              <div class="flex items-end">
                <button
                  type="button"
                  :disabled="gradeSavingByGroup[group.groupId]"
                  @click="submitGrade(group)"
                  class="inline-flex w-full items-center justify-center rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <Loader2 v-if="gradeSavingByGroup[group.groupId]" class="mr-2 h-4 w-4 animate-spin" />
                  <Save v-else class="mr-2 h-4 w-4" />
                  Save Grade
                </button>
              </div>
            </div>

            <p v-if="gradeErrorByGroup[group.groupId]" class="mt-2 text-xs text-red-600 dark:text-red-400">
              {{ gradeErrorByGroup[group.groupId] }}
            </p>
            <p v-else-if="gradeSuccessByGroup[group.groupId]" class="mt-2 text-xs text-emerald-600 dark:text-emerald-400">
              {{ gradeSuccessByGroup[group.groupId] }}
            </p>
          </section>

          <section class="mt-4">
            <h3 class="text-sm font-semibold text-slate-900 dark:text-white">Per-Student Summary</h3>
            <div
              v-if="(trackingByGroup[group.groupId]?.perStudentSummary ?? group.perStudentSummary).length === 0"
              class="mt-2 rounded-lg border border-dashed border-slate-300 p-4 text-sm text-slate-500 dark:border-slate-600 dark:text-slate-400"
            >
              No per-student summary yet.
            </div>
            <div v-else class="mt-2 overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-600">
              <table class="w-full text-sm">
                <thead>
                  <tr class="border-b border-slate-200 bg-slate-50 dark:border-slate-600 dark:bg-slate-700/50">
                    <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Assignee</th>
                    <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Completed Points</th>
                    <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">AI Validation</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-100 dark:divide-slate-700">
                  <tr
                    v-for="student in trackingByGroup[group.groupId]?.perStudentSummary ?? group.perStudentSummary"
                    :key="`${group.groupId}-${student.assigneeGithubUsername}`"
                    class="hover:bg-slate-50 dark:hover:bg-slate-700"
                  >
                    <td class="px-3 py-2 text-slate-900 dark:text-white">{{ student.assigneeGithubUsername }}</td>
                    <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ student.completedPoints }}</td>
                    <td class="px-3 py-2">
                      <AiResultBadge :result="student.aiValidationStatus" />
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section class="mt-4">
            <h3 class="text-sm font-semibold text-slate-900 dark:text-white">Issue Tracking Detail</h3>
            <div v-if="trackingLoadingByGroup[group.groupId]" class="mt-2 flex items-center text-sm text-slate-600 dark:text-slate-400">
              <Loader2 class="mr-2 h-4 w-4 animate-spin" />
              Loading tracking details...
            </div>
            <div
              v-else-if="trackingErrorByGroup[group.groupId]"
              class="mt-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-900/20 dark:text-red-300"
            >
              {{ trackingErrorByGroup[group.groupId] }}
            </div>
            <div
              v-else-if="(trackingByGroup[group.groupId]?.issues ?? []).length === 0"
              class="mt-2 rounded-lg border border-dashed border-slate-300 p-4 text-sm text-slate-500 dark:border-slate-600 dark:text-slate-400"
            >
              Tracking data not yet available.
            </div>
            <div v-else class="mt-2 overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-600">
              <table class="w-full text-sm">
                <thead>
                  <tr class="border-b border-slate-200 bg-slate-50 dark:border-slate-600 dark:bg-slate-700/50">
                    <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Issue</th>
                    <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Assignee</th>
                    <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Story Points</th>
                    <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">PR Merged</th>
                    <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">AI PR Review</th>
                    <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">AI Diff Match</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-100 dark:divide-slate-700">
                  <tr
                    v-for="issue in trackingByGroup[group.groupId]?.issues ?? []"
                    :key="`${group.groupId}-${issue.issueKey}`"
                    class="hover:bg-slate-50 dark:hover:bg-slate-700"
                  >
                    <td class="px-3 py-2 font-medium text-slate-900 dark:text-white">{{ issue.issueKey }}</td>
                    <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ issue.assigneeGithubUsername ?? "-" }}</td>
                    <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ issue.storyPoints ?? "-" }}</td>
                    <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ issue.prMerged ? "Yes" : "No" }}</td>
                    <td class="px-3 py-2">
                      <AiResultBadge :result="issue.aiPrResult" />
                    </td>
                    <td class="px-3 py-2">
                      <AiResultBadge :result="issue.aiDiffResult" />
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </div>
      </section>
    </div>
  </main>
</template>
