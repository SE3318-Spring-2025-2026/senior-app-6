<script setup lang="ts">
	import { LogOut, GitBranch, FileCheck, Users, Send, Clock, CheckCircle2, AlertCircle } from "lucide-vue-next";
	import { useAuthStore } from "~/stores/auth";
  import { useAuthStore } from "~/stores/auth";                                                                                                                                           
  import type { StudentDeliverable } from "~/types/submission";
  import type { ActiveSprintResponse } from "~/types/sprint";                                                                                                                             

  definePageMeta({
      middleware: "auth",
      roles: ["Student"],
  });

  const router = useRouter();
  const authStore = useAuthStore();
  const { getAuthToken, fetchStudentDeliverables, fetchActiveSprint } = useApiClient();

  // Deliverables
  const deliverables = ref<StudentDeliverable[]>([]);
  const loadingDeliverables = ref(true);
  const deliverablesError = ref<string | null>(null);

  async function loadDeliverables() {
      loadingDeliverables.value = true;
      deliverablesError.value = null;
      try {
          const token = getAuthToken();
          if (token) {
              deliverables.value = await fetchStudentDeliverables(token);
          }
      } catch {
          deliverablesError.value = "Failed to load deliverables.";
      } finally {
          loadingDeliverables.value = false;
      }
  }

  function formatDate(dateStr: string) {
      try {
          return new Intl.DateTimeFormat("tr-TR", { dateStyle: "medium" }).format(new Date(dateStr));
      } catch {
          return dateStr;
      }
  }

  // Active sprint
  type SprintState = "loading" | "loaded" | "no-sprint" | "error";
  const sprintState = ref<SprintState>("loading");
  const sprint = ref<ActiveSprintResponse | null>(null);

  const sprintDateRange = computed(() => {
      if (!sprint.value) return "";
      const fmt = (d: string) =>
          new Intl.DateTimeFormat("en-US", { dateStyle: "medium" }).format(new Date(d));
      return `${fmt(sprint.value.startDate)} – ${fmt(sprint.value.endDate)}`;
  });

  async function loadActiveSprint() {
      const token = getAuthToken();
      if (!token) return;
      try {
          sprint.value = await fetchActiveSprint(token);
          sprintState.value = "loaded";
      } catch (err: unknown) {
          const apiError = err as { status?: number };
          sprintState.value = apiError.status === 404 ? "no-sprint" : "error";
      }
  }

  onMounted(() => {
      void loadDeliverables();
      void loadActiveSprint();
  });

  function handleLogout() {
      authStore.logout();
      router.push("/auth/login");
  }

	onMounted(loadDeliverables);
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-5xl space-y-6">
      <!-- Header -->
      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
              Student Dashboard
            </h1>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Track your sprints, deliverables, and project progress.
            </p>
          </div>
          <button
            class="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
            @click="handleLogout"
          >
            <LogOut class="mr-2 inline h-4 w-4" />
            Sign Out
          </button>
        </div>
      </header>

      <!-- Quick nav cards -->
      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <NuxtLink
          to="/student/group/sprint"
          class="block rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition hover:border-blue-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-blue-600"
        >
          <GitBranch class="h-8 w-8 text-blue-600 dark:text-blue-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white">Current Sprint</h3>

          <!-- Loading skeleton -->
          <div v-if="sprintState === 'loading'" class="mt-3 space-y-2 animate-pulse">
            <div class="h-3.5 w-36 rounded bg-slate-200 dark:bg-slate-700"></div>
            <div class="h-3.5 w-24 rounded bg-slate-200 dark:bg-slate-700"></div>
          </div>

          <!-- No active sprint -->
          <p v-else-if="sprintState === 'no-sprint'" class="mt-1 text-sm text-slate-500 dark:text-slate-400">
            No active sprint found.
          </p>

          <!-- Error -->
          <p v-else-if="sprintState === 'error'" class="mt-1 text-sm text-red-500 dark:text-red-400">
            Couldn't load sprint info.
          </p>

          <!-- Loaded -->
          <template v-else-if="sprintState === 'loaded' && sprint">
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              {{ sprintDateRange }}
            </p>
            <div class="mt-3 flex flex-wrap gap-2">
              <span
                v-if="sprint.daysRemaining != null"
                class="inline-flex items-center rounded-full border border-blue-200 bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700 dark:border-blue-800 dark:bg-blue-900/30 dark:text-blue-300"
              >
                {{ sprint.daysRemaining }}d remaining
              </span>
              <span
                v-if="sprint.storyPointTarget != null"
                class="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-medium text-slate-600 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-400"
              >
                {{ sprint.storyPointTarget }} SP target
              </span>
            </div>
          </template>
        </NuxtLink>

        <NuxtLink
          to="/student/group/deliverables"
          class="block rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition hover:border-emerald-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-emerald-600"
        >
          <FileCheck class="h-8 w-8 text-emerald-600 dark:text-emerald-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white">Deliverables</h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            View all deliverables, deadlines, and submission status.
          </p>
        </NuxtLink>

        <NuxtLink
          to="/student/group"
          class="block rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition hover:border-violet-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-violet-600"
        >
          <Users class="h-8 w-8 text-violet-600 dark:text-violet-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white">Group Hub</h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Open your group page to create a team, review members, and check your current group state.
          </p>
        </NuxtLink>
      </div>

      <!-- Deliverables section -->
      <section class="rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800 overflow-hidden">
        <div class="flex items-center gap-2 px-6 py-4 border-b border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/60">
          <FileCheck class="w-4 h-4 text-emerald-500" />
          <h2 class="text-sm font-semibold text-slate-700 dark:text-slate-200">Deliverables</h2>
        </div>

        <!-- Loading -->
        <div v-if="loadingDeliverables" class="px-6 py-8 text-center text-sm text-slate-400 dark:text-slate-500">
          Loading…
        </div>

        <!-- Error -->
        <div v-else-if="deliverablesError" class="flex items-center gap-3 px-6 py-5 text-sm text-red-600 dark:text-red-400">
          <AlertCircle class="w-4 h-4 flex-shrink-0" />
          {{ deliverablesError }}
        </div>

        <!-- Empty -->
        <div v-else-if="deliverables.length === 0" class="px-6 py-8 text-center text-sm text-slate-400 dark:text-slate-500">
          No deliverables have been published yet.
        </div>

        <!-- List -->
        <ul v-else class="divide-y divide-slate-100 dark:divide-slate-700/60">
          <li
            v-for="d in deliverables"
            :key="d.id"
            class="flex items-center gap-4 px-6 py-4"
          >
            <!-- Status icon -->
            <span class="flex-shrink-0">
              <CheckCircle2
                v-if="d.submissionStatus === 'SUBMITTED'"
                class="w-5 h-5 text-emerald-500"
              />
              <Clock v-else class="w-5 h-5 text-slate-400 dark:text-slate-500" />
            </span>

            <!-- Info -->
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-slate-800 dark:text-slate-100 truncate">{{ d.name }}</p>
              <div class="flex flex-wrap items-center gap-x-3 gap-y-0.5 mt-0.5">
                <span class="text-xs text-slate-500 dark:text-slate-400">
                  Due: {{ formatDate(d.submissionDeadline) }}
                </span>
                <span
                  class="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium"
                  :class="{
                    'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300': d.submissionStatus === 'SUBMITTED',
                    'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-400': d.submissionStatus === 'NOT_SUBMITTED',
                  }"
                >
                  {{ d.submissionStatus === 'SUBMITTED' ? 'Submitted' : 'Not Submitted' }}
                </span>
              </div>
            </div>

            <!-- Action button -->
            <NuxtLink
              :to="`/student/submit/${d.id}`"
              class="flex-shrink-0 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-all border"
              :class="d.submissionStatus === 'NOT_SUBMITTED'
                ? 'bg-indigo-600 text-white border-indigo-600 hover:bg-indigo-700 shadow-sm'
                : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50 dark:bg-slate-700 dark:text-slate-300 dark:border-slate-600 dark:hover:bg-slate-600'"
            >
              <Send class="w-3.5 h-3.5" />
              {{ d.submissionStatus === 'NOT_SUBMITTED' ? 'Submit' : 'Update' }}
            </NuxtLink>
          </li>
        </ul>
      </section>
    </div>
  </main>
</template>
