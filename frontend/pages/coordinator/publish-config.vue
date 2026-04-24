<script setup lang="ts">
	import {
		AlertCircle,
		CheckCircle2,
		Play,
		FileText,
		ShieldAlert,
		Loader as LoaderIcon,
	} from "lucide-vue-next";
import type { Deliverable } from "~/types/deliverable";
import type { Sprint } from "~/types/sprint";

	const { getAuthToken, fetchSprints, fetchDeliverables, publishConfig } = useApiClient();

	const sprints = ref<Sprint[]>([]);
	const deliverables = ref<Deliverable[]>([]);
	const isLoading = ref(true);
	const fetchError = ref<string | null>(null);

	const showConfirmModal = ref(false);
	const publishSubmitting = ref(false);
	const publishError = ref<string | null>(null);
	const publishSuccess = ref(false);

	function formatDate(value: string): string {
		const date = new Date(value);
		if (Number.isNaN(date.getTime())) return "Invalid date";
		return date.toLocaleDateString("en-US", {
			weekday: "short",
			year: "numeric",
			month: "short",
			day: "numeric",
		});
	}

	onMounted(async () => {
		isLoading.value = true;
		fetchError.value = null;
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required. Please log in.");
			const [sprintData, deliverableData] = await Promise.all([
				fetchSprints(token),
				fetchDeliverables(token),
			]);
			sprints.value = sprintData;
			deliverables.value = deliverableData;
		} catch (err) {
			const errorMsg =
				err && typeof err === "object" && "message" in err
					? String(err.message)
					: "Failed to load data";
			fetchError.value = errorMsg;
		} finally {
			isLoading.value = false;
		}
	});

	async function handlePublish() {
		publishError.value = null;
		publishSubmitting.value = true;
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required");
			await publishConfig(token);
			publishSuccess.value = true;
			showConfirmModal.value = false;
		} catch (err) {
			const errorMsg =
				err && typeof err === "object" && "message" in err
					? String(err.message)
					: "Failed to publish configuration";
			publishError.value = errorMsg;
			showConfirmModal.value = false;
		} finally {
			publishSubmitting.value = false;
		}
	}
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-4xl space-y-6">
      <!-- Page Header -->
      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
          Publish Configuration
        </h1>
        <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
          Review all settings before activating the system. This action is irreversible.
        </p>
      </header>

      <!-- Fetch Error -->
      <div
        v-if="fetchError"
        class="flex items-start gap-3 rounded-lg border border-red-300 bg-red-50 p-4 dark:border-red-800 dark:bg-red-950/50"
      >
        <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
        <div>
          <p class="font-medium text-red-900 dark:text-red-300">Error Loading Data</p>
          <p class="text-sm text-red-700 dark:text-red-400">{{ fetchError }}</p>
        </div>
      </div>

      <!-- Loading -->
      <div
        v-if="isLoading && !fetchError"
        class="flex items-center justify-center rounded-lg border border-slate-200 bg-white p-12 dark:border-slate-700 dark:bg-slate-800"
      >
        <div class="text-center">
          <LoaderIcon class="mx-auto h-8 w-8 animate-spin text-blue-600 dark:text-blue-400" />
          <p class="mt-3 text-sm text-slate-600 dark:text-slate-400">Loading configuration...</p>
        </div>
      </div>

      <!-- Content -->
      <div v-if="!isLoading && !fetchError" class="space-y-6">
        <!-- Sprints Summary -->
        <article class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <h2 class="mb-4 flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
            <Play class="h-5 w-5" />
            Sprints ({{ sprints.length }})
          </h2>

          <p v-if="sprints.length === 0" class="text-sm text-slate-500 dark:text-slate-400">
            No sprints configured.
          </p>

          <div v-else class="space-y-3">
            <div
              v-for="sprint in sprints"
              :key="sprint.id"
              class="rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-700 dark:bg-slate-900/50"
            >
              <p class="text-sm font-medium text-slate-900 dark:text-white">
                {{ formatDate(sprint.startDate) }} → {{ formatDate(sprint.endDate) }}
              </p>
              <p class="mt-1 text-xs text-slate-600 dark:text-slate-400">
                Story Point Target: <span class="font-semibold">{{ sprint.storyPointTarget || "Not set" }}</span>
              </p>
            </div>
          </div>
        </article>

        <!-- Deliverables Summary -->
        <article class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <h2 class="mb-4 flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
            <FileText class="h-5 w-5" />
            Deliverables ({{ deliverables.length }})
          </h2>

          <p v-if="deliverables.length === 0" class="text-sm text-slate-500 dark:text-slate-400">
            No deliverables configured.
          </p>

          <div v-else class="space-y-3">
            <div
              v-for="deliverable in deliverables"
              :key="deliverable.id"
              class="rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-700 dark:bg-slate-900/50"
            >
              <p class="text-sm font-medium text-slate-900 dark:text-white">{{ deliverable.name }}</p>
              <p class="mt-1 text-xs text-slate-600 dark:text-slate-400">
                Type: {{ deliverable.type }} · Submission: {{ formatDate(deliverable.submissionDeadline) }} · Review: {{ formatDate(deliverable.reviewDeadline) }}
              </p>
            </div>
          </div>
        </article>

        <!-- Publish Action -->
        <article class="rounded-2xl border border-amber-200 bg-amber-50 p-6 shadow-sm transition-colors dark:border-amber-800 dark:bg-amber-950/30 dark:shadow-lg">
          <div class="flex items-start gap-3">
            <ShieldAlert class="mt-0.5 h-6 w-6 shrink-0 text-amber-600 dark:text-amber-400" />
            <div class="flex-1">
              <h3 class="font-semibold text-amber-900 dark:text-amber-200">Ready to Publish?</h3>
              <p class="mt-1 text-sm text-amber-800 dark:text-amber-300">
                Publishing will lock in all sprint configurations, deliverables, and rubrics. This action cannot be undone.
              </p>
            </div>
          </div>

          <div
            v-if="publishError"
            class="mt-4 flex items-start gap-2 rounded-lg border border-red-300 bg-red-50 p-3 dark:border-red-800 dark:bg-red-950/50"
          >
            <AlertCircle class="mt-0.5 h-4 w-4 shrink-0 text-red-600 dark:text-red-400" />
            <p class="text-xs text-red-700 dark:text-red-400">{{ publishError }}</p>
          </div>

          <div
            v-if="publishSuccess"
            class="mt-4 flex items-start gap-2 rounded-lg border border-emerald-300 bg-emerald-50 p-3 dark:border-emerald-800 dark:bg-emerald-950/50"
          >
            <CheckCircle2 class="mt-0.5 h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
            <p class="text-xs text-emerald-700 dark:text-emerald-400">Configuration published successfully! The system is now active.</p>
          </div>

          <button
            v-if="!publishSuccess"
            @click="showConfirmModal = true"
            :disabled="publishSubmitting"
            class="mt-4 w-full rounded-lg bg-amber-600 px-4 py-2.5 font-medium text-white transition hover:bg-amber-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-amber-700 dark:hover:bg-amber-600"
          >
            {{ publishSubmitting ? "Publishing..." : "Publish Configuration" }}
          </button>
        </article>
      </div>
    </div>

    <!-- Confirmation Modal -->
    <Teleport to="body">
      <div
        v-if="showConfirmModal"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
        @click.self="showConfirmModal = false"
      >
        <div class="mx-4 w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-xl dark:border-slate-700 dark:bg-slate-800">
          <div class="flex items-start gap-3">
            <ShieldAlert class="mt-0.5 h-6 w-6 shrink-0 text-amber-600 dark:text-amber-400" />
            <div>
              <h3 class="text-lg font-semibold text-slate-900 dark:text-white">Confirm Publication</h3>
              <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
                Are you sure? This will lock in the rules for the system. Sprint configurations, deliverables, and rubrics can no longer be modified.
              </p>
            </div>
          </div>

          <div class="mt-6 flex gap-3">
            <button
              @click="handlePublish"
              :disabled="publishSubmitting"
              class="flex-1 rounded-lg bg-amber-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-amber-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-amber-700 dark:hover:bg-amber-600"
            >
              {{ publishSubmitting ? "Publishing..." : "Yes, Publish" }}
            </button>
            <button
              @click="showConfirmModal = false"
              :disabled="publishSubmitting"
              class="flex-1 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
            >
              Cancel
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </main>
</template>
