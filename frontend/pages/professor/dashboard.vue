<script setup lang="ts">
	import { LogOut, BookOpen, BarChart3, Inbox, Users, ClipboardList } from "lucide-vue-next";
	import { useAuthStore } from "~/stores/auth";

	definePageMeta({
		middleware: "auth",
		roles: ["Professor"],
	});

	const router = useRouter();
	const authStore = useAuthStore();

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
          to="/professor/sprint"
          class="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-amber-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800
        dark:hover:border-amber-600"
        >
          <ClipboardList class="h-8 w-8 text-amber-600 dark:text-amber-400" />
          <h3 class="mt-3 font-semibold text-slate-900 transition-colors group-hover:text-amber-600 dark:text-white dark:group-hover:text-amber-400">
            Sprint Panel
          </h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Track group sprint progress, AI checks, and submit Point A/B grades.
          </p>
        </NuxtLink>

        <NuxtLink
          to="/professor/pending-reviews"
          class="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-emerald-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800
        dark:hover:border-emerald-600"
        >
          <BookOpen class="h-8 w-8 text-emerald-600 dark:text-emerald-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white group-hover:text-emerald-600 dark:group-hover:text-emerald-400 transition-colors">
            Pending Reviews
          </h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            View and review student deliverable submissions assigned to your committees.
          </p>
        </NuxtLink>

        <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
          <BarChart3 class="h-8 w-8 text-emerald-600 dark:text-emerald-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white">Grade Summary</h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            View overall grading progress across teams.
          </p>
        </div>
      </div>
    </div>
  </main>
</template>
