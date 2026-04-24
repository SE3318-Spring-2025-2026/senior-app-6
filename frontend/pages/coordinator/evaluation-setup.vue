<script setup lang="ts">
	import { z } from "zod";
	import { AlertCircle, CheckCircle2, Loader2, Pencil, Plus, Scale, Trash2 } from "lucide-vue-next";

	const { getAuthToken, fetchDeliverables, fetchRubric, updateRubric } = useApiClient();

	const gradingTypes = ["Binary", "Soft"] as const;
	interface DeliverableOption {
		id: string;
		name: string;
	}

	interface CriterionEntry {
		criterionName: string;
		weightPercentage: number;
		gradingType: "Binary" | "Soft";
	}

	const availableDeliverables = ref<DeliverableOption[]>([]);
	const deliverablesLoading = ref(true);
	const deliverablesError = ref<string | null>(null);
	const deliverableId = ref("");
	const criteria = ref<CriterionEntry[]>([
		{ criterionName: "Code Quality", weightPercentage: 30, gradingType: "Soft" },
	]);
	const submitting = ref(false);
	const formErrors = ref<Record<string, string>>({});
	const successMessage = ref("");

	// Edit mode state
	const isEditMode = ref(false);
	const rubricLoading = ref(false);

	const totalWeight = computed(() => criteria.value.reduce((sum, c) => sum + (c.weightPercentage || 0), 0));
	const isWeightValid = computed(() => totalWeight.value === 100);

	function handleAddCriterion() {
		criteria.value.push({ criterionName: "", weightPercentage: 0, gradingType: "Soft" });
	}

	function handleRemoveCriterion(index: number) {
		if (criteria.value.length > 1) {
			criteria.value.splice(index, 1);
		}
	}

	// When deliverable changes, load existing rubric
	watch(deliverableId, async (newId) => {
		formErrors.value = {};
		successMessage.value = "";
		if (!newId) {
			isEditMode.value = false;
			criteria.value = [{ criterionName: "Code Quality", weightPercentage: 30, gradingType: "Soft" }];
			return;
		}

		rubricLoading.value = true;
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required");

			const existingCriteria = await fetchRubric(newId, token);
			if (existingCriteria && existingCriteria.length > 0) {
				isEditMode.value = true;
				criteria.value = existingCriteria.map(c => ({
					criterionName: c.criterionName,
					weightPercentage: c.weight,
					gradingType: c.gradingType,
				}));
			} else {
				isEditMode.value = false;
				criteria.value = [{ criterionName: "", weightPercentage: 100, gradingType: "Soft" }];
			}
		} catch {
			// If fetch fails, default to create mode
			isEditMode.value = false;
			criteria.value = [{ criterionName: "", weightPercentage: 100, gradingType: "Soft" }];
		} finally {
			rubricLoading.value = false;
		}
	});

	const rubricSchema = z.object({
		deliverableId: z.string().min(1, "Please select a deliverable."),
		criteria: z
			.array(
				z.object({
					criterionName: z.string().trim().min(1, "Criterion name is required."),
					weightPercentage: z.number().min(1, "Weight must be at least 1.").max(100, "Weight cannot exceed 100."),
					gradingType: z.enum(gradingTypes),
				})
			)
			.min(1, "At least one criterion is required."),
	});

	async function onSubmit() {
		formErrors.value = {};

		const result = rubricSchema.safeParse({
			deliverableId: deliverableId.value,
			criteria: criteria.value,
		});

		if (!result.success) {
			const fe = result.error.flatten();
			formErrors.value = {
				deliverableId: fe.fieldErrors.deliverableId?.[0] || "",
				criteria: fe.fieldErrors.criteria?.[0] || "",
			};
			return;
		}

		submitting.value = true;
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required");

			await updateRubric(result.data.deliverableId, result.data.criteria, token);
			if (isEditMode.value) {
				successMessage.value = "Rubric updated successfully.";
			} else {
				isEditMode.value = true;
				successMessage.value = "Rubric created successfully.";
			}

			formErrors.value = {};
			setTimeout(() => (successMessage.value = ""), 3000);
		} catch (err) {
			const errorMsg = err && typeof err === "object" && "message" in err ? String(err.message) : "Failed to save rubric";
			formErrors.value = { criteria: errorMsg };
		} finally {
			submitting.value = false;
		}
	}

	onMounted(async () => {
		deliverablesLoading.value = true;
		deliverablesError.value = null;
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required. Please log in.");
			const data = await fetchDeliverables(token);
			availableDeliverables.value = data.map((d) => ({ id: d.id, name: d.name }));
		} catch (err) {
			const errorMsg = err && typeof err === "object" && "message" in err ? String(err.message) : "Failed to load deliverables";
			deliverablesError.value = errorMsg;
		} finally {
			deliverablesLoading.value = false;
		}
	});
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-3xl space-y-6">
      <!-- Page Header -->
      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
          Evaluation Setup
        </h1>
        <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
          Define grading rubrics and evaluation criteria for project deliverables. Select a deliverable to edit its existing rubric or create a new one.
        </p>
      </header>

      <!-- Main Form -->
      <form @submit.prevent="onSubmit" class="space-y-6" novalidate>
        <!-- Deliverable Selection -->
        <article class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <label class="block space-y-2">
            <span class="text-sm font-semibold text-slate-900 dark:text-white">Select Deliverable</span>
            <p class="text-xs text-slate-600 dark:text-slate-400">
              Choose which deliverable to create or edit an evaluation rubric for
            </p>
            <select
              v-model="deliverableId"
              :disabled="deliverablesLoading"
              class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400 dark:focus:ring-blue-400/20"
            >
              <option value="">{{ deliverablesLoading ? 'Loading deliverables...' : '-- Choose a deliverable --' }}</option>
              <option v-for="d in availableDeliverables" :key="d.id" :value="d.id">{{ d.name }}</option>
            </select>
            <p v-if="deliverablesError" class="text-xs text-red-600 dark:text-red-400">
              {{ deliverablesError }}
            </p>
            <p v-if="formErrors.deliverableId" class="text-xs text-red-600 dark:text-red-400">
              {{ formErrors.deliverableId }}
            </p>
            <!-- Rubric loading indicator -->
            <div v-if="rubricLoading" class="flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
              <Loader2 class="h-3.5 w-3.5 animate-spin" />
              Loading existing rubric...
            </div>
            <!-- Edit mode badge -->
            <div v-if="isEditMode && !rubricLoading" class="flex items-center gap-1.5 text-xs text-blue-600 dark:text-blue-400">
              <Pencil class="h-3 w-3" />
              Editing existing rubric
            </div>
          </label>
        </article>

        <!-- Criteria Definition -->
        <article class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <div class="mb-6 flex items-center justify-between">
            <div>
              <h2 class="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
                <Scale class="h-5 w-5" />
                Grading Criteria
              </h2>
              <p class="mt-1 text-xs text-slate-600 dark:text-slate-400">
                Define evaluation criteria and assign weights. Total weight should equal 100.
              </p>
            </div>
            <button
              type="button"
              @click="handleAddCriterion"
              class="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
            >
              <Plus class="h-4 w-4" />
              Add Criterion
            </button>
          </div>

          <!-- Weight Validation Alert -->
          <div
            v-if="criteria.length > 0"
            :class="[
              'mb-4 flex items-start gap-3 rounded-lg border p-3 text-sm',
              isWeightValid
                ? 'border-emerald-300 bg-emerald-50 text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-300'
                : 'border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-800 dark:bg-amber-950/50 dark:text-amber-300',
            ]"
          >
            <CheckCircle2 class="mt-0.5 h-4 w-4 shrink-0" />
            <div>
              <p class="font-medium">Total Weight: {{ totalWeight }}/100</p>
              <p v-if="!isWeightValid" class="mt-1 text-xs opacity-90">
                Adjust criterion weights so the total equals exactly 100
              </p>
            </div>
          </div>

          <!-- Criteria List -->
          <div class="space-y-4">
            <div
              v-for="(criterion, index) in criteria"
              :key="index"
              class="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/50"
            >
              <div class="grid gap-4 md:grid-cols-[1fr_80px_140px_40px]">
                <!-- Criterion Name -->
                <label class="block space-y-1">
                  <span class="text-xs font-medium text-slate-700 dark:text-slate-300">Criterion Name</span>
                  <input
                    v-model="criterion.criterionName"
                    type="text"
                    placeholder="e.g., Code Quality, Design"
                    class="w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:placeholder:text-slate-500 dark:focus:border-blue-400"
                  />
                </label>

                <!-- Weight -->
                <label class="block space-y-1">
                  <span class="text-xs font-medium text-slate-700 dark:text-slate-300">Weight</span>
                  <NumericInput
                    v-model.number="criterion.weightPercentage"
                    :min="0"
                    :max="100 - (totalWeight - (criterion.weightPercentage || 0))"
                    class="w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-900 outline-none transition focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400"
                  />
                </label>

                <!-- Grading Type -->
                <label class="block space-y-1">
                  <span class="text-xs font-medium text-slate-700 dark:text-slate-300">Type</span>
                  <select
                    v-model="criterion.gradingType"
                    class="w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-900 outline-none transition focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400"
                  >
                    <option v-for="type in gradingTypes" :key="type" :value="type">{{ type }}</option>
                  </select>
                </label>

                <!-- Delete Button -->
                <div class="flex items-end">
                  <button
                    type="button"
                    @click="handleRemoveCriterion(index)"
                    :disabled="criteria.length === 1"
                    class="w-full rounded-md bg-red-100 p-1 text-red-600 transition hover:bg-red-200 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-red-900/30 dark:text-red-400 dark:hover:bg-red-900/50"
                    title="Remove criterion"
                  >
                    <Trash2 class="h-4 w-4 mx-auto" />
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div
            v-if="formErrors.criteria"
            class="mt-4 flex items-start gap-2 rounded-lg border border-red-300 bg-red-50 p-3 dark:border-red-800 dark:bg-red-950/50"
          >
            <AlertCircle class="mt-0.5 h-4 w-4 shrink-0 text-red-600 dark:text-red-400" />
            <p class="text-xs text-red-700 dark:text-red-400">{{ formErrors.criteria }}</p>
          </div>
        </article>

        <!-- Submit Button -->
        <div class="space-y-3">
          <div
            v-if="successMessage"
            class="flex items-start gap-2 rounded-lg border border-emerald-300 bg-emerald-50 p-3 dark:border-emerald-800 dark:bg-emerald-950/50"
          >
            <CheckCircle2 class="mt-0.5 h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
            <p class="text-sm text-emerald-700 dark:text-emerald-400">{{ successMessage }}</p>
          </div>

          <button
            type="submit"
            :disabled="submitting || !isWeightValid || rubricLoading"
            class="w-full rounded-lg bg-blue-600 px-4 py-3 font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
          >
            {{ submitting
              ? (isEditMode ? "Updating Rubric..." : "Creating Rubric...")
              : (isEditMode ? "Update Rubric" : "Create Rubric")
            }}
          </button>
        </div>
      </form>
    </div>
  </main>
</template>