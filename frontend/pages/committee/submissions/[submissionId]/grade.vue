<script setup lang="ts">
import {
  ArrowLeft,
  AlertCircle,
  Loader2,
  FileText,
  CheckCircle2,
  PanelLeft,
  PanelRight,
  Calendar,
  Users,
} from "lucide-vue-next";
import type { SubmissionResponse, RubricMappingEntry, SubmissionComment } from "~/types/submission";
import type { RubricCriterionResponse, RubricGradeSubmitResponse } from "~/types/rubric";

definePageMeta({
  middleware: "auth",
  roles: ["Professor"],
});

const route = useRoute();
const {
  getAuthToken,
  fetchSubmission,
  fetchRubric,
  fetchRubricMappings,
  fetchSubmissionComments,
  fetchSubmissionGrades,
  submitRubricGrade,
} = useApiClient();

const submissionId = computed(() => route.params.submissionId as string);

// ─── Load state ───────────────────────────────────────────────────────────────
const submission = ref<SubmissionResponse | null>(null);
const criteria = ref<RubricCriterionResponse[]>([]);
const rubricMappings = ref<RubricMappingEntry[]>([]);
const loading = ref(true);
const loadError = ref<string | null>(null);

// ─── Grade form state ─────────────────────────────────────────────────────────
const selections = ref<Record<string, string>>({});
const submitting = ref(false);
const submitError = ref<string | null>(null);
const submitResult = ref<RubricGradeSubmitResponse | null>(null);

// ─── Panel visibility ─────────────────────────────────────────────────────────
const leftPanelVisible = ref(true);
const rightPanelVisible = ref(true);

// ─── Hash navigation ──────────────────────────────────────────────────────────
const selectedCriterionName = ref<string | null>(null);
const highlightRef = ref<string | null>(null);

// ─── Comments (read-only) ─────────────────────────────────────────────────────
const comments = ref<SubmissionComment[]>([]);
const commentsLoading = ref(false);

// ─── Derived ──────────────────────────────────────────────────────────────────
const binaryOptions = ["S", "F"] as const;
const softOptions = ["A", "B", "C", "D", "F"] as const;

const allSelected = computed(() =>
  criteria.value.length > 0 &&
  criteria.value.every((c) => c.id && selections.value[c.id] !== undefined)
);

// ─── Helpers ──────────────────────────────────────────────────────────────────
function extractMessage(err: unknown, fallback: string): string {
  if (err && typeof err === "object" && "message" in err) {
    return String((err as { message: string }).message);
  }
  return fallback;
}

function formatDate(dateStr: string): string {
  return new Intl.DateTimeFormat("tr-TR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(dateStr));
}

function getMappedSectionRef(criterion: RubricCriterionResponse): string | null {
  if (!criterion.id) return null;
  return rubricMappings.value.find((m) => m.criterionId === criterion.id)?.sectionKey ?? null;
}

function handleCriterionClick(criterion: RubricCriterionResponse) {
  if (selectedCriterionName.value === criterion.criterionName) {
    selectedCriterionName.value = null;
    highlightRef.value = null;
  } else {
    selectedCriterionName.value = criterion.criterionName;
    highlightRef.value = getMappedSectionRef(criterion) ?? criterion.criterionName;
  }
}

// ─── Load ─────────────────────────────────────────────────────────────────────
async function load() {
  loading.value = true;
  loadError.value = null;

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication is required.");

    const sub = await fetchSubmission(submissionId.value, token);
    submission.value = sub;

    if (sub.deliverableId) {
      try {
        criteria.value = await fetchRubric(sub.deliverableId, token);
      } catch {
        criteria.value = [];
      }
    }

    // Pre-populate existing grades for this reviewer
    try {
      const existing = await fetchSubmissionGrades(submissionId.value, token);
      const pre: Record<string, string> = {};
      for (const entry of existing.grades) {
        pre[entry.criterionId] = entry.selectedGrade;
      }
      selections.value = pre;
    } catch {
      // No prior grades — leave selections empty
    }

    // Fetch rubric mappings for hash navigation
    try {
      const mappingsResp = await fetchRubricMappings(submissionId.value, token);
      rubricMappings.value = mappingsResp.mappings ?? [];
    } catch {
      rubricMappings.value = [];
    }

    // Fetch comments (read-only)
    commentsLoading.value = true;
    try {
      comments.value = await fetchSubmissionComments(submissionId.value, token);
    } catch {
      comments.value = [];
    } finally {
      commentsLoading.value = false;
    }
  } catch (err: unknown) {
    loadError.value = extractMessage(err, "Failed to load submission.");
  } finally {
    loading.value = false;
  }
}

