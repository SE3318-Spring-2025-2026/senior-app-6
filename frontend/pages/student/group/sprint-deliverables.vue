<script setup lang="ts">
import {
  ArrowLeft,
  AlertCircle,
  Loader2,
  GitBranch,
  FileCheck,
  Link2,
  Calendar,
  CheckCircle2,
  Clock,
} from "lucide-vue-next";
import type { ActiveSprintResponse, SprintDeliverableMappingItem } from "~/types/sprint";
import type { StudentDeliverable } from "~/types/submission";

definePageMeta({
  middleware: "auth",
  roles: ["Student"],
});

const { getAuthToken, fetchActiveSprint, fetchStudentDeliverables, fetchSprintDeliverableMappingsStudent } = useApiClient();

type SprintState = "loading" | "loaded" | "no-sprint" | "error";

const sprintState = ref<SprintState>("loading");
const sprint = ref<ActiveSprintResponse | null>(null);
const deliverables = ref<StudentDeliverable[]>([]);
const sprintMappings = ref<SprintDeliverableMappingItem[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);

const sprintDateRange = computed(() => {
  if (!sprint.value) return "";
  const fmt = (d: string) =>
    new Intl.DateTimeFormat("en-US", { dateStyle: "medium" }).format(new Date(d));
  return `${fmt(sprint.value.startDate)} – ${fmt(sprint.value.endDate)}`;
});

function formatDate(dateStr: string): string {
  try {
    return new Intl.DateTimeFormat("en-US", { dateStyle: "medium" }).format(new Date(dateStr));
  } catch {
    return dateStr;
  }
}

function isOverdue(dateStr: string): boolean {
  return new Date(dateStr) < new Date();
}

const deliverableTypeColor: Record<string, string> = {
  Proposal: "bg-cyan-100 text-cyan-700 dark:bg-cyan-900/40 dark:text-cyan-300",
  SoW: "bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300",
  Demonstration: "bg-violet-100 text-violet-700 dark:bg-violet-900/40 dark:text-violet-300",
};

const totalWeight = computed(() =>
  deliverables.value.reduce((sum, d) => sum + (d.weight ?? 0), 0)
);

const submittedCount = computed(
  () => deliverables.value.filter((d) => d.submissionStatus === "SUBMITTED").length
);

