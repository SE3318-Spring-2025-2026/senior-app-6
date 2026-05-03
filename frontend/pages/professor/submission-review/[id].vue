<script setup lang="ts">
import {
  ArrowLeft,
  AlertCircle,
  Loader2,
  FileText,
  Calendar,
  Users,
  PanelLeft,
  PanelRight,
} from "lucide-vue-next";
import type { SubmissionResponse, RubricMappingItem } from "~/types/submission";
import type { RubricCriterionResponse } from "~/types/rubric";

definePageMeta({
  middleware: "auth",
  roles: ["Professor"],
});

const route = useRoute();
const router = useRouter();
const { getAuthToken, fetchSubmission, fetchRubricMappings, fetchRubric } =
  useApiClient();

const submissionId = computed(() => route.params.id as string);

// Data
const submission = ref<SubmissionResponse | null>(null);
const rubricCriteria = ref<RubricCriterionResponse[]>([]);
const rubricMappings = ref<RubricMappingItem[]>([]);
const loading = ref(true);
const loadError = ref<string | null>(null);

// Highlight / selection state
const selectedCriterionName = ref<string | null>(null);
const highlightRef = ref<string | null>(null);

// Panel visibility toggles (for small screens or manual collapse)
const leftPanelVisible = ref(true);
const rightPanelVisible = ref(true);

function handleCriterionSelect(criterionName: string, sectionRef: string | null) {
  selectedCriterionName.value = criterionName;
  highlightRef.value = sectionRef ?? criterionName;
}

function handleCriterionDeselect() {
  selectedCriterionName.value = null;
  highlightRef.value = null;
}

