<script setup lang="ts">
	import { LogOut, GitBranch, FileCheck, Users } from "lucide-vue-next";
	import { useAuthStore } from "~/stores/auth";
	import type { ActiveSprintResponse } from "~/types/sprint";

	definePageMeta({
		middleware: "auth",
		roles: ["Student"],
	});

	const router = useRouter();
	const authStore = useAuthStore();
	const { getAuthToken, fetchActiveSprint } = useApiClient();

	type SprintState = "loading" | "loaded" | "no-sprint" | "error";

	const sprintState = ref<SprintState>("loading");
	const sprint = ref<ActiveSprintResponse | null>(null);

	const sprintDateRange = computed(() => {
		if (!sprint.value) return "";
		const fmt = (d: string) =>
			new Intl.DateTimeFormat("en-US", { dateStyle: "medium" }).format(new Date(d));
		return `${fmt(sprint.value.startDate)} – ${fmt(sprint.value.endDate)}`;
	});

	onMounted(async () => {
		const token = getAuthToken();
		if (!token) return;
		try {
			sprint.value = await fetchActiveSprint(token);
			sprintState.value = "loaded";
		} catch (err: unknown) {
			const apiError = err as { status?: number };
			sprintState.value = apiError.status === 404 ? "no-sprint" : "error";
		}
	});

	function handleLogout() {
		authStore.logout();
		router.push("/auth/login");
	}
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
            @click="handleLogout"
            class="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
          >
            <LogOut class="mr-2 inline h-4 w-4" />
            Sign Out
          </button>
        </div>
      </header>

      <!-- Overview -->
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

        <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
          <FileCheck class="h-8 w-8 text-emerald-600 dark:text-emerald-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white">Deliverables</h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            View upcoming deliverable deadlines.
          </p>
        </div>

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
    </div>
  </main>
</template>
