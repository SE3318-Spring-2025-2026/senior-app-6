<script setup lang="ts">
import {
  ArrowLeft,
  AlertCircle,
  Loader2,
  BarChart3,
  ClipboardList,
  Clock,
  Calendar,
  Users,
  CheckCircle2,
  Scale,
  UserCheck,
} from "lucide-vue-next";
import type { ProfessorCommittee } from "~/types/committee";

definePageMeta({
  middleware: "auth",
  roles: ["Professor"],
});

const { getAuthToken, fetchProfessorCommittees } = useApiClient();

const committees = ref<ProfessorCommittee[]>([]);
const loading = ref(true);
const loadError = ref<string | null>(null);

// Only committees that have at least one group with a submission
const gradeable = computed(() =>
  committees.value.filter((c) => c.groups.some((g) => g.submissionId))
);

const pending = computed(() =>
  committees.value.filter((c) => !c.groups.some((g) => g.submissionId))
);

function formatDate(dateStr: string | undefined): string {
  if (!dateStr) return "—";
  return new Intl.DateTimeFormat("tr-TR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(dateStr));
}

function daysUntil(dateStr: string | undefined): number | null {
  if (!dateStr) return null;
  return Math.ceil((new Date(dateStr).getTime() - Date.now()) / (1000 * 60 * 60 * 24));
}

function deadlineColor(dateStr: string | undefined): string {
  const days = daysUntil(dateStr);
  if (days === null) return "text-slate-500 dark:text-slate-400";
  if (days < 0) return "text-red-600 dark:text-red-400";
  if (days <= 3) return "text-amber-600 dark:text-amber-400";
  return "text-slate-600 dark:text-slate-400";
}

onMounted(async () => {
  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required.");
    committees.value = await fetchProfessorCommittees(token);
  } catch (err: unknown) {
    loadError.value =
      err && typeof err === "object" && "message" in err
        ? String((err as { message: string }).message)
        : "Failed to load committees.";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-5xl space-y-6">
      <NuxtLink
        to="/professor/dashboard"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to dashboard
      </NuxtLink>

      <!-- Header -->
      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <div class="flex items-center gap-3">
          <BarChart3 class="h-6 w-6 text-emerald-600 dark:text-emerald-400" />
          <div>
            <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
              Grade Summary
            </h1>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
              Committees with submissions ready to grade, deadlines, and progress.
            </p>
          </div>
        </div>
      </header>

      <!-- Loading -->
      <div v-if="loading" class="flex items-center justify-center rounded-2xl border border-slate-200 bg-white p-12 dark:border-slate-700 dark:bg-slate-800">
        <Loader2 class="h-5 w-5 animate-spin text-emerald-500" />
        <span class="ml-3 text-sm text-slate-500 dark:text-slate-400">Loading…</span>
      </div>

      <!-- Error -->
      <div v-else-if="loadError" class="flex items-start gap-3 rounded-2xl border border-red-300 bg-red-50 p-5 text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300">
        <AlertCircle class="mt-0.5 h-5 w-5 shrink-0" />
        <p class="text-sm">{{ loadError }}</p>
      </div>

      <template v-else>

        <!-- Ready to grade -->
        <section>
          <h2 class="mb-3 flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
            <CheckCircle2 class="h-4 w-4 text-emerald-500" />
            Ready to Grade ({{ gradeable.length }})
          </h2>

          <div v-if="gradeable.length === 0" class="rounded-2xl border border-dashed border-slate-300 bg-white py-10 text-center text-sm text-slate-400 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-500">
            No submissions to grade yet.
          </div>

          <div v-else class="space-y-4">
            <article
              v-for="committee in gradeable"
              :key="committee.committeeId"
              class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800"
            >
              <!-- Committee header -->
              <div class="flex items-center justify-between gap-4 border-b border-slate-100 px-5 py-4 dark:border-slate-700">
                <div class="flex items-center gap-3">
                  <div
                    class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border"
                    :class="committee.professorRole === 'ADVISOR'
                      ? 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-400'
                      : 'border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-700 dark:bg-blue-950/50 dark:text-blue-400'"
                  >
                    <UserCheck v-if="committee.professorRole === 'ADVISOR'" class="h-4 w-4" />
                    <Scale v-else class="h-4 w-4" />
                  </div>
                  <div>
                    <p class="font-semibold text-slate-900 dark:text-white">{{ committee.committeeName }}</p>
                    <div class="mt-0.5 flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
                      <span
                        class="rounded-full px-1.5 py-0.5 font-medium"
                        :class="committee.professorRole === 'ADVISOR'
                          ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300'
                          : 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300'"
                      >
                        {{ committee.professorRole }}
                      </span>
                      <span v-if="committee.deliverableName">{{ committee.deliverableName }}</span>
                      <span v-if="committee.deliverableWeight != null" class="rounded-full bg-indigo-50 px-1.5 py-0.5 font-medium text-indigo-600 dark:bg-indigo-900/30 dark:text-indigo-300">
                        {{ committee.deliverableWeight }}%
                      </span>
                    </div>
                  </div>
                </div>

                <!-- Review deadline -->
                <div v-if="committee.reviewDeadline" class="hidden shrink-0 text-right sm:block">
                  <p class="text-[10px] uppercase tracking-wide text-slate-400 dark:text-slate-500">Review deadline</p>
                  <p class="mt-0.5 flex items-center gap-1 text-xs font-medium" :class="deadlineColor(committee.reviewDeadline)">
                    <Calendar class="h-3 w-3" />
                    {{ formatDate(committee.reviewDeadline) }}
                    <span v-if="daysUntil(committee.reviewDeadline) !== null">
                      ({{ daysUntil(committee.reviewDeadline)! < 0 ? 'overdue' : daysUntil(committee.reviewDeadline) + 'd' }})
                    </span>
                  </p>
                </div>
              </div>

              <!-- Groups with submissions -->
              <div class="divide-y divide-slate-100 dark:divide-slate-700">
                <div
                  v-for="group in committee.groups.filter(g => g.submissionId)"
                  :key="group.groupId"
                  class="flex items-center justify-between gap-4 px-5 py-3.5"
                >
                  <div class="flex items-center gap-2.5">
                    <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300">
                      <span class="text-xs font-bold">{{ group.groupName.charAt(0).toUpperCase() }}</span>
                    </div>
                    <span class="text-sm font-medium text-slate-800 dark:text-slate-100">{{ group.groupName }}</span>
                  </div>

                  <div class="flex items-center gap-2">
                    <NuxtLink
                      :to="`/professor/submission-review/${group.submissionId}`"
                      class="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
                    >
                      Review
                    </NuxtLink>
                    <NuxtLink
                      :to="`/committee/submissions/${group.submissionId}/grade`"
                      class="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white shadow-sm transition hover:bg-emerald-700"
                    >
                      <ClipboardList class="h-3.5 w-3.5" />
                      Grade
                    </NuxtLink>
                  </div>
                </div>

                <!-- Groups without submissions (greyed out) -->
                <div
                  v-for="group in committee.groups.filter(g => !g.submissionId)"
                  :key="group.groupId"
                  class="flex items-center gap-2.5 px-5 py-3 opacity-40"
                >
                  <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-slate-100 dark:bg-slate-700">
                    <span class="text-xs font-bold text-slate-400">{{ group.groupName.charAt(0).toUpperCase() }}</span>
                  </div>
                  <span class="text-sm text-slate-500 dark:text-slate-500">{{ group.groupName }}</span>
                  <span class="ml-auto text-xs text-slate-400 dark:text-slate-500 italic">No submission</span>
                </div>
              </div>
            </article>
          </div>
        </section>

        <!-- Committees awaiting submission -->
        <section v-if="pending.length > 0">
          <h2 class="mb-3 flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">
            <Clock class="h-4 w-4" />
            Awaiting Submission ({{ pending.length }})
          </h2>

          <div class="overflow-hidden rounded-2xl border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800">
            <div
              v-for="(committee, idx) in pending"
              :key="committee.committeeId"
              class="flex items-center justify-between gap-4 px-5 py-3.5"
              :class="idx < pending.length - 1 ? 'border-b border-slate-100 dark:border-slate-700' : ''"
            >
              <div class="flex items-center gap-3 min-w-0">
                <Users class="h-4 w-4 shrink-0 text-slate-400" />
                <span class="truncate text-sm text-slate-600 dark:text-slate-400">{{ committee.committeeName }}</span>
                <span v-if="committee.deliverableName" class="shrink-0 text-xs text-slate-400 dark:text-slate-500">
                  — {{ committee.deliverableName }}
                </span>
              </div>
              <span v-if="committee.submissionDeadline" class="shrink-0 text-xs" :class="deadlineColor(committee.submissionDeadline)">
                Due {{ formatDate(committee.submissionDeadline) }}
              </span>
            </div>
          </div>
        </section>

        <!-- No committees at all -->
        <div v-if="committees.length === 0" class="flex flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 bg-white py-16 dark:border-slate-600 dark:bg-slate-800">
          <BarChart3 class="h-10 w-10 text-slate-300 dark:text-slate-600" />
          <p class="mt-4 text-sm text-slate-500 dark:text-slate-400">You are not assigned to any committees yet.</p>
        </div>

      </template>
    </div>
  </main>
</template>
