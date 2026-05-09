<script setup lang="ts">
	import { LogOut, BookOpen, BarChart3, Inbox, Users, ClipboardList, Loader2, UserCircle2 } from "lucide-vue-next";
	import { useAuthStore } from "~/stores/auth";
	import type { ProfessorCommittee } from "~/types/committee";

	definePageMeta({
		middleware: "auth",
		roles: ["Professor"],
	});

	const router = useRouter();
	const authStore = useAuthStore();
	const { getAuthToken, fetchProfessorCommittees } = useApiClient();

	const committees = ref<ProfessorCommittee[]>([]);
	const summaryLoading = ref(true);

	interface CommitteeStat {
		id: string;
		name: string;
		role: string;
		deliverableName: string;
		total: number;
		graded: number;
	}

	const committeeStats = computed<CommitteeStat[]>(() =>
		committees.value.map((c) => ({
			id: c.committeeId,
			name: c.committeeName,
			role: c.professorRole,
			deliverableName: c.deliverableName ?? "—",
			total: c.groups.length,
			graded: c.groups.filter((g) => g.gradedByMe).length,
		}))
	);

	function handleLogout() {
		authStore.logout();
		router.push("/auth/login");
	}

	onMounted(async () => {
		try {
			const token = getAuthToken();
			if (token) committees.value = await fetchProfessorCommittees(token);
		} finally {
			summaryLoading.value = false;
		}
	});
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-5xl space-y-6">
      <!-- Header -->
      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
              Professor Dashboard
            </h1>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Evaluate student deliverables and review submissions.
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
      <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">

        <!-- Row 1: Active work -->
        <NuxtLink
          to="/professor/pending-reviews"
          class="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-emerald-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-emerald-600"
        >
          <BookOpen class="h-8 w-8 text-emerald-600 dark:text-emerald-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white group-hover:text-emerald-600 dark:group-hover:text-emerald-400 transition-colors">
            Pending Reviews
          </h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            View and review student deliverable submissions assigned to your committees.
          </p>
        </NuxtLink>

        <NuxtLink
          to="/professor/grade-summary"
          class="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-indigo-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-indigo-600"
        >
          <BarChart3 class="h-8 w-8 text-indigo-600 dark:text-indigo-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
            Grade Summary
          </h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            View committees ready to grade, review deadlines, and submit grades.
          </p>
        </NuxtLink>

        <NuxtLink
          to="/professor/sprint"
          class="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-amber-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-amber-600"
        >
          <ClipboardList class="h-8 w-8 text-amber-600 dark:text-amber-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white transition-colors group-hover:text-amber-600 dark:group-hover:text-amber-400">
            Sprint Panel
          </h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Track group sprint progress, AI checks, and submit Point A/B grades.
          </p>
        </NuxtLink>

        <!-- Row 2: Management + info -->
        <NuxtLink
          to="/professor/committees"
          class="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-cyan-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-cyan-600"
        >
          <Users class="h-8 w-8 text-cyan-600 dark:text-cyan-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white group-hover:text-cyan-600 dark:group-hover:text-cyan-400 transition-colors">
            My Committees
          </h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            View your committees, assigned groups, and upcoming deadlines.
          </p>
        </NuxtLink>

        <NuxtLink
          to="/professor/requests"
          class="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-blue-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-blue-600"
        >
          <Inbox class="h-8 w-8 text-blue-600 dark:text-blue-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
            Advising Requests
          </h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            View and respond to incoming advising requests from student groups.
          </p>
        </NuxtLink>

        <!-- Professor info card (non-interactive) -->
        <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800 flex flex-col gap-4">
          <!-- Avatar + role -->
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-violet-100 dark:bg-violet-900/40">
              <span class="text-sm font-bold text-violet-700 dark:text-violet-300">
                {{ (authStore.userInfo?.mail ?? 'P').charAt(0).toUpperCase() }}
              </span>
            </div>
            <div class="min-w-0">
              <span class="inline-block rounded-full bg-violet-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-violet-700 dark:bg-violet-900/40 dark:text-violet-300">
                Professor
              </span>
              <p class="mt-0.5 truncate text-sm text-slate-600 dark:text-slate-300">
                {{ authStore.userInfo?.mail ?? '—' }}
              </p>
            </div>
          </div>

          <!-- Stats row -->
          <div class="grid grid-cols-2 gap-2 mt-auto">
            <div class="rounded-xl bg-slate-50 dark:bg-slate-900/40 px-3 py-2 text-center">
              <p class="text-lg font-bold text-slate-800 dark:text-slate-100">
                {{ summaryLoading ? '…' : committees.length }}
              </p>
              <p class="text-[10px] text-slate-500 dark:text-slate-400 uppercase tracking-wide">Committees</p>
            </div>
            <div class="rounded-xl bg-slate-50 dark:bg-slate-900/40 px-3 py-2 text-center">
              <p class="text-lg font-bold text-slate-800 dark:text-slate-100">
                {{ summaryLoading ? '…' : committeeStats.reduce((s, c) => s + c.graded, 0) }}
              </p>
              <p class="text-[10px] text-slate-500 dark:text-slate-400 uppercase tracking-wide">Graded</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Grade Summary -->
      <section class="rounded-2xl border border-slate-200 bg-white shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800">
        <div class="flex items-center gap-3 border-b border-slate-100 px-6 py-4 dark:border-slate-700">
          <BarChart3 class="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
          <h2 class="text-base font-semibold text-slate-900 dark:text-white">Grade Summary</h2>
        </div>

        <div v-if="summaryLoading" class="flex items-center justify-center px-6 py-10">
          <Loader2 class="h-5 w-5 animate-spin text-slate-400" />
          <span class="ml-2 text-sm text-slate-500 dark:text-slate-400">Loading…</span>
        </div>

        <div
          v-else-if="committeeStats.length === 0"
          class="px-6 py-10 text-center text-sm text-slate-500 dark:text-slate-400"
        >
          No committees assigned.
        </div>

        <div v-else class="divide-y divide-slate-100 dark:divide-slate-700">
          <div
            v-for="stat in committeeStats"
            :key="stat.id"
            class="flex items-center gap-4 px-6 py-4"
          >
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <span class="truncate text-sm font-medium text-slate-900 dark:text-white">
                  {{ stat.name }}
                </span>
                <span
                  :class="[
                    'shrink-0 rounded-full px-2 py-0.5 text-xs font-medium',
                    stat.role === 'ADVISOR'
                      ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300'
                      : 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300',
                  ]"
                >
                  {{ stat.role }}
                </span>
              </div>
              <p class="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                {{ stat.deliverableName }}
              </p>
            </div>

            <div class="flex shrink-0 flex-col items-end gap-1">
              <span class="text-sm font-semibold text-slate-900 dark:text-white">
                <span class="text-emerald-600 dark:text-emerald-400">{{ stat.graded }}</span>
                <span class="text-slate-400">/{{ stat.total }}</span>
                <span class="ml-1 text-xs font-normal text-slate-500 dark:text-slate-400">graded</span>
              </span>
              <div class="h-1.5 w-24 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-600">
                <div
                  class="h-full rounded-full bg-emerald-500 transition-all"
                  :style="{ width: stat.total > 0 ? `${(stat.graded / stat.total) * 100}%` : '0%' }"
                />
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </main>
</template>
