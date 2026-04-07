<script setup lang="ts">
	import { LogOut, BookOpen, BarChart3 } from "lucide-vue-next";
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
      <div class="grid gap-4 md:grid-cols-2">
        <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
          <BookOpen class="h-8 w-8 text-blue-600 dark:text-blue-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white">Pending Reviews</h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Review and grade student deliverables.
          </p>
        </div>

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
