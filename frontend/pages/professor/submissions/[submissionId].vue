<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ArrowLeft, FileText, Loader2, Users } from "lucide-vue-next";
import type { DeliverableSubmissionDetailResponse } from "~/types/deliverable";

definePageMeta({
  middleware: "auth",
  roles: ["Professor"],
});

const route = useRoute();
const { getAuthToken, fetchDeliverableSubmission } = useApiClient();

const submission = ref<DeliverableSubmissionDetailResponse | null>(null);
const loading = ref(true);
const loadError = ref("");

const submissionId = computed(() => String(route.params.submissionId || ""));

function formatDateTime(value?: string | null): string {
  if (!value) return "Not available";

  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function parseErrorMessage(error: unknown, fallback: string): string {
  return error && typeof error === "object" && "message" in error
    ? String((error as { message?: string }).message || fallback)
    : fallback;
}

async function loadSubmission() {
  loading.value = true;
  loadError.value = "";

  try {
    const token = getAuthToken();
    if (!token) {
      throw new Error("Authentication required. Please log in again.");
    }

    submission.value = await fetchDeliverableSubmission(submissionId.value, token);
  } catch (error: unknown) {
    loadError.value = parseErrorMessage(error, "Unable to load the submission review panel.");
    submission.value = null;
  } finally {
    loading.value = false;
  }
}

watch(submissionId, loadSubmission, { immediate: true });
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-7xl space-y-6">
      <NuxtLink
        to="/professor/committees"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-700 transition hover:text-slate-900 dark:text-slate-300 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to Committees
      </NuxtLink>

      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <p class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
          Process 6 • Committee Review
        </p>
        <h1 class="mt-2 text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
          Submission Review Panel
        </h1>
        <p class="mt-2 max-w-3xl text-sm text-slate-600 dark:text-slate-400">
          Read the submitted document, leave committee feedback, and keep the full comment thread visible to the student team.
        </p>
      </header>

      <section v-if="loading" class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
        <div class="flex items-center gap-3 text-slate-600 dark:text-slate-400">
          <Loader2 class="h-5 w-5 animate-spin" />
          Loading submission…
        </div>
      </section>

      <section v-else-if="loadError" class="rounded-2xl border border-red-300 bg-red-50 p-6 text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-200">
        <p class="font-semibold">Unable to load submission</p>
        <p class="mt-2 text-sm">{{ loadError }}</p>
      </section>

      <div v-else-if="submission" class="grid gap-6 xl:grid-cols-[minmax(0,1.3fr)_minmax(360px,0.7fr)]">
        <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <div class="flex items-center gap-2">
                <FileText class="h-5 w-5 text-blue-600 dark:text-blue-400" />
                <h2 class="text-2xl font-semibold text-slate-900 dark:text-white">Submission {{ submission.submissionId.slice(0, 8) }}</h2>
              </div>
              <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
                Deliverable {{ submission.deliverableId }} • Group {{ submission.groupId }}
              </p>
            </div>
            <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700 dark:border-slate-700 dark:bg-slate-900/50 dark:text-slate-300">
              <p class="font-semibold text-slate-900 dark:text-white">Submitted</p>
              <p class="mt-1">{{ formatDateTime(submission.submittedAt) }}</p>
              <p v-if="submission.updatedAt" class="mt-1 text-xs text-slate-500 dark:text-slate-400">
                Updated: {{ formatDateTime(submission.updatedAt) }}
              </p>
            </div>
          </div>

          <div class="mt-6 rounded-2xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-slate-900/40">
            <div class="mb-5 flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-300">
              <Users class="h-4 w-4" />
              Submission content
            </div>
            <MarkdownViewer :content="submission.markdownContent" />
          </div>

        </section>

        <SubmissionCommentThread
          :submission-id="submission.submissionId"
          :can-comment="true"
          title="Committee comments"
          description="Leave feedback for the students and review the full comment history."
        />
      </div>
    </div>
  </main>
</template>