// ─── Submit ───────────────────────────────────────────────────────────────────
async function handleSubmit() {
  if (!allSelected.value) return;

  submitting.value = true;
  submitError.value = null;
  submitResult.value = null;

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication is required.");

    const grades = criteria.value
      .filter((c) => c.id)
      .map((c) => ({ criterionId: c.id!, selectedGrade: selections.value[c.id!] }));

    const result = await submitRubricGrade(submissionId.value, grades, token);
    submitResult.value = result;
  } catch (err: unknown) {
    const status = (err as { status?: number }).status;

    if (status === 403) {
      await navigateTo("/professor/committees");
      return;
    }

    submitError.value = extractMessage(err, "Failed to submit grades.");
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="flex flex-col h-screen bg-slate-50 dark:bg-slate-950 overflow-hidden">

    <!-- Top bar -->
    <header class="flex items-center gap-3 px-4 py-3 border-b border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 flex-shrink-0 shadow-sm">
      <NuxtLink
        to="/professor/committees"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to committees
      </NuxtLink>

      <div class="h-5 w-px bg-slate-200 dark:bg-slate-700" />

      <!-- Panel toggles -->
      <div class="flex items-center gap-1">
        <button
          class="p-1.5 rounded hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          :class="leftPanelVisible ? 'text-indigo-500' : 'text-slate-400'"
          :title="leftPanelVisible ? 'Hide document panel' : 'Show document panel'"
          @click="leftPanelVisible = !leftPanelVisible"
        >
          <PanelLeft class="w-4 h-4" />
        </button>
        <button
          class="p-1.5 rounded hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          :class="rightPanelVisible ? 'text-indigo-500' : 'text-slate-400'"
          :title="rightPanelVisible ? 'Hide grading panel' : 'Show grading panel'"
          @click="rightPanelVisible = !rightPanelVisible"
        >
          <PanelRight class="w-4 h-4" />
        </button>
      </div>

      <div class="h-5 w-px bg-slate-200 dark:bg-slate-700" />

      <FileText class="w-4 h-4 text-indigo-500 flex-shrink-0" />
      <h1 class="text-sm font-semibold text-slate-800 dark:text-slate-100 truncate">
        Grade Submission
        <span
          v-if="submission"
          class="ml-2 text-xs font-normal text-slate-500 dark:text-slate-400"
        >
          — {{ submissionId.slice(0, 8) }}…
        </span>
      </h1>
    </header>

    <!-- Submission meta sub-bar -->
    <div
      v-if="submission && !loading"
      class="flex items-center gap-4 px-4 py-1.5 border-b border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/40 text-xs text-slate-500 dark:text-slate-400 flex-shrink-0"
    >
      <span class="flex items-center gap-1">
        <Calendar class="w-3 h-3" />
        {{ formatDate(submission.updatedAt ?? submission.submittedAt) }}
        <span v-if="submission.updatedAt" class="text-amber-500">(revised)</span>
      </span>
      <span class="flex items-center gap-1">
        <Users class="w-3 h-3" />
        {{ submission.groupId.slice(0, 8) }}…
      </span>
    </div>

    <!-- Loading -->
    <div
      v-if="loading"
      class="flex-1 flex items-center justify-center"
    >
      <div class="flex flex-col items-center gap-3">
        <Loader2 class="w-8 h-8 text-indigo-500 animate-spin" />
        <p class="text-sm text-slate-500 dark:text-slate-400">Loading submission…</p>
      </div>
    </div>

    <!-- Load error -->
    <div
      v-else-if="loadError"
      class="flex-1 flex items-center justify-center p-8"
    >
      <div class="max-w-md w-full bg-red-50 dark:bg-red-900/20 rounded-xl border border-red-200 dark:border-red-800 p-6 flex gap-4">
        <AlertCircle class="w-5 h-5 text-red-500 flex-shrink-0 mt-0.5" />
        <div>
          <p class="font-semibold text-sm text-red-700 dark:text-red-300">Load error</p>
          <p class="text-sm text-red-600 dark:text-red-400 mt-1">{{ loadError }}</p>
          <button
            class="mt-3 text-sm text-red-600 dark:text-red-400 underline hover:no-underline"
            @click="load"
          >
            Try again
          </button>
        </div>
      </div>
    </div>

    <!-- Split layout -->
    <div
      v-else-if="submission"
      class="flex-1 flex overflow-hidden min-h-0"
    >
      <!-- Left: Markdown document (read-only) -->
      <section
        v-show="leftPanelVisible"
        class="flex-1 flex flex-col overflow-hidden border-r border-slate-200 dark:border-slate-700 transition-all duration-200"
        aria-label="Submission document"
      >
        <div class="px-4 py-2.5 bg-slate-50 dark:bg-slate-800/50 border-b border-slate-200 dark:border-slate-700 flex-shrink-0 flex items-center gap-2">
          <FileText class="w-3.5 h-3.5 text-slate-400" />
          <span class="text-xs font-medium text-slate-600 dark:text-slate-300 uppercase tracking-wide">
            Document
          </span>
          <span class="ml-auto text-xs text-slate-400 dark:text-slate-500 bg-slate-100 dark:bg-slate-800 px-2 py-0.5 rounded-full">
            Read-only
          </span>
        </div>

        <div class="flex-1 overflow-y-auto px-6 py-6">
          <div
            v-if="!submission.markdownContent"
            class="flex items-center justify-center h-full"
          >
            <p class="text-sm text-slate-400 dark:text-slate-500">No content in this submission.</p>
          </div>
          <MarkdownViewer
            v-else
            :content="submission.markdownContent"
            :highlight-ref="highlightRef"
          />
        </div>
      </section>

      <!-- Right: Grading form + Comments -->
      <section
        v-show="rightPanelVisible"
        class="flex flex-col overflow-hidden bg-white dark:bg-slate-900 transition-all duration-200"
        :class="leftPanelVisible ? 'w-96 flex-shrink-0' : 'flex-1'"
        aria-label="Rubric grading form and comments"
      >
        <!-- Top: Grading form -->
        <div class="flex-1 min-h-0 flex flex-col overflow-hidden border-b border-slate-200 dark:border-slate-700">
          <!-- Section header -->
          <div class="px-4 py-2.5 bg-slate-50 dark:bg-slate-800/50 border-b border-slate-200 dark:border-slate-700 flex-shrink-0 flex items-center gap-2">
            <span class="text-xs font-medium text-slate-600 dark:text-slate-300 uppercase tracking-wide">
              Rubric
            </span>
            <span class="ml-auto text-xs text-slate-400">
              {{ criteria.filter(c => c.id && selections[c.id]).length }}/{{ criteria.length }} graded
            </span>
          </div>

          <div class="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-3">

            <!-- No criteria configured -->
            <div
              v-if="criteria.length === 0"
              class="flex items-center justify-center h-full"
            >
              <p class="text-sm text-slate-400 dark:text-slate-500">No rubric criteria configured for this deliverable.</p>
            </div>

            <template v-else>
              <!-- Criterion rows -->
              <div
                v-for="criterion in criteria"
                :key="criterion.id"
                class="rounded-lg border bg-slate-50 dark:bg-slate-800/50 p-3 flex flex-col gap-2 cursor-pointer transition-colors"
                :class="selectedCriterionName === criterion.criterionName
                  ? 'border-indigo-400 dark:border-indigo-500 bg-indigo-50 dark:bg-indigo-900/20 border-l-4'
                  : 'border-slate-200 dark:border-slate-700 border-l-4 border-l-transparent hover:border-slate-300 dark:hover:border-slate-600'"
                @click="handleCriterionClick(criterion)"
              >
                <div class="flex items-start justify-between gap-2">
                  <p
                    class="text-sm font-medium"
                    :class="selectedCriterionName === criterion.criterionName
                      ? 'text-indigo-700 dark:text-indigo-300'
                      : 'text-slate-800 dark:text-slate-100'"
                  >
                    {{ criterion.criterionName }}
                  </p>
                  <div class="flex items-center gap-1.5 flex-shrink-0">
                    <span
                      v-if="getMappedSectionRef(criterion)"
                      class="text-[10px] text-emerald-600 dark:text-emerald-400 font-medium"
                    >
                      ✓ mapped
                    </span>
                    <span class="text-xs text-slate-400">w: {{ criterion.weight }}</span>
                    <span
                      class="text-xs px-1.5 py-0.5 rounded-full font-medium"
                      :class="criterion.gradingType === 'Binary'
                        ? 'bg-sky-100 text-sky-700 dark:bg-sky-900/40 dark:text-sky-300'
                        : 'bg-violet-100 text-violet-700 dark:bg-violet-900/40 dark:text-violet-300'"
                    >
                      {{ criterion.gradingType }}
                    </span>
                  </div>
                </div>

                <!-- Binary dropdown -->
                <select
                  v-if="criterion.gradingType === 'Binary'"
                  v-model="selections[criterion.id!]"
                  class="w-full rounded-md border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-900 text-sm text-slate-800 dark:text-slate-100 px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  @click.stop
                >
                  <option value="" disabled selected>Select grade…</option>
                  <option v-for="opt in binaryOptions" :key="opt" :value="opt">
                    {{ opt === "S" ? "S — Satisfactory" : "F — Fail" }}
                  </option>
                </select>

                <!-- Soft dropdown -->
                <select
                  v-else
                  v-model="selections[criterion.id!]"
                  class="w-full rounded-md border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-900 text-sm text-slate-800 dark:text-slate-100 px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  @click.stop
                >
                  <option value="" disabled selected>Select grade…</option>
                  <option v-for="opt in softOptions" :key="opt" :value="opt">{{ opt }}</option>
                </select>
              </div>

              <!-- Success banner -->
              <div
                v-if="submitResult"
                class="rounded-lg border border-emerald-200 dark:border-emerald-700 bg-emerald-50 dark:bg-emerald-900/20 p-4 flex items-start gap-3"
              >
                <CheckCircle2 class="w-5 h-5 text-emerald-500 flex-shrink-0 mt-0.5" />
                <div>
                  <p class="text-sm font-semibold text-emerald-700 dark:text-emerald-300">Grades submitted</p>
                  <p class="text-sm text-emerald-600 dark:text-emerald-400 mt-0.5">
                    Base Deliverable Grade:
                    <span class="font-bold">{{ submitResult.baseDeliverableGrade.toFixed(2) }}</span>
                  </p>
                </div>
              </div>

              <!-- Submit error -->
              <div
                v-if="submitError"
                class="rounded-lg border border-red-200 dark:border-red-700 bg-red-50 dark:bg-red-900/20 p-3 flex items-start gap-2"
              >
                <AlertCircle class="w-4 h-4 text-red-500 flex-shrink-0 mt-0.5" />
                <p class="text-sm text-red-600 dark:text-red-400">{{ submitError }}</p>
              </div>

              <!-- Submit button -->
              <button
                class="mt-auto w-full py-2.5 px-4 rounded-lg text-sm font-semibold transition-colors flex items-center justify-center gap-2"
                :class="allSelected && !submitting
                  ? 'bg-indigo-600 hover:bg-indigo-700 text-white'
                  : 'bg-slate-200 dark:bg-slate-700 text-slate-400 dark:text-slate-500 cursor-not-allowed'"
                :disabled="!allSelected || submitting"
                @click="handleSubmit"
              >
                <Loader2 v-if="submitting" class="w-4 h-4 animate-spin" />
                {{ submitting ? "Submitting…" : "Submit Grades" }}
              </button>
            </template>
          </div>
        </div>

        <!-- Bottom: Comments (read-only) -->
        <div class="h-64 flex-shrink-0 overflow-hidden">
          <CommentsPanel
            :comments="comments"
            :loading="commentsLoading"
            :posting="false"
            :error="null"
            :read-only="true"
          />
        </div>
      </section>
    </div>

  </div>
</template>