function formatDate(dateStr: string): string {
  return new Intl.DateTimeFormat("tr-TR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(dateStr));
}

async function load() {
  loading.value = true;
  loadError.value = null;

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Kimlik doğrulaması gerekiyor.");

    // Fetch submission content
    const sub = await fetchSubmission(submissionId.value, token);
    submission.value = sub;

    // Fetch rubric mappings (student-defined section references)
    // The endpoint may not exist yet (backend #193); handle gracefully.
    try {
      const mappingsResp = await fetchRubricMappings(submissionId.value, token);
      rubricMappings.value = mappingsResp.mappings ?? [];
    } catch {
      rubricMappings.value = [];
    }

    // Fetch rubric criteria directly via deliverableId from the submission
    if (sub.deliverableId) {
      try {
        rubricCriteria.value = await fetchRubric(sub.deliverableId, token);
      } catch {
        rubricCriteria.value = [];
      }
    }
  } catch (err: unknown) {
    const msg =
      err && typeof err === "object" && "message" in err
        ? String((err as { message: string }).message)
        : "Teslim yüklenirken hata oluştu.";
    loadError.value = msg;
  } finally {
    loading.value = false;
  }
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
        Geri
      </button>

      <div class="h-5 w-px bg-slate-200 dark:bg-slate-700" />

      <FileText class="w-4 h-4 text-indigo-500 flex-shrink-0" />
      <h1 class="text-sm font-semibold text-slate-800 dark:text-slate-100 truncate">
        Teslim İnceleme
        <span
          v-if="submission"
          class="ml-2 text-xs font-normal text-slate-500 dark:text-slate-400"
        >
          — {{ submission.submissionId.slice(0, 8) }}…
        </span>
      </h1>

      <!-- Submission meta -->
      <div
        v-if="submission && !loading"
        class="ml-auto flex items-center gap-4 text-xs text-slate-500 dark:text-slate-400"
      >
        <span class="flex items-center gap-1">
          <Calendar class="w-3 h-3" />
          {{ formatDate(submission.revisedAt ?? submission.submittedAt) }}
          <span v-if="submission.revisedAt" class="text-amber-500">(revize)</span>
        </span>
        <span class="flex items-center gap-1">
          <Users class="w-3 h-3" />
          {{ submission.groupId.slice(0, 8) }}…
        </span>
      </div>

      <!-- Panel toggles -->
      <div class="flex items-center gap-1 ml-4">
        <button
          class="p-1.5 rounded hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          :class="leftPanelVisible ? 'text-indigo-500' : 'text-slate-400'"
          :title="leftPanelVisible ? 'Doküman panelini gizle' : 'Doküman panelini göster'"
          @click="leftPanelVisible = !leftPanelVisible"
        >
          <PanelLeft class="w-4 h-4" />
        </button>
        <button
          class="p-1.5 rounded hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          :class="rightPanelVisible ? 'text-indigo-500' : 'text-slate-400'"
          :title="rightPanelVisible ? 'Rubrik panelini gizle' : 'Rubrik panelini göster'"
          @click="rightPanelVisible = !rightPanelVisible"
        >
          <PanelRight class="w-4 h-4" />
        </button>
      </div>
    </header>

    <!-- Loading -->
    <div
      v-if="loading"
      class="flex-1 flex items-center justify-center"
    >
      <div class="flex flex-col items-center gap-3">
        <Loader2 class="w-8 h-8 text-indigo-500 animate-spin" />
        <p class="text-sm text-slate-500 dark:text-slate-400">Teslim yükleniyor…</p>
      </div>
    </div>

    <!-- Error -->
    <div
      v-else-if="loadError"
      class="flex-1 flex items-center justify-center p-8"
    >
      <div class="max-w-md w-full bg-red-50 dark:bg-red-900/20 rounded-xl border border-red-200 dark:border-red-800 p-6 flex gap-4">
        <AlertCircle class="w-5 h-5 text-red-500 flex-shrink-0 mt-0.5" />
        <div>
          <p class="font-semibold text-sm text-red-700 dark:text-red-300">Yükleme hatası</p>
          <p class="text-sm text-red-600 dark:text-red-400 mt-1">{{ loadError }}</p>
          <button
            class="mt-3 text-sm text-red-600 dark:text-red-400 underline hover:no-underline"
            @click="load"
          >
            Tekrar dene
          </button>
        </div>
      </div>
    </div>

    <!-- Split panel layout -->
    <div
      v-else-if="submission"
      class="flex-1 flex overflow-hidden min-h-0"
    >
      <!-- Left: Markdown document -->
      <section
        v-show="leftPanelVisible"
        class="flex flex-col overflow-hidden border-r border-slate-200 dark:border-slate-700 transition-all duration-200"
        :class="rightPanelVisible ? 'flex-1' : 'flex-1'"
        aria-label="Teslim dokümanı"
      >
        <!-- Section header -->
        <div class="px-4 py-2.5 bg-slate-50 dark:bg-slate-800/50 border-b border-slate-200 dark:border-slate-700 flex-shrink-0 flex items-center gap-2">
          <FileText class="w-3.5 h-3.5 text-slate-400" />
          <span class="text-xs font-medium text-slate-600 dark:text-slate-300 uppercase tracking-wide">
            Doküman
          </span>
          <span class="ml-auto text-xs text-slate-400 dark:text-slate-500 bg-slate-100 dark:bg-slate-800 px-2 py-0.5 rounded-full">
            Salt Okunur
          </span>
        </div>

        <!-- Markdown content -->
        <div class="flex-1 overflow-y-auto px-6 py-6">
          <div
            v-if="!submission.content"
            class="flex items-center justify-center h-full"
          >
            <p class="text-sm text-slate-400 dark:text-slate-500">Bu teslimde içerik bulunamadı.</p>
          </div>
          <MarkdownViewer
            v-else
            :content="submission.content"
            :highlight-ref="highlightRef"
          />
        </div>
      </section>

      <!-- Right: Rubric panel -->
      <section
        v-show="rightPanelVisible"
        class="flex flex-col overflow-hidden bg-white dark:bg-slate-900 transition-all duration-200"
        :class="leftPanelVisible ? 'w-80 flex-shrink-0 lg:w-96' : 'flex-1'"
        aria-label="Değerlendirme rubriği"
      >
        <RubricPanel
          :criteria="rubricCriteria"
          :mappings="rubricMappings"
          :selected-criterion-name="selectedCriterionName"
          @select="handleCriterionSelect"
          @deselect="handleCriterionDeselect"
        />
      </section>
    </div>
  </div>
</template>
