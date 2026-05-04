<script setup lang="ts">
import {
  ArrowLeft,
  AlertCircle,
  CheckCircle2,
  Loader2,
  Send,
  FileText,
  PanelRight,
} from "lucide-vue-next";
import type { RubricCriterionResponse } from "~/types/rubric";
import type { LocalMappingEntry } from "~/types/submission";

definePageMeta({
  middleware: "auth",
  roles: ["Student"],
});

const route = useRoute();
const router = useRouter();
const {
  getAuthToken,
  fetchStudentDeliverables,
  submitDeliverable,
  fetchStudentRubric,
  saveRubricMappings,
} = useApiClient();

const deliverableId = computed(() => route.params.deliverableId as string);

// State
const deliverableName = ref<string>("");
const markdownContent = ref<string>("");
const rubricCriteria = ref<RubricCriterionResponse[]>([]);
const localMappings = ref<LocalMappingEntry[]>([]);

const loadingRubric = ref(true);
const loadError = ref<string | null>(null);
const submitting = ref(false);
const submitError = ref<string | null>(null);
const submitSuccess = ref(false);
const submittedId = ref<string | null>(null);

const rubricPanelVisible = ref(true);

// Selection state (synced from MarkdownEditor via expose)
const editorRef = ref<{ selectionText: string; selectionStart: number; selectionEnd: number; hasSelection: boolean } | null>(null);

const hasSelection = computed(() => editorRef.value?.hasSelection ?? false);
const selectionText = computed(() => editorRef.value?.selectionText ?? "");

// Whether criteria have IDs (backend may not return them)
const noCriteriaIds = computed(() =>
  rubricCriteria.value.length > 0 && rubricCriteria.value.every((c) => !c.id)
);

// ── Load ──────────────────────────────────────────────────────────────────────

async function load() {
  loadingRubric.value = true;
  loadError.value = null;
  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required.");

    // Get deliverable name from student deliverables list
    try {
      const deliverables = await fetchStudentDeliverables(token);
      const match = deliverables.find((d) => d.id === deliverableId.value);
      if (match) deliverableName.value = match.name;
    } catch {
      // Not critical
    }

    // Get rubric criteria (403 if coordinator-only; handle gracefully)
    try {
      rubricCriteria.value = await fetchStudentRubric(deliverableId.value, token);
    } catch {
      rubricCriteria.value = [];
    }
  } catch (err: unknown) {
    loadError.value = parseError(err, "Failed to load data.");
  } finally {
    loadingRubric.value = false;
  }
}

// ── Mapping ───────────────────────────────────────────────────────────────────

function handleLink(criterion: RubricCriterionResponse) {
  const text = selectionText.value;
  if (!text.trim()) return;

  // Prevent duplicate: same criterion + same text
  const alreadyExists = localMappings.value.some(
    (m) => m.criterionName === criterion.criterionName && m.sectionReference === text
  );
  if (alreadyExists) return;

  localMappings.value.push({
    localId: crypto.randomUUID(),
    criterionId: criterion.id ?? criterion.criterionName,
    criterionName: criterion.criterionName,
    sectionReference: text,
  });
}

function handleRemoveMapping(localId: string) {
  localMappings.value = localMappings.value.filter((m) => m.localId !== localId);
}

// ── Submit ────────────────────────────────────────────────────────────────────

async function handleSubmit() {
  if (!markdownContent.value.trim()) {
    submitError.value = "Submission content cannot be empty.";
    return;
  }

  submitting.value = true;
  submitError.value = null;

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required.");

    // 1. POST submission content
    const result = await submitDeliverable(deliverableId.value, markdownContent.value, token);
    submittedId.value = result.submissionId;

    // 2. POST rubric mappings if any
    if (localMappings.value.length > 0) {
      try {
        await saveRubricMappings(
          result.submissionId,
          {
            mappings: localMappings.value.map((m) => ({
              criterionId: m.criterionId,
              sectionReference: m.sectionReference,
            })),
          },
          token
        );
      } catch {
        // Mappings failed but submission succeeded — show warning, not error
        submitError.value = "Submission saved but section mappings could not be saved.";
      }
    }

    submitSuccess.value = true;
  } catch (err: unknown) {
    submitError.value = parseError(err, "An error occurred while submitting.");
  } finally {
    submitting.value = false;
  }
}

// ── Utils ─────────────────────────────────────────────────────────────────────

