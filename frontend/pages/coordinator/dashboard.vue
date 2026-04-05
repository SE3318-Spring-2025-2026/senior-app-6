<script setup lang="ts">
	import { LogOut, Play, FileText, ClipboardCheck, Send } from "lucide-vue-next";
	import { useAuthStore } from "~/stores/auth";

	definePageMeta({
		middleware: "auth",
		roles: ["Coordinator"],
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
              Coordinator Dashboard
            </h1>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Configure sprints, deliverables, and publish system settings.
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

      <!-- Quick Actions -->
      <div class="grid gap-4 md:grid-cols-3">
        <NuxtLink
          to="/coordinator/sprint-setup"
          class="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-blue-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-blue-600"
        >
          <Play class="h-8 w-8 text-blue-600 dark:text-blue-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white group-hover:text-blue-600 dark:group-hover:text-blue-400">
            Sprint Setup
          </h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Create sprints, set targets, and map deliverables.
          </p>
        </NuxtLink>

        <NuxtLink
          to="/coordinator/deliverables"
          class="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-purple-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-purple-600"
        >
          <FileText class="h-8 w-8 text-purple-600 dark:text-purple-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white group-hover:text-purple-600 dark:group-hover:text-purple-400">
            Deliverables
          </h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Manage project deliverables and deadlines.
          </p>
        </NuxtLink>

        <NuxtLink
          to="/coordinator/evaluation-setup"
          class="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-emerald-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-emerald-600"
        >
          <ClipboardCheck class="h-8 w-8 text-emerald-600 dark:text-emerald-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white group-hover:text-emerald-600 dark:group-hover:text-emerald-400">
            Evaluation Setup
          </h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Configure grading rubrics and weights.
          </p>
        </NuxtLink>

        <NuxtLink
          to="/coordinator/publish-config"
          class="group rounded-2xl border border-amber-200 bg-amber-50 p-6 shadow-sm transition-all hover:border-amber-400 hover:shadow-md dark:border-amber-800 dark:bg-amber-950/30 dark:hover:border-amber-500"
        >
          <Send class="h-8 w-8 text-amber-600 dark:text-amber-400" />
          <h3 class="mt-3 font-semibold text-amber-900 dark:text-amber-200 group-hover:text-amber-700 dark:group-hover:text-amber-300">
            Publish Configuration
          </h3>
          <p class="mt-1 text-sm text-amber-800 dark:text-amber-300">
            Review and finalize all settings.
          </p>
        </NuxtLink>
      </div>
    </div>
  </main>
</template>
