<script setup lang="ts">
import { AlertCircle, ArrowLeft, Loader2 } from "lucide-vue-next";
import type { FinalGradeResponse } from "~/composables/useApiClient";
import { useAuthStore } from "~/stores/auth";

definePageMeta({
  middleware: "auth",
  roles: ["Student"],
});

const authStore = useAuthStore();
const { getAuthToken, calculateStudentGrade } = useApiClient();

const grade = ref<FinalGradeResponse | null>(null);
const isLoading = ref(true);
const error = ref<string | null>(null);

const studentId = computed(() => {
  if (authStore.userInfo?.userType !== "Student") return null;
  return authStore.userInfo.studentId;
});

function formatDecimal(value: number | null | undefined, digits = 4): string {
  if (value === null || value === undefined) return "—";
  if (!Number.isFinite(value)) return "—";
  return value.toFixed(digits);
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleString("en-US", { dateStyle: "medium", timeStyle: "short" });
}

async function loadGrade() {
  isLoading.value = true;
  error.value = null;

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required");
    if (!studentId.value) throw new Error("Student ID is missing.");

    grade.value = await calculateStudentGrade(studentId.value, token);
  } catch (err: unknown) {
    error.value =
      err && typeof err === "object" && "message" in err
        ? String(err.message)
        : "Failed to load final grade.";
  } finally {
    isLoading.value = false;
  }
}

onMounted(loadGrade);
</script>

<template>
  <main
    class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8"
  >
    <div class="mx-auto w-full max-w-6xl space-y-6">
      <NuxtLink
        to="/student/dashboard"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to Dashboard
      </NuxtLink>

      <header
        class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg"
      >
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
          Final Grade
        </h1>
        <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
          Review your final grade, completion ratio, and deliverable breakdown.
        </p>
      </header>

      <section
        v-if="isLoading"
        class="flex items-center justify-center rounded-2xl border border-slate-200 bg-white p-12 dark:border-slate-700 dark:bg-slate-800"
      >
        <Loader2 class="h-6 w-6 animate-spin text-blue-600 dark:text-blue-400" />
        <span class="ml-3 text-sm text-slate-600 dark:text-slate-400">Loading final grade...</span>
      </section>

      <section
        v-else-if="error"
        class="flex items-start gap-3 rounded-2xl border border-red-300 bg-red-50 p-4 text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300"
      >
        <AlertCircle class="mt-0.5 h-5 w-5 shrink-0" />
        <p class="text-sm">{{ error }}</p>
      </section>

      <template v-else-if="grade">
        <section class="grid gap-4 md:grid-cols-3">
          <article class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800">
            <p class="text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">Weighted Total</p>
            <p class="mt-2 text-2xl font-semibold text-slate-900 dark:text-white">{{ formatDecimal(grade.weightedTotal) }}</p>
          </article>
          <article class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800">
            <p class="text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">Completion Ratio</p>
            <p class="mt-2 text-2xl font-semibold text-slate-900 dark:text-white">{{ formatDecimal(grade.completionRatio) }}</p>
          </article>
          <article class="rounded-2xl border border-blue-200 bg-blue-50 p-5 shadow-sm dark:border-blue-800 dark:bg-blue-950/30">
            <p class="text-xs font-semibold uppercase tracking-wider text-blue-700 dark:text-blue-300">Final Grade</p>
            <p class="mt-2 text-2xl font-semibold text-blue-900 dark:text-blue-100">{{ formatDecimal(grade.finalGrade) }}</p>
          </article>
        </section>

        <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <h2 class="text-lg font-semibold text-slate-900 dark:text-white">Deliverable Breakdown</h2>
            <p class="text-xs text-slate-500 dark:text-slate-400">
              Last calculated: {{ formatDateTime(grade.calculatedAt) }}
            </p>
          </div>

          <div v-if="grade.deliverableBreakdown.length === 0" class="mt-4 rounded-lg border border-dashed border-slate-300 p-4 text-sm text-slate-600 dark:border-slate-600 dark:text-slate-400">
            No deliverable breakdown available.
          </div>

          <div v-else class="mt-4 overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-700">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-700/50">
                  <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Deliverable</th>
                  <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Base</th>
                  <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Scrum</th>
                  <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Review</th>
                  <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Scalar</th>
                  <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Scaled</th>
                  <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Weight</th>
                  <th class="px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Contribution</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100 dark:divide-slate-700">
                <tr v-for="item in grade.deliverableBreakdown" :key="item.deliverableId" class="hover:bg-slate-50 dark:hover:bg-slate-700">
                  <td class="px-3 py-2 font-medium text-slate-900 dark:text-white">{{ item.deliverableName }}</td>
                  <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ formatDecimal(item.baseGrade) }}</td>
                  <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ formatDecimal(item.scrumScalar) }}</td>
                  <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ formatDecimal(item.reviewScalar) }}</td>
                  <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ formatDecimal(item.deliverableScalar) }}</td>
                  <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ formatDecimal(item.scaledGrade) }}</td>
                  <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ formatDecimal(item.weight) }}</td>
                  <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ formatDecimal(item.weightedContribution) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>
    </div>
  </main>
</template>