function parseError(err: unknown, fallback: string): string {
  return err && typeof err === "object" && "message" in err
    ? String((err as { message: string }).message)
    : fallback;
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
      <FileText class="w-4 h-4 text-indigo-500 flex-shrink-0" />
      <h1 class="text-sm font-semibold text-slate-800 dark:text-slate-100 truncate">
        {{ deliverableName || "Write Submission" }}
      </h1>

      <div class="ml-auto flex items-center gap-2">
        <!-- Panel toggle -->
        <button
          class="p-1.5 rounded hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          :class="rubricPanelVisible ? 'text-indigo-500' : 'text-slate-400'"
          title="Toggle rubric panel"
          @click="rubricPanelVisible = !rubricPanelVisible"
        >
          <PanelRight class="w-4 h-4" />
        </button>

        <!-- Submit button -->
        <button
          :disabled="submitting || submitSuccess || !markdownContent.trim()"
          class="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all"
          :class="submitSuccess
            ? 'bg-emerald-600 text-white cursor-default'
            : 'bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed shadow-sm'"
          @click="handleSubmit"
        >
          <Loader2 v-if="submitting" class="w-4 h-4 animate-spin" />
          <CheckCircle2 v-else-if="submitSuccess" class="w-4 h-4" />
          <Send v-else class="w-4 h-4" />
          {{ submitSuccess ? "Submitted" : submitting ? "Submitting…" : "Submit" }}
        </button>
      </div>
    </header>

    <!-- Error / success banner -->
    <div
      v-if="submitError"
      class="flex items-start gap-3 px-4 py-3 bg-red-50 dark:bg-red-900/20 border-b border-red-200 dark:border-red-800 text-sm text-red-700 dark:text-red-300 flex-shrink-0"
    >
      <AlertCircle class="w-4 h-4 flex-shrink-0 mt-0.5" />
      {{ submitError }}
    </div>

    <div
      v-if="submitSuccess"
      class="flex items-start gap-3 px-4 py-3 bg-emerald-50 dark:bg-emerald-900/20 border-b border-emerald-200 dark:border-emerald-800 text-sm text-emerald-700 dark:text-emerald-300 flex-shrink-0"
    >
      <CheckCircle2 class="w-4 h-4 flex-shrink-0 mt-0.5" />
      Submission saved successfully!
      <span v-if="localMappings.length > 0"> With {{ localMappings.length }} section mapping(s).</span>
    </div>

    <!-- Main load error -->
    <div
      v-if="loadError"
      class="flex-1 flex items-center justify-center p-8"
    >
      <div class="max-w-sm w-full bg-red-50 dark:bg-red-900/20 rounded-xl border border-red-200 dark:border-red-800 p-6 flex gap-4">
        <AlertCircle class="w-5 h-5 text-red-500 flex-shrink-0 mt-0.5" />
        <div>
          <p class="font-semibold text-sm text-red-700 dark:text-red-300">Loading error</p>
          <p class="text-sm text-red-600 dark:text-red-400 mt-1">{{ loadError }}</p>
          <button class="mt-3 text-sm underline text-red-600 dark:text-red-400" @click="load">
            Try again
          </button>
        </div>
      </div>
    </div>

    <!-- Split layout -->
    <div v-else class="flex-1 flex overflow-hidden min-h-0">

      <!-- Left: Markdown editor -->
      <section
        class="flex flex-col overflow-hidden border-r border-slate-200 dark:border-slate-700 flex-1 bg-white dark:bg-slate-900"
        aria-label="Markdown editor"
      >
        <!-- Section header -->
        <div class="px-4 py-2.5 bg-slate-50 dark:bg-slate-800/50 border-b border-slate-200 dark:border-slate-700 flex-shrink-0 flex items-center gap-2">
          <FileText class="w-3.5 h-3.5 text-slate-400" />
          <span class="text-xs font-medium text-slate-600 dark:text-slate-300 uppercase tracking-wide">
            Markdown Editor
          </span>
          <span class="ml-auto text-xs text-slate-400">
            {{ markdownContent.length }} characters
          </span>
        </div>

        <MarkdownEditor
          ref="editorRef"
          v-model="markdownContent"
          :mappings="localMappings"
          :disabled="submitSuccess"
          class="flex-1 min-h-0"
        />
      </section>

      <!-- Right: Rubric linking panel -->
      <section
        v-show="rubricPanelVisible"
        class="w-80 lg:w-96 flex-shrink-0 flex flex-col overflow-hidden bg-white dark:bg-slate-900"
        aria-label="Rubric section mapping"
      >
        <RubricLinkingPanel
          :criteria="rubricCriteria"
          :mappings="localMappings"
          :has-selection="hasSelection"
          :selection-text="selectionText"
          :loading="loadingRubric"
          :no-criteria-ids="noCriteriaIds"
          @link="handleLink"
          @remove-mapping="handleRemoveMapping"
        />
      </section>
    </div>
  </div>
</template>
