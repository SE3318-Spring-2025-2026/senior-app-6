<script setup lang="ts">
import {
  ArrowLeft,
  AlertCircle,
  Loader2,
  FileText,
  CheckCircle2,
  PanelLeft,
  PanelRight,
} from "lucide-vue-next";
import type { SubmissionResponse } from "~/types/submission";
import type { RubricCriterionResponse, RubricGradeSubmitResponse } from "~/types/rubric";

definePageMeta({
  middleware: "auth",
  roles: ["Professor"],
});

const route = useRoute();
const router = useRouter();
const {
  getAuthToken,
  fetchSubmission,
  fetchRubric,
  submitRubricGrade,
} = useApiClient();

const submissionId = computed(() => route.params.submissionId as string);

// ─── Load state ───────────────────────────────────────────────────────────────
const submission = ref<SubmissionResponse | null>(null);
const criteria = ref<RubricCriterionResponse[]>([]);
const loading = ref(true);
const loadError = ref<string | null>(null);

// ─── Grade form state ─────────────────────────────────────────────────────────
// Maps criterionId → selectedGrade string
const selections = ref<Record<string, string>>({});
const submitting = ref(false);
const submitError = ref<string | null>(null);
const submitResult = ref<RubricGradeSubmitResponse | null>(null);

// ─── Panel visibility ─────────────────────────────────────────────────────────
const leftPanelVisible = ref(true);
const rightPanelVisible = ref(true);

// ─── Derived ──────────────────────────────────────────────────────────────────
const binaryOptions = ["S", "F"] as const;
const softOptions = ["A", "B", "C", "D", "F"] as const;

const allSelected = computed(() =>
  criteria.value.length > 0 &&
  criteria.value.every((c) => c.id && selections.value[c.id] !== undefined)
);

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

    // 403 → not a committee member for this submission; redirect away
    if (status === 403) {
      router.back();
      return;
    }

    submitError.value = extractMessage(err, "Failed to submit grades.");
  } finally {
    submitting.value = false;
  }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
function extractMessage(err: unknown, fallback: string): string {
  if (err && typeof err === "object" && "message" in err) {
    return String((err as { message: string }).message);
  }
  return fallback;
}

onMounted(load);
</script>

<template>
  <div class="flex flex-col h-screen bg-slate-50 dark:bg-slate-950 overflow-hidden">

    <!-- Top bar -->
    <header class="flex items-center gap-3 px-4 py-3 border-b border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 flex-shrink-0 shadow-sm">
      <button
        class="inline-flex items-center gap-1.5 text-sm text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white transition-colors"
        @click="router.back()"
      >
        <ArrowLeft class="w-4 h-4" />
        Back
      </button>

      <div class="h-5 w-px bg-slate-200 dark:bg-slate-700" />

      <!-- Panel toggles -->
      <div class="flex items-center gap-1">
        <button
          class="p-1.5 rounded hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          :class="leftPanelVisible ? 'text-indigo-500' : 'text-slate-400'"
          title="Toggle document panel"
          @click="leftPanelVisible = !leftPanelVisible"
        >
          <PanelLeft class="w-4 h-4" />
        </button>
        <button
          class="p-1.5 rounded hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          :class="rightPanelVisible ? 'text-indigo-500' : 'text-slate-400'"
          title="Toggle grading panel"
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
        class="flex flex-col overflow-hidden border-r border-slate-200 dark:border-slate-700 transition-all duration-200"
        class="flex-1"
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
          />
        </div>
      </section>

      <!-- Right: Rubric grading form -->
      <section
        v-show="rightPanelVisible"
        class="flex flex-col overflow-hidden bg-white dark:bg-slate-900 transition-all duration-200"
        :class="leftPanelVisible ? 'w-96 flex-shrink-0' : 'flex-1'"
        aria-label="Rubric grading form"
      >
        <!-- Section header -->
        <div class="px-4 py-2.5 bg-slate-50 dark:bg-slate-800/50 border-b border-slate-200 dark:border-slate-700 flex-shrink-0 flex items-center gap-2">
          <span class="text-xs font-medium text-slate-600 dark:text-slate-300 uppercase tracking-wide">
            Rubric
          </span>
          <span class="ml-auto text-xs text-slate-400">
            {{ criteria.filter(c => c.id && selections[c.id]).length }}/{{ criteria.length }} graded
          </span>
        </div>

        <div class="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-4">

          <!-- No criteria configured -->
          <div
            v-if="criteria.length === 0"
            class="flex items-center justify-center h-full"
          >
            <p class="text-sm text-slate-400 dark:text-slate-500">No rubric criteria configured for this deliverable.</p>
          </div>

          <!-- Criterion rows -->
          <template v-else>
            <div
              v-for="criterion in criteria"
              :key="criterion.id"
              class="rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50 p-3 flex flex-col gap-2"
            >
              <div class="flex items-start justify-between gap-2">
                <p class="text-sm font-medium text-slate-800 dark:text-slate-100">
                  {{ criterion.criterionName }}
                </p>
                <div class="flex items-center gap-1.5 flex-shrink-0">
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

              <!-- Binary dropdown: S / F only -->
              <select
                v-if="criterion.gradingType === 'Binary'"
                v-model="selections[criterion.id!]"
                class="w-full rounded-md border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-900 text-sm text-slate-800 dark:text-slate-100 px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              >
                <option value="" disabled selected>Select grade…</option>
                <option
                  v-for="opt in binaryOptions"
                  :key="opt"
                  :value="opt"
                >
                  {{ opt === "S" ? "S — Satisfactory" : "F — Fail" }}
                </option>
              </select>

              <!-- Soft dropdown: A B C D F -->
              <select
                v-else
                v-model="selections[criterion.id!]"
                class="w-full rounded-md border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-900 text-sm text-slate-800 dark:text-slate-100 px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              >
                <option value="" disabled selected>Select grade…</option>
                <option
                  v-for="opt in softOptions"
                  :key="opt"
                  :value="opt"
                >
                  {{ opt }}
                </option>
              </select>
            </div>

            <!-- Success banner -->
            <div
              v-if="submitResult"
              class="rounded-lg border border-emerald-200 dark:border-emerald-700 bg-emerald-50 dark:bg-emerald-900/20 p-4 flex items-start gap-3"
            >
              <CheckCircle2 class="w-5 h-5 text-emerald-500 flex-shrink-0 mt-0.5" />
              <div>
                <p class="text-sm font-semibold text-emerald-700 dark:text-emerald-300">
                  Grades submitted
                </p>
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
              <Loader2
                v-if="submitting"
                class="w-4 h-4 animate-spin"
              />
              {{ submitting ? "Submitting…" : "Submit Grades" }}
            </button>
          </template>
        </div>
      </section>
    </div>

  </div>
</template>
