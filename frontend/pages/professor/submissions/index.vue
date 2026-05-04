<script setup lang="ts">
import { 
  ArrowLeft, 
  BookOpen, 
  Loader2, 
  AlertCircle,
  FileText,
  MessageSquare,
  Calendar
} from "lucide-vue-next";
import type { CommitteeSubmissionSummary, ProfessorCommittee } from "~/types/committee";

definePageMeta({
  middleware: "auth",
  roles: ["Professor"],
});

const { getAuthToken, fetchProfessorCommittees, fetchCommitteeSubmissions } = useApiClient();

const loading = ref(true);
const error = ref<string | null>(null);
const submissions = ref<(CommitteeSubmissionSummary & { committeeName: string })[]>([]);

async function loadSubmissions() {
  loading.value = true;
  error.value = null;
  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required");

    const committees = await fetchProfessorCommittees(token);
    
    // Fetch submissions for all committees in parallel
    const allSubmissionsResults = await Promise.all(
      committees.map(async (c) => {
        const subs = await fetchCommitteeSubmissions(c.committeeId, token);
        return subs.map(s => ({ ...s, committeeName: c.committeeName }));
      })
    );

    submissions.value = allSubmissionsResults.flat().sort((a, b) => 
      new Date(b.submittedAt).getTime() - new Date(a.submittedAt).getTime()
    );
  } catch (err: any) {
    error.value = err.message || "Failed to load submissions.";
  } finally {
    loading.value = false;
  }
}

function formatDate(dateStr: string): string {
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(dateStr));
}

onMounted(loadSubmissions);
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-5xl space-y-6">
      <!-- Header -->
      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-4">
            <div class="flex h-12 w-12 items-center justify-center rounded-xl bg-emerald-100 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-400">
              <BookOpen class="h-6 w-6" />
            </div>
            <div>
              <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
                Pending Reviews
              </h1>
              <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
                Manage and evaluate all student submissions.
              </p>
            </div>
          </div>
          <NuxtLink
            to="/professor/dashboard"
            class="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
          >
            <ArrowLeft class="mr-2 inline h-4 w-4" />
            Back to Dashboard
          </NuxtLink>
        </div>
      </header>

      <!-- Loading State -->
      <div v-if="loading" class="flex flex-col items-center justify-center rounded-2xl border border-slate-200 bg-white py-20 dark:border-slate-700 dark:bg-slate-800">
        <Loader2 class="h-8 w-8 animate-spin text-emerald-600 dark:text-emerald-400" />
        <p class="mt-4 text-sm text-slate-500 dark:text-slate-400">Loading submissions...</p>
      </div>

      <!-- Error State -->
      <div v-else-if="error" class="flex items-start gap-3 rounded-2xl border border-red-200 bg-red-50 p-6 text-red-900 dark:border-red-900/30 dark:bg-red-900/10 dark:text-red-400">
        <AlertCircle class="h-5 w-5 shrink-0" />
        <div>
          <h3 class="font-semibold">Error loading submissions</h3>
          <p class="mt-1 text-sm opacity-90">{{ error }}</p>
          <button @click="loadSubmissions" class="mt-3 text-sm font-semibold underline underline-offset-2">Try again</button>
        </div>
      </div>

      <!-- Empty State -->
      <div v-else-if="submissions.length === 0" class="flex flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 bg-white py-20 dark:border-slate-700 dark:bg-slate-800">
        <div class="flex h-16 w-16 items-center justify-center rounded-full bg-slate-100 dark:bg-slate-700">
          <FileText class="h-8 w-8 text-slate-400 dark:text-slate-500" />
        </div>
        <h3 class="mt-4 text-lg font-medium text-slate-900 dark:text-white">No submissions found</h3>
        <p class="mt-2 text-sm text-slate-500 dark:text-slate-400 text-center max-w-xs">
          There are no student submissions available for review in your committees at this time.
        </p>
      </div>

      <!-- Submissions List -->
      <div v-else class="grid gap-4">
        <div 
          v-for="submission in submissions" 
          :key="submission.submissionId"
          class="group overflow-hidden rounded-2xl border border-slate-200 bg-white transition-all hover:border-emerald-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-emerald-600"
        >
          <div class="flex flex-col sm:flex-row sm:items-center justify-between p-6 gap-4">
            <div class="space-y-1">
              <div class="flex items-center gap-2">
                <span class="text-xs font-semibold uppercase tracking-wider text-emerald-600 dark:text-emerald-400">
                  {{ submission.committeeName }}
                </span>
                <span class="h-1 w-1 rounded-full bg-slate-300 dark:bg-slate-600" />
                <span class="text-xs text-slate-500 dark:text-slate-400">
                  {{ submission.deliverableName }}
                </span>
              </div>
              <h3 class="text-lg font-semibold text-slate-900 dark:text-white">
                {{ submission.groupName }}
              </h3>
              <div class="flex items-center gap-4 pt-1">
                <div class="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
                  <Calendar class="h-3.5 w-3.5" />
                  {{ formatDate(submission.submittedAt) }}
                </div>
                <div class="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
                  <MessageSquare class="h-3.5 w-3.5" />
                  {{ submission.commentCount }} feedback
                </div>
              </div>
            </div>

            <NuxtLink
              :to="`/professor/submissions/${submission.submissionId}`"
              class="inline-flex items-center justify-center rounded-xl bg-slate-900 px-6 py-3 text-sm font-semibold text-white transition hover:bg-slate-800 dark:bg-emerald-600 dark:hover:bg-emerald-500"
            >
              Review Submission
            </NuxtLink>
          </div>
        </div>
      </div>
    </div>
  </main>
</template>