onMounted(async () => {
  const token = getAuthToken();
  if (!token) {
    error.value = "Authentication required.";
    loading.value = false;
    return;
  }

  try {
    const [deliverableList] = await Promise.all([
      fetchStudentDeliverables(token),
      fetchActiveSprint(token)
        .then(async (s) => {
          sprint.value = s;
          sprintState.value = "loaded";
          try {
            sprintMappings.value = await fetchSprintDeliverableMappingsStudent(s.sprintId, token);
          } catch {
            sprintMappings.value = [];
          }
        })
        .catch((err: { status?: number }) => {
          sprintState.value = err.status === 404 ? "no-sprint" : "error";
        }),
    ]);
    deliverables.value = deliverableList;
  } catch (err: unknown) {
    error.value =
      err && typeof err === "object" && "message" in err
        ? String((err as { message: string }).message)
        : "Failed to load data.";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-4xl space-y-6">
      <NuxtLink
        to="/student/dashboard"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to dashboard
      </NuxtLink>

      <!-- Header -->
      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <div class="flex items-center gap-3">
          <Link2 class="h-6 w-6 text-indigo-600 dark:text-indigo-400" />
          <div>
            <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white">
              Sprint &amp; Deliverables
            </h1>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
              Read-only overview of deliverables and their contribution to your final grade.
            </p>
          </div>
        </div>
      </header>

      <!-- Load error -->
      <div
        v-if="error"
        class="flex items-start gap-3 rounded-2xl border border-red-300 bg-red-50 p-4 text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300"
      >
        <AlertCircle class="mt-0.5 h-5 w-5 shrink-0" />
        <p class="text-sm">{{ error }}</p>
      </div>

      <!-- Loading -->
      <div
        v-else-if="loading"
        class="flex items-center justify-center rounded-2xl border border-slate-200 bg-white p-12 dark:border-slate-700 dark:bg-slate-800"
      >
        <Loader2 class="h-5 w-5 animate-spin text-indigo-500" />
        <span class="ml-3 text-sm text-slate-500 dark:text-slate-400">Loading…</span>
      </div>

      <template v-else>
        <!-- Active Sprint card -->
        <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800">
          <div class="flex items-center gap-2 mb-3">
            <GitBranch class="h-4 w-4 text-blue-500" />
            <h2 class="text-sm font-semibold text-slate-700 dark:text-slate-200">Active Sprint</h2>
          </div>

          <div v-if="sprintState === 'loading'" class="text-sm text-slate-400 animate-pulse">Loading sprint…</div>

          <p v-else-if="sprintState === 'no-sprint'" class="text-sm text-slate-500 dark:text-slate-400">
            No active sprint at the moment.
          </p>

          <p v-else-if="sprintState === 'error'" class="text-sm text-red-500 dark:text-red-400">
            Could not load sprint info.
          </p>

          <template v-else-if="sprint">
            <div class="flex flex-wrap items-center gap-3">
              <span class="flex items-center gap-1.5 text-sm font-medium text-slate-800 dark:text-slate-100">
                <Calendar class="h-3.5 w-3.5 text-slate-400" />
                {{ sprintDateRange }}
              </span>
              <span
                v-if="sprint.daysRemaining != null"
                class="inline-flex items-center rounded-full border border-blue-200 bg-blue-50 px-2.5 py-0.5 text-xs font-medium text-blue-700 dark:border-blue-800 dark:bg-blue-900/30 dark:text-blue-300"
              >
                {{ sprint.daysRemaining }}d remaining
              </span>
              <span
                v-if="sprint.storyPointTarget != null"
                class="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-2.5 py-0.5 text-xs font-medium text-slate-600 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-400"
              >
                {{ sprint.storyPointTarget }} SP target
              </span>
            </div>
          </template>
        </section>

        <!-- Sprint-Deliverable Mappings -->
        <section
          v-if="sprintState === 'loaded' && sprintMappings.length > 0"
          class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800"
        >
          <div class="flex items-center gap-2 mb-3">
            <Link2 class="h-4 w-4 text-purple-500" />
            <h2 class="text-sm font-semibold text-slate-700 dark:text-slate-200">Sprint Deliverable Contributions</h2>
          </div>
          <ul class="space-y-2">
            <li
              v-for="m in sprintMappings"
              :key="m.id"
              class="flex items-center justify-between rounded-lg bg-purple-50 px-4 py-2 dark:bg-purple-900/20"
            >
              <span class="text-sm text-slate-700 dark:text-slate-300">{{ m.deliverableName }}</span>
              <span class="text-sm font-semibold text-purple-700 dark:text-purple-300">{{ m.contributionPercentage }}%</span>
            </li>
          </ul>
        </section>

        <!-- Deliverables table -->
        <section class="rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800 overflow-hidden">
          <div class="flex items-center justify-between gap-3 border-b border-slate-100 px-6 py-4 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/60">
            <div class="flex items-center gap-2">
              <FileCheck class="h-4 w-4 text-emerald-500" />
              <h2 class="text-sm font-semibold text-slate-700 dark:text-slate-200">Deliverables</h2>
            </div>
            <div class="flex items-center gap-3 text-xs text-slate-500 dark:text-slate-400">
              <span>{{ submittedCount }}/{{ deliverables.length }} submitted</span>
              <span class="font-medium text-indigo-600 dark:text-indigo-400">Total weight: {{ totalWeight }}%</span>
            </div>
          </div>

          <div
            v-if="deliverables.length === 0"
            class="px-6 py-10 text-center text-sm text-slate-400 dark:text-slate-500"
          >
            No deliverables published yet.
          </div>

          <div v-else class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-slate-100 dark:border-slate-700 bg-white dark:bg-slate-800/40">
                  <th class="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Deliverable</th>
                  <th class="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Deadline</th>
                  <th class="px-5 py-3 text-right text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Weight</th>
                  <th class="px-5 py-3 text-right text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Status</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100 dark:divide-slate-700">
                <tr
                  v-for="d in deliverables"
                  :key="d.id"
                  class="transition hover:bg-slate-50 dark:hover:bg-slate-700/40"
                >
                  <td class="px-5 py-3.5">
                    <div class="flex items-center gap-2">
                      <span class="font-medium text-slate-900 dark:text-white">{{ d.name }}</span>
                      <span
                        class="rounded-full px-2 py-0.5 text-[10px] font-medium"
                        :class="deliverableTypeColor[d.type] ?? 'bg-slate-100 text-slate-600'"
                      >
                        {{ d.type }}
                      </span>
                    </div>
                  </td>
                  <td class="px-5 py-3.5">
                    <span
                      class="flex items-center gap-1 text-xs"
                      :class="isOverdue(d.submissionDeadline) && d.submissionStatus !== 'SUBMITTED'
                        ? 'text-red-600 dark:text-red-400'
                        : 'text-slate-600 dark:text-slate-400'"
                    >
                      <Clock class="h-3 w-3" />
                      {{ formatDate(d.submissionDeadline) }}
                    </span>
                  </td>
                  <td class="px-5 py-3.5 text-right">
                    <span class="inline-flex items-center rounded-full bg-indigo-50 px-2.5 py-0.5 text-xs font-semibold text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300">
                      {{ d.weight }}%
                    </span>
                  </td>
                  <td class="px-5 py-3.5 text-right">
                    <span
                      class="inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium"
                      :class="d.submissionStatus === 'SUBMITTED'
                        ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300'
                        : 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-400'"
                    >
                      <CheckCircle2
                        v-if="d.submissionStatus === 'SUBMITTED'"
                        class="h-3 w-3"
                      />
                      {{ d.submissionStatus === "SUBMITTED" ? "Submitted" : "Not Submitted" }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Weight bar -->
          <div
            v-if="deliverables.length > 0"
            class="border-t border-slate-100 dark:border-slate-700 px-5 py-3"
          >
            <p class="mb-1.5 text-xs text-slate-500 dark:text-slate-400">Grade weight distribution</p>
            <div class="flex h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-700">
              <div
                v-for="d in deliverables"
                :key="d.id"
                :title="`${d.name}: ${d.weight}%`"
                class="h-full transition-all first:rounded-l-full last:rounded-r-full"
                :class="d.type === 'Proposal'
                  ? 'bg-cyan-400'
                  : d.type === 'SoW'
                    ? 'bg-amber-400'
                    : 'bg-violet-400'"
                :style="{ width: `${d.weight}%` }"
              />
            </div>
            <div class="mt-1.5 flex flex-wrap gap-3">
              <span
                v-for="d in deliverables"
                :key="d.id"
                class="flex items-center gap-1 text-[10px] text-slate-500 dark:text-slate-400"
              >
                <span
                  class="inline-block h-2 w-2 rounded-full"
                  :class="d.type === 'Proposal' ? 'bg-cyan-400' : d.type === 'SoW' ? 'bg-amber-400' : 'bg-violet-400'"
                />
                {{ d.name }} {{ d.weight }}%
              </span>
            </div>
          </div>
        </section>
      </template>
    </div>
  </main>
</template>
