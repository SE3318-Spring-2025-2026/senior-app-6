<script setup lang="ts">
	import { z } from "zod";
	import {
		AlertCircle,
		CheckCircle2,
		Calendar,
		Loader as LoaderIcon,
		Play,
		TrendingUp,
		Link2,
	} from "lucide-vue-next";
	import type { Sprint, Deliverable } from "~/composables/useApiClient";

	const {
		createSprint,
		updateSprintTarget,
		getAuthToken,
		fetchSprints,
		fetchDeliverables,
		createSprintDeliverableMapping,
	} = useApiClient();

	const sprintCreateSchema = z
		.object({
			startDate: z.string().min(1, "Start date is required."),
			endDate: z.string().min(1, "End date is required."),
		})
		.refine((v) => new Date(v.endDate) > new Date(v.startDate), {
			path: ["endDate"],
			message: "End date must be after start date.",
		});

	const sprintTargetSchema = z.object({
		storyPointTarget: z.number().min(1, "Story point target must be at least 1."),
	});

	// State
	const sprints = ref<Sprint[]>([]);
	const deliverables = ref<Deliverable[]>([]);
	const isLoading = ref(true);
	const fetchError = ref<string | null>(null);

	// Create form
	const createStartDate = ref("");
	const createEndDate = ref("");
	const createSubmitting = ref(false);
	const createError = ref<string | null>(null);
	const createMessage = ref("");
	const createErrors = ref<Record<string, string>>({});

	// End date min computed — prevents selecting before start date
	const endDateMin = computed(() => {
		if (!createStartDate.value) return undefined;
		const start = new Date(createStartDate.value);
		start.setDate(start.getDate() + 1);
		return start.toISOString().split("T")[0];
	});

	// Target edit
	const editingTargetId = ref<string | null>(null);
	const targetStoryPoint = ref(0);
	const targetSubmitting = ref(false);
	const targetError = ref<string | null>(null);
	const targetMessage = ref("");
	const targetErrors = ref<Record<string, string>>({});

	// Mapping
	const mappingSprintId = ref<string | null>(null);
	const mappingDeliverableId = ref("");
	const mappingContribution = ref<number>(0);
	const mappingSubmitting = ref(false);
	const mappingError = ref<string | null>(null);
	const mappingMessage = ref("");

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
			const errorMsg = err && typeof err === "object" && "message" in err ? String(err.message) : "Failed to load data";
			fetchError.value = errorMsg;
		} finally {
			isLoading.value = false;
		}
	});

	// When start date changes, clear end date if it's now before start
	watch(createStartDate, (newStart) => {
		if (createEndDate.value && newStart && new Date(createEndDate.value) <= new Date(newStart)) {
			createEndDate.value = "";
		}
	});

	async function handleCreateSubmit() {
		createMessage.value = "";
		createError.value = null;
		createErrors.value = {};

		const result = sprintCreateSchema.safeParse({
			startDate: createStartDate.value,
			endDate: createEndDate.value,
		});

		if (!result.success) {
			const fe = result.error.flatten().fieldErrors;
			createErrors.value = {
				startDate: fe.startDate?.[0] || "",
				endDate: fe.endDate?.[0] || "",
			};
			return;
		}

		createSubmitting.value = true;
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required");

			const created = await createSprint(result.data, token);
			sprints.value.unshift(created);

			createStartDate.value = "";
			createEndDate.value = "";
			createMessage.value = "✓ Sprint created successfully";
			setTimeout(() => (createMessage.value = ""), 3000);
		} catch (err) {
			const errorMsg = err && typeof err === "object" && "message" in err ? String(err.message) : "Failed to create sprint";
			createError.value = errorMsg;
		} finally {
			createSubmitting.value = false;
		}
	}

	function handleOpenTargetEdit(sprint: Sprint) {
		editingTargetId.value = sprint.id;
		targetError.value = null;
		targetErrors.value = {};
		targetStoryPoint.value = sprint.storyPointTarget || 0;
	}

	function handleCancelEdit() {
		editingTargetId.value = null;
		targetError.value = null;
	}

	async function handleTargetSubmit() {
		if (!editingTargetId.value) return;
		targetMessage.value = "";
		targetError.value = null;
		targetErrors.value = {};

		const result = sprintTargetSchema.safeParse({ storyPointTarget: targetStoryPoint.value });

		if (!result.success) {
			const fe = result.error.flatten().fieldErrors;
			targetErrors.value = { storyPointTarget: fe.storyPointTarget?.[0] || "" };
			return;
		}

		targetSubmitting.value = true;
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required");

			await updateSprintTarget(editingTargetId.value, result.data.storyPointTarget, token);

			sprints.value = sprints.value.map((s) =>
				s.id === editingTargetId.value ? { ...s, storyPointTarget: result.data.storyPointTarget } : s
			);

			targetMessage.value = "✓ Sprint target updated successfully";
			editingTargetId.value = null;
			setTimeout(() => (targetMessage.value = ""), 3000);
		} catch (err) {
			const errorMsg = err && typeof err === "object" && "message" in err ? String(err.message) : "Failed to update sprint target";
			targetError.value = errorMsg;
		} finally {
			targetSubmitting.value = false;
		}
	}

	function handleOpenMapping(sprintId: string) {
		mappingSprintId.value = sprintId;
		mappingDeliverableId.value = "";
		mappingContribution.value = 0;
		mappingError.value = null;
		mappingMessage.value = "";
	}

	function handleCancelMapping() {
		mappingSprintId.value = null;
		mappingError.value = null;
	}

	async function handleMappingSubmit() {
		if (!mappingSprintId.value) return;
		mappingError.value = null;
		mappingMessage.value = "";

		if (!mappingDeliverableId.value) {
			mappingError.value = "Please select a deliverable.";
			return;
		}
		if (mappingContribution.value <= 0 || mappingContribution.value > 100) {
			mappingError.value = "Contribution percentage must be between 1 and 100.";
			return;
		}

		mappingSubmitting.value = true;
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required");

			await createSprintDeliverableMapping(
				mappingSprintId.value,
				mappingDeliverableId.value,
				mappingContribution.value,
				token
			);
			mappingMessage.value = "✓ Mapping created successfully";
			mappingDeliverableId.value = "";
			mappingContribution.value = 0;
			setTimeout(() => {
				mappingMessage.value = "";
				mappingSprintId.value = null;
			}, 2000);
		} catch (err) {
			const errorMsg = err && typeof err === "object" && "message" in err ? String(err.message) : "Failed to create mapping";
			mappingError.value = errorMsg;
		} finally {
			mappingSubmitting.value = false;
		}
	}
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-4xl space-y-6">
      <!-- Page Header -->
      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
          Sprint Setup
        </h1>
        <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
          Create and manage project sprints with story point targets.
        </p>
      </header>

      <!-- Fetch Error Alert -->
      <div
        v-if="fetchError"
        class="flex items-start gap-3 rounded-lg border border-red-300 bg-red-50 p-4 dark:border-red-800 dark:bg-red-950/50"
      >
        <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
        <div>
          <p class="font-medium text-red-900 dark:text-red-300">Error Loading Sprints</p>
          <p class="text-sm text-red-700 dark:text-red-400">{{ fetchError }}</p>
        </div>
      </div>

      <!-- Loading State -->
      <div
        v-if="isLoading && !fetchError"
        class="flex items-center justify-center rounded-lg border border-slate-200 bg-white p-12 dark:border-slate-700 dark:bg-slate-800"
      >
        <div class="text-center">
          <LoaderIcon class="mx-auto h-8 w-8 animate-spin text-blue-600 dark:text-blue-400" />
          <p class="mt-3 text-sm text-slate-600 dark:text-slate-400">Loading sprints...</p>
        </div>
      </div>

      <!-- Main Content -->
      <div v-if="!isLoading && !fetchError" class="space-y-6">
        <!-- Create Sprint Form -->
        <article class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <h2 class="mb-4 flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
            <Calendar class="h-5 w-5" />
            Create New Sprint
          </h2>

          <form @submit.prevent="handleCreateSubmit" class="space-y-4" novalidate>
            <div class="grid gap-4 md:grid-cols-2">
              <label class="block space-y-1.5">
                <span class="text-sm font-medium text-slate-700 dark:text-slate-300">Start Date</span>
                <input
                  v-model="createStartDate"
                  type="date"
                  class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400"
                />
                <p v-if="createErrors.startDate" class="text-xs text-red-600 dark:text-red-400">{{ createErrors.startDate }}</p>
              </label>

              <label class="block space-y-1.5">
                <span class="text-sm font-medium text-slate-700 dark:text-slate-300">End Date</span>
                <input
                  v-model="createEndDate"
                  type="date"
                  :min="endDateMin"
                  class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400"
                />
                <p v-if="createErrors.endDate" class="text-xs text-red-600 dark:text-red-400">{{ createErrors.endDate }}</p>
              </label>
            </div>

            <div
              v-if="createError"
              class="flex items-start gap-2 rounded-lg border border-red-300 bg-red-50 p-3 dark:border-red-800 dark:bg-red-950/50"
            >
              <AlertCircle class="mt-0.5 h-4 w-4 shrink-0 text-red-600 dark:text-red-400" />
              <p class="text-xs text-red-700 dark:text-red-400">{{ createError }}</p>
            </div>

            <div
              v-if="createMessage"
              class="flex items-start gap-2 rounded-lg border border-emerald-300 bg-emerald-50 p-3 dark:border-emerald-800 dark:bg-emerald-950/50"
            >
              <CheckCircle2 class="mt-0.5 h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
              <p class="text-xs text-emerald-700 dark:text-emerald-400">{{ createMessage }}</p>
            </div>

            <button
              type="submit"
              :disabled="createSubmitting"
              class="w-full rounded-lg bg-blue-600 px-4 py-2 font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
            >
              {{ createSubmitting ? "Creating..." : "Create Sprint" }}
            </button>
          </form>
        </article>

        <!-- Sprints List -->
        <article class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <h2 class="mb-4 flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
            <Play class="h-5 w-5" />
            Active Sprints ({{ sprints.length }})
          </h2>

          <p v-if="sprints.length === 0" class="text-sm text-slate-600 dark:text-slate-400">
            No sprints yet. Create one using the form above.
          </p>

          <div v-else class="space-y-4">
            <div
              v-for="sprint in sprints"
              :key="sprint.id"
              class="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/50"
            >
              <div class="mb-4 flex items-start justify-between gap-4">
                <div class="flex-1">
                  <h3 class="font-medium text-slate-900 dark:text-white">
                    Sprint: {{ formatDate(sprint.startDate) }} → {{ formatDate(sprint.endDate) }}
                  </h3>
                  <p class="mt-1 text-xs text-slate-600 dark:text-slate-400">
                    ID: <span class="font-mono">{{ sprint.id }}</span>
                  </p>
                </div>
              </div>

              <!-- Story Point Target - Edit Mode -->
              <form
                v-if="editingTargetId === sprint.id"
                @submit.prevent="handleTargetSubmit"
                class="space-y-3"
                novalidate
              >
                <label class="block space-y-1">
                  <span class="text-xs font-medium text-slate-700 dark:text-slate-300">Story Point Target</span>
                  <input
                    v-model.number="targetStoryPoint"
                    type="number"
                    min="1"
                    class="w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-900 outline-none transition focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400"
                  />
                  <p v-if="targetErrors.storyPointTarget" class="text-xs text-red-600 dark:text-red-400">
                    {{ targetErrors.storyPointTarget }}
                  </p>
                </label>

                <div
                  v-if="targetError"
                  class="flex items-start gap-2 rounded-md border border-red-300 bg-red-100 p-2 dark:border-red-800 dark:bg-red-950/50"
                >
                  <AlertCircle class="mt-0.5 h-3 w-3 shrink-0 text-red-600 dark:text-red-400" />
                  <p class="text-xs text-red-700 dark:text-red-400">{{ targetError }}</p>
                </div>

                <div class="flex gap-2">
                  <button
                    type="submit"
                    :disabled="targetSubmitting"
                    class="flex-1 rounded-md bg-blue-600 px-2 py-1 text-xs font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
                  >
                    {{ targetSubmitting ? "Saving..." : "Save Target" }}
                  </button>
                  <button
                    type="button"
                    @click="handleCancelEdit"
                    :disabled="targetSubmitting"
                    class="flex-1 rounded-md border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
                  >
                    Cancel
                  </button>
                </div>

                <div
                  v-if="targetMessage"
                  class="flex items-start gap-2 rounded-md border border-emerald-300 bg-emerald-100 p-2 dark:border-emerald-800 dark:bg-emerald-950/50"
                >
                  <CheckCircle2 class="mt-0.5 h-3 w-3 shrink-0 text-emerald-600 dark:text-emerald-400" />
                  <p class="text-xs text-emerald-700 dark:text-emerald-400">{{ targetMessage }}</p>
                </div>
              </form>

              <!-- Story Point Target - Display Mode -->
              <div v-else class="flex items-center justify-between gap-4">
                <div class="flex items-center gap-2">
                  <TrendingUp class="h-4 w-4 text-blue-600 dark:text-blue-400" />
                  <div>
                    <p class="text-xs font-medium text-slate-700 dark:text-slate-300">Story Point Target</p>
                    <p class="text-sm font-semibold text-slate-900 dark:text-white">
                      {{ sprint.storyPointTarget || "Not set" }}
                    </p>
                  </div>
                </div>
                <button
                  @click="handleOpenTargetEdit(sprint)"
                  class="rounded-md bg-blue-100 px-3 py-1 text-xs font-medium text-blue-600 transition hover:bg-blue-200 dark:bg-blue-900/30 dark:text-blue-400 dark:hover:bg-blue-900/50"
                >
                  Edit
                </button>
              </div>

              <!-- Deliverable Mapping Section -->
              <div class="mt-4 border-t border-slate-200 pt-4 dark:border-slate-700">
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <Link2 class="h-4 w-4 text-purple-600 dark:text-purple-400" />
                    <p class="text-xs font-medium text-slate-700 dark:text-slate-300">Deliverable Mapping</p>
                  </div>
                  <button
                    v-if="mappingSprintId !== sprint.id"
                    @click="handleOpenMapping(sprint.id)"
                    class="rounded-md bg-purple-100 px-3 py-1 text-xs font-medium text-purple-600 transition hover:bg-purple-200 dark:bg-purple-900/30 dark:text-purple-400 dark:hover:bg-purple-900/50"
                  >
                    Map Deliverable
                  </button>
                </div>

                <!-- Mapping Form -->
                <form
                  v-if="mappingSprintId === sprint.id"
                  @submit.prevent="handleMappingSubmit"
                  class="mt-3 space-y-3"
                  novalidate
                >
                  <label class="block space-y-1">
                    <span class="text-xs font-medium text-slate-700 dark:text-slate-300">Deliverable</span>
                    <select
                      v-model="mappingDeliverableId"
                      class="w-full rounded-md border border-slate-300 bg-white px-2 py-1.5 text-xs text-slate-900 outline-none transition focus:border-purple-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-purple-400"
                    >
                      <option value="" disabled>Select a deliverable</option>
                      <option v-for="d in deliverables" :key="d.id" :value="d.id">{{ d.name }}</option>
                    </select>
                  </label>

                  <label class="block space-y-1">
                    <span class="text-xs font-medium text-slate-700 dark:text-slate-300">Contribution Percentage (%)</span>
                    <input
                      v-model.number="mappingContribution"
                      type="number"
                      min="1"
                      max="100"
                      placeholder="e.g. 30"
                      class="w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-900 outline-none transition focus:border-purple-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-purple-400"
                    />
                  </label>

                  <div
                    v-if="mappingError"
                    class="flex items-start gap-2 rounded-md border border-red-300 bg-red-100 p-2 dark:border-red-800 dark:bg-red-950/50"
                  >
                    <AlertCircle class="mt-0.5 h-3 w-3 shrink-0 text-red-600 dark:text-red-400" />
                    <p class="text-xs text-red-700 dark:text-red-400">{{ mappingError }}</p>
                  </div>

                  <div
                    v-if="mappingMessage"
                    class="flex items-start gap-2 rounded-md border border-emerald-300 bg-emerald-100 p-2 dark:border-emerald-800 dark:bg-emerald-950/50"
                  >
                    <CheckCircle2 class="mt-0.5 h-3 w-3 shrink-0 text-emerald-600 dark:text-emerald-400" />
                    <p class="text-xs text-emerald-700 dark:text-emerald-400">{{ mappingMessage }}</p>
                  </div>

                  <div class="flex gap-2">
                    <button
                      type="submit"
                      :disabled="mappingSubmitting"
                      class="flex-1 rounded-md bg-purple-600 px-2 py-1 text-xs font-medium text-white transition hover:bg-purple-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-purple-700 dark:hover:bg-purple-600"
                    >
                      {{ mappingSubmitting ? "Saving..." : "Save Mapping" }}
                    </button>
                    <button
                      type="button"
                      @click="handleCancelMapping"
                      :disabled="mappingSubmitting"
                      class="flex-1 rounded-md border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
                    >
                      Cancel
                    </button>
                  </div>
                </form>
              </div>
            </div>
          </div>
        </article>
      </div>
    </div>
  </main>
</template>
