<script setup lang="ts">
	import { AlertTriangle, LogOut, Play, FileText, ClipboardCheck, Send, ShieldX, X } from "lucide-vue-next";
	import { useAuthStore } from "~/stores/auth";
	import type { SanitizationReport } from "~/composables/useApiClient";

	definePageMeta({
		middleware: "auth",
		roles: ["Coordinator"],
	});

	const router = useRouter();
	const authStore = useAuthStore();
	const { getAuthToken, runCoordinatorSanitization } = useApiClient();

	const sanitizationLoading = ref(false);
	const sanitizationError = ref("");
	const sanitizationReport = ref<SanitizationReport | null>(null);
	const showReportModal = ref(false);
	const showForceWarning = ref(false);

	const autoRejectedCount = computed(() =>
		sanitizationReport.value?.autoRejectedRequestCount ??
		sanitizationReport.value?.rejectedRequestCount ??
		0
	);

	const triggeredAtLabel = computed(() => {
		if (!sanitizationReport.value?.triggeredAt) {
			return "Unavailable";
		}

		return new Intl.DateTimeFormat("en-US", {
			dateStyle: "medium",
			timeStyle: "short",
		}).format(new Date(sanitizationReport.value.triggeredAt));
	});

	function handleLogout() {
		authStore.logout();
		router.push("/auth/login");
	}

	function closeReportModal() {
		showReportModal.value = false;
	}

	function isActiveWindowError(message = "") {
		const normalized = message.toLowerCase();
		return normalized.includes("window") && normalized.includes("active");
	}

	async function submitSanitization(force = false) {
		sanitizationLoading.value = true;
		sanitizationError.value = "";

		try {
			const token = getAuthToken();
			if (!token) {
				throw new Error("Authentication required. Please log in again.");
			}

			const report = await runCoordinatorSanitization(force, token);
			sanitizationReport.value = report;
			showForceWarning.value = false;
			showReportModal.value = true;
		} catch (error: unknown) {
			const apiError = error as { message?: string };
			const message = apiError.message || "Sanitization could not be started.";

			if (!force && isActiveWindowError(message)) {
				showForceWarning.value = true;
				return;
			}

			sanitizationError.value = message;
		} finally {
			sanitizationLoading.value = false;
		}
	}

	function handleRunSanitization() {
		submitSanitization(false);
	}

	function confirmForcedSanitization() {
		submitSanitization(true);
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

        <button
          type="button"
          class="group rounded-2xl border border-red-200 bg-red-50 p-6 text-left shadow-sm transition-all hover:border-red-400 hover:shadow-md disabled:cursor-not-allowed disabled:opacity-70 dark:border-red-900 dark:bg-red-950/30 dark:hover:border-red-500"
          :disabled="sanitizationLoading"
          @click="handleRunSanitization"
        >
          <ShieldX class="h-8 w-8 text-red-600 dark:text-red-400" />
          <h3 class="mt-3 font-semibold text-red-950 group-hover:text-red-700 dark:text-red-100 dark:group-hover:text-red-300">
            Run Sanitization
          </h3>
          <p class="mt-1 text-sm text-red-800 dark:text-red-300">
            Disband unadvised groups and auto-reject pending advisor requests.
          </p>
        </button>
      </div>

      <section
        v-if="sanitizationError"
        class="rounded-2xl border border-red-300 bg-red-50 p-4 text-sm text-red-800 shadow-sm dark:border-red-800 dark:bg-red-950/40 dark:text-red-200"
      >
        {{ sanitizationError }}
      </section>
    </div>

    <div
      v-if="showForceWarning"
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="force-sanitization-title"
    >
      <section class="w-full max-w-lg rounded-lg border border-red-300 bg-white p-6 shadow-xl dark:border-red-800 dark:bg-slate-900">
        <div class="flex items-start gap-3">
          <AlertTriangle class="mt-1 h-6 w-6 shrink-0 text-red-600 dark:text-red-400" />
          <div>
            <h2 id="force-sanitization-title" class="text-xl font-semibold text-red-950 dark:text-red-100">
              Advisor window is still active
            </h2>
            <p class="mt-3 text-sm leading-6 text-red-900 dark:text-red-200">
              Forced sanitization will disband every group without an advisor right now and auto-reject their pending advisor requests. This bypasses the active window.
            </p>
          </div>
        </div>

        <div class="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <button
            type="button"
            class="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700"
            :disabled="sanitizationLoading"
            @click="showForceWarning = false"
          >
            Cancel
          </button>
          <button
            type="button"
            class="rounded-lg bg-red-700 px-4 py-2 text-sm font-semibold text-white transition hover:bg-red-800 disabled:cursor-not-allowed disabled:opacity-70"
            :disabled="sanitizationLoading"
            @click="confirmForcedSanitization"
          >
            Force Sanitization
          </button>
        </div>
      </section>
    </div>

    <div
      v-if="showReportModal && sanitizationReport"
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="sanitization-report-title"
    >
      <section class="w-full max-w-lg rounded-lg border border-slate-200 bg-white p-6 shadow-xl dark:border-slate-700 dark:bg-slate-900">
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
              Sanitization Report
            </p>
            <h2 id="sanitization-report-title" class="mt-2 text-2xl font-semibold text-slate-950 dark:text-white">
              Run complete
            </h2>
          </div>
          <button
            type="button"
            class="rounded-lg p-2 text-slate-500 transition hover:bg-slate-100 hover:text-slate-800 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-100"
            aria-label="Close report"
            @click="closeReportModal"
          >
            <X class="h-5 w-5" />
          </button>
        </div>

        <dl class="mt-6 grid gap-4 sm:grid-cols-2">
          <div class="rounded-lg border border-slate-200 p-4 dark:border-slate-700">
            <dt class="text-sm text-slate-600 dark:text-slate-400">
              Groups disbanded
            </dt>
            <dd class="mt-2 text-3xl font-semibold text-slate-950 dark:text-white">
              {{ sanitizationReport.disbandedCount }}
            </dd>
          </div>
          <div class="rounded-lg border border-slate-200 p-4 dark:border-slate-700">
            <dt class="text-sm text-slate-600 dark:text-slate-400">
              Requests auto-rejected
            </dt>
            <dd class="mt-2 text-3xl font-semibold text-slate-950 dark:text-white">
              {{ autoRejectedCount }}
            </dd>
          </div>
          <div class="rounded-lg border border-slate-200 p-4 dark:border-slate-700 sm:col-span-2">
            <dt class="text-sm text-slate-600 dark:text-slate-400">
              Triggered at
            </dt>
            <dd class="mt-2 text-sm font-medium text-slate-950 dark:text-white">
              {{ triggeredAtLabel }}
            </dd>
          </div>
        </dl>
      </section>
    </div>
  </main>
</template>
