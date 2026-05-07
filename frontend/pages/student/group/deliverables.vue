<script setup lang="ts">
  import {
    ArrowLeft,
    AlertCircle,
    CheckCircle2,
    Clock,
    FileCheck,
    CalendarClock,
    CalendarCheck,
    Pencil,
    Send,
    Eye,
    Loader2,
  } from "lucide-vue-next";
  import type { StudentDeliverable } from "~/types/submission";
  import type { GroupDetailResponse, GroupMemberRole } from "~/types/group";
  import { useAuthStore } from "~/stores/auth";

  definePageMeta({
    middleware: "auth",
    roles: ["Student"],
  });

  const router = useRouter();
  const authStore = useAuthStore();
  const { getAuthToken, fetchStudentDeliverables, fetchMyGroup } = useApiClient();

  const deliverables = ref<StudentDeliverable[]>([]);
  const group = ref<GroupDetailResponse | null>(null);
  const loading = ref(true);
  const errorMessage = ref<string | null>(null);

  const myRole = computed<GroupMemberRole | null>(() => {
    if (!group.value || !authStore.userInfo) return null;
    const me = group.value.members.find((m) => m.studentId === authStore.userInfo!.id);
    return me?.role ?? null;
  });

  const isTeamLeader = computed(() => myRole.value === "TEAM_LEADER");

  async function load() {
    loading.value = true;
    errorMessage.value = null;
    try {
      const token = getAuthToken();
      if (!token) throw new Error("Authentication required.");

      const [deliverablesResult, groupResult] = await Promise.allSettled([
        fetchStudentDeliverables(token),
        fetchMyGroup(token),
      ]);

      if (deliverablesResult.status === "fulfilled") {
        deliverables.value = deliverablesResult.value;
      } else {
        throw deliverablesResult.reason;
      }

      if (groupResult.status === "fulfilled") {
        group.value = groupResult.value;
      } else {
        group.value = null;
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load deliverables.";
      errorMessage.value = message;
    } finally {
      loading.value = false;
    }
  }

  function formatDate(dateStr: string) {
    try {
      return new Intl.DateTimeFormat("tr-TR", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(dateStr));
    } catch {
      return dateStr;
    }
  }

  function isPast(dateStr: string): boolean {
    return new Date(dateStr).getTime() < Date.now();
  }

  type ActionKind = "start" | "edit" | "view" | "locked";

  function actionFor(d: StudentDeliverable): ActionKind {
    const submissionPassed = isPast(d.submissionDeadline);
    const reviewPassed = isPast(d.reviewDeadline);

    if (d.submissionStatus === "NOT_SUBMITTED") {
      if (isTeamLeader.value && !submissionPassed) return "start";
      return "locked";
    }
    if (isTeamLeader.value && !reviewPassed) return "edit";
    return "view";
  }

  function statusLabel(status: StudentDeliverable["submissionStatus"]) {
    return status === "SUBMITTED" ? "Submitted" : "Not Submitted";
  }

  function go(d: StudentDeliverable) {
    router.push(`/student/submit/${d.id}`);
  }

  function deadlineState(deadline: string) {
    const passed = isPast(deadline);
    if (passed) return "passed";
    const ms = new Date(deadline).getTime() - Date.now();
    const days = ms / (1000 * 60 * 60 * 24);
    if (days < 3) return "soon";
    return "ok";
  }

  onMounted(load);
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-6xl space-y-6">
      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90">
        <div class="flex items-center justify-between gap-4">
          <div class="flex items-center gap-3">
            <button
              type="button"
              class="rounded-lg border border-slate-300 bg-white p-2 text-slate-600 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
              @click="router.push('/student/dashboard')"
            >
              <ArrowLeft class="h-4 w-4" />
            </button>
            <div>
              <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
                Deliverables
              </h1>
              <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
                Track upcoming deadlines and your group's submission status.
              </p>
            </div>
          </div>
          <div v-if="group" class="hidden text-right md:block">
            <p class="text-xs uppercase tracking-wide text-slate-500 dark:text-slate-400">Group</p>
            <p class="text-sm font-medium text-slate-800 dark:text-slate-100">{{ group.groupName }}</p>
            <p class="text-xs text-slate-500 dark:text-slate-400">
              {{ isTeamLeader ? "Team Leader" : "Member" }}
            </p>
          </div>
        </div>
      </header>

      <div v-if="loading" class="flex items-center justify-center rounded-2xl border border-slate-200 bg-white p-12 text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-400">
        <Loader2 class="mr-2 h-4 w-4 animate-spin" />
        Loading deliverables…
      </div>

      <div v-else-if="errorMessage" class="flex items-center gap-3 rounded-2xl border border-red-200 bg-red-50 p-5 text-sm text-red-700 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-300">
        <AlertCircle class="h-5 w-5 flex-shrink-0" />
        {{ errorMessage }}
      </div>

      <div v-else-if="deliverables.length === 0" class="rounded-2xl border border-slate-200 bg-white p-12 text-center text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-400">
        <FileCheck class="mx-auto mb-3 h-8 w-8 text-slate-400" />
        No deliverables have been published yet.
      </div>

      <div v-else class="grid gap-4 md:grid-cols-2">
        <article
          v-for="d in deliverables"
          :key="d.id"
          class="flex flex-col rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:shadow-md dark:border-slate-700 dark:bg-slate-800"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0 flex-1">
              <p class="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                {{ d.type }}
              </p>
              <h2 class="mt-1 text-lg font-semibold text-slate-900 dark:text-white">
                {{ d.name }}
              </h2>
            </div>
            <span
              class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium"
              :class="{
                'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300': d.submissionStatus === 'SUBMITTED',
                'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300': d.submissionStatus === 'NOT_SUBMITTED',
              }"
            >
              <CheckCircle2 v-if="d.submissionStatus === 'SUBMITTED'" class="h-3.5 w-3.5" />
              <Clock v-else class="h-3.5 w-3.5" />
              {{ statusLabel(d.submissionStatus) }}
            </span>
          </div>

          <dl class="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2">
            <div class="rounded-lg border border-slate-100 bg-slate-50 p-3 dark:border-slate-700 dark:bg-slate-900/40">
              <dt class="flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                <CalendarClock class="h-3.5 w-3.5" />
                Submission Deadline
              </dt>
              <dd
                class="mt-1 text-sm font-medium"
                :class="{
                  'text-red-600 dark:text-red-400': deadlineState(d.submissionDeadline) === 'passed',
                  'text-amber-600 dark:text-amber-400': deadlineState(d.submissionDeadline) === 'soon',
                  'text-slate-800 dark:text-slate-100': deadlineState(d.submissionDeadline) === 'ok',
                }"
              >
                {{ formatDate(d.submissionDeadline) }}
              </dd>
            </div>
            <div class="rounded-lg border border-slate-100 bg-slate-50 p-3 dark:border-slate-700 dark:bg-slate-900/40">
              <dt class="flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                <CalendarCheck class="h-3.5 w-3.5" />
                Review Deadline
              </dt>
              <dd
                class="mt-1 text-sm font-medium"
                :class="{
                  'text-red-600 dark:text-red-400': deadlineState(d.reviewDeadline) === 'passed',
                  'text-amber-600 dark:text-amber-400': deadlineState(d.reviewDeadline) === 'soon',
                  'text-slate-800 dark:text-slate-100': deadlineState(d.reviewDeadline) === 'ok',
                }"
              >
                {{ formatDate(d.reviewDeadline) }}
              </dd>
            </div>
          </dl>

          <div class="mt-5 flex items-center justify-end">
            <button
              v-if="actionFor(d) === 'start'"
              type="button"
              class="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-indigo-700"
              @click="go(d)"
            >
              <Send class="h-4 w-4" />
              Start Submission
            </button>
            <button
              v-else-if="actionFor(d) === 'edit'"
              type="button"
              class="inline-flex items-center gap-2 rounded-lg bg-amber-500 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-amber-600"
              @click="go(d)"
            >
              <Pencil class="h-4 w-4" />
              Edit Submission
            </button>
            <button
              v-else-if="actionFor(d) === 'view'"
              type="button"
              class="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600"
              @click="go(d)"
            >
              <Eye class="h-4 w-4" />
              View Read-Only
            </button>
            <span
              v-else
              class="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-4 py-2 text-sm font-medium text-slate-400 dark:border-slate-700 dark:bg-slate-900/40 dark:text-slate-500"
            >
              <Clock class="h-4 w-4" />
              Awaiting Team Leader
            </span>
          </div>
        </article>
      </div>
    </div>
  </main>
</template>
