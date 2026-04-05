<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-7xl space-y-6">
      <!-- Page Header -->
      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
          Deliverables Management
        </h1>
        <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
          Create and manage project deliverables, deadlines, and review windows.
        </p>
      </header>

      <!-- Fetch Error Alert -->
      <div
        v-if="fetchError"
        class="flex items-start gap-3 rounded-lg border border-red-300 bg-red-50 p-4 dark:border-red-800 dark:bg-red-950/50"
      >
        <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
        <div>
          <p class="font-medium text-red-900 dark:text-red-300">Error Loading Deliverables</p>
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
          <p class="mt-3 text-sm text-slate-600 dark:text-slate-400">Loading deliverables...</p>
        </div>
      </div>

      <!-- Main Content -->
      <section v-if="!isLoading && !fetchError" class="grid gap-6 lg:grid-cols-[minmax(320px,380px)_1fr]">
        <!-- Create Deliverable Form -->
        <article class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <h2 class="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
            <Plus class="h-5 w-5 text-slate-700 dark:text-slate-300" />
            New Deliverable
          </h2>

          <form @submit.prevent="handleCreateSubmit" class="mt-4 space-y-4" novalidate>
            <label class="block space-y-1.5">
              <span class="text-sm font-medium text-slate-700 dark:text-slate-300">Name</span>
              <input
                v-model="createName"
                type="text"
                placeholder="Enter deliverable name"
                class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-slate-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:placeholder:text-slate-500 dark:focus:border-slate-400"
              />
              <p v-if="createErrors.name" class="text-xs text-red-600 dark:text-red-400">{{ createErrors.name }}</p>
            </label>

            <label class="block space-y-1.5">
              <span class="text-sm font-medium text-slate-700 dark:text-slate-300">Type</span>
              <select
                v-model="createType"
                class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-slate-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-slate-400"
              >
                <option v-for="type in deliverableTypes" :key="type" :value="type">{{ type }}</option>
              </select>
              <p v-if="createErrors.type" class="text-xs text-red-600 dark:text-red-400">{{ createErrors.type }}</p>
            </label>

            <label class="block space-y-1.5">
              <span class="text-sm font-medium text-slate-700 dark:text-slate-300">Submission Deadline</span>
              <input
                v-model="createSubmissionDeadline"
                type="datetime-local"
                class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-slate-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-slate-400"
              />
              <p v-if="createErrors.submissionDeadline" class="text-xs text-red-600 dark:text-red-400">{{ createErrors.submissionDeadline }}</p>
            </label>

            <label class="block space-y-1.5">
              <span class="text-sm font-medium text-slate-700 dark:text-slate-300">Review Deadline</span>
              <input
                v-model="createReviewDeadline"
                type="datetime-local"
                class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-slate-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-slate-400"
              />
              <p v-if="createErrors.reviewDeadline" class="text-xs text-red-600 dark:text-red-400">{{ createErrors.reviewDeadline }}</p>
            </label>

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
              class="w-full rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
            >
              {{ createSubmitting ? "Creating..." : "Create Deliverable" }}
            </button>
          </form>
        </article>

        <!-- Deliverables List -->
        <article class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <h2 class="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
            <CalendarDays class="h-5 w-5 text-slate-700 dark:text-slate-300" />
            Deliverables ({{ deliverables.length }})
          </h2>

          <p v-if="deliverables.length === 0" class="mt-4 text-sm text-slate-600 dark:text-slate-400">
            No deliverables yet. Create one using the form on the left.
          </p>

          <div v-else class="mt-4 space-y-3">
            <template v-for="deliverable in deliverables" :key="deliverable.id">
              <!-- Edit Mode -->
              <div
                v-if="activeEditId === deliverable.id"
                class="rounded-lg border border-blue-300 bg-blue-50 p-4 dark:border-blue-800 dark:bg-blue-950/30"
              >
                <h3 class="mb-3 font-medium text-slate-900 dark:text-white">Edit: {{ deliverable.name }}</h3>

                <form @submit.prevent="handleEditSubmit" class="space-y-3" novalidate>
                  <label class="block space-y-1">
                    <span class="text-xs font-medium text-slate-700 dark:text-slate-300">Submission Deadline</span>
                    <input
                      v-model="editSubmissionDeadline"
                      type="datetime-local"
                      class="w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-900 outline-none transition focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400"
                    />
                    <p v-if="editErrors.submissionDeadline" class="text-xs text-red-600 dark:text-red-400">{{ editErrors.submissionDeadline }}</p>
                  </label>

                  <label class="block space-y-1">
                    <span class="text-xs font-medium text-slate-700 dark:text-slate-300">Review Deadline</span>
                    <input
                      v-model="editReviewDeadline"
                      type="datetime-local"
                      class="w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-900 outline-none transition focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400"
                    />
                    <p v-if="editErrors.reviewDeadline" class="text-xs text-red-600 dark:text-red-400">{{ editErrors.reviewDeadline }}</p>
                  </label>

                  <div
                    v-if="editError"
                    class="flex items-start gap-2 rounded-md border border-red-300 bg-red-100 p-2 dark:border-red-800 dark:bg-red-950/50"
                  >
                    <AlertCircle class="mt-0.5 h-3 w-3 shrink-0 text-red-600 dark:text-red-400" />
                    <p class="text-xs text-red-700 dark:text-red-400">{{ editError }}</p>
                  </div>

                  <div class="flex gap-2">
                    <button
                      type="submit"
                      :disabled="editSubmitting"
                      class="flex-1 rounded-md bg-blue-600 px-2 py-1 text-xs font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
                    >
                      {{ editSubmitting ? "Saving..." : "Save Changes" }}
                    </button>
                    <button
                      type="button"
                      @click="handleCancelEdit"
                      :disabled="editSubmitting"
                      class="flex-1 rounded-md border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
                    >
                      Cancel
                    </button>
                  </div>
                </form>

                <div
                  v-if="editMessage"
                  class="mt-3 flex items-start gap-2 rounded-md border border-emerald-300 bg-emerald-100 p-2 dark:border-emerald-800 dark:bg-emerald-950/50"
                >
                  <CheckCircle2 class="mt-0.5 h-3 w-3 shrink-0 text-emerald-600 dark:text-emerald-400" />
                  <p class="text-xs text-emerald-700 dark:text-emerald-400">{{ editMessage }}</p>
                </div>
              </div>

              <!-- Display Mode -->
              <div
                v-else
                class="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/50"
              >
                <div class="flex items-start justify-between gap-3">
                  <div class="flex-1">
                    <h3 class="font-medium text-slate-900 dark:text-white">{{ deliverable.name }}</h3>
                    <p class="mt-1 text-xs text-slate-600 dark:text-slate-400">
                      Type: <span class="font-medium">{{ deliverable.type }}</span>
                    </p>
                    <div class="mt-3 space-y-1 text-xs text-slate-600 dark:text-slate-400">
                      <p>
                        📤 Submission:
                        <span class="font-mono">{{ formatDeadline(deliverable.submissionDeadline) }}</span>
                      </p>
                      <p>
                        ✅ Review:
                        <span class="font-mono">{{ formatDeadline(deliverable.reviewDeadline) }}</span>
                      </p>
                    </div>
                  </div>
                  <button
                    @click="handleOpenEdit(deliverable)"
                    class="rounded-md bg-blue-100 p-2 text-blue-600 transition hover:bg-blue-200 dark:bg-blue-900/30 dark:text-blue-400 dark:hover:bg-blue-900/50"
                    title="Edit deliverable"
                  >
                    <Edit class="h-4 w-4" />
                  </button>
                </div>
              </div>
            </template>
          </div>
        </article>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { z } from "zod";
import { CalendarDays, CheckCircle2, Edit, Plus, AlertCircle, Loader as LoaderIcon } from "lucide-vue-next";
import type { Deliverable } from "~/composables/useApiClient";

const { createDeliverable, updateDeliverable, getAuthToken, fetchDeliverables } = useApiClient();

const deliverableTypes = ["Proposal", "SoW", "Demonstration"] as const;

const deliverableCreateSchema = z
  .object({
    name: z.string().trim().min(2, "Deliverable name must be at least 2 characters."),
    type: z.enum(deliverableTypes, { error: "Please select a valid deliverable type." }),
    submissionDeadline: z.string().min(1, "Submission deadline is required."),
    reviewDeadline: z.string().min(1, "Review deadline is required."),
  })
  .refine(
    (v) => new Date(v.reviewDeadline).getTime() > new Date(v.submissionDeadline).getTime(),
    { path: ["reviewDeadline"], message: "Review deadline must be after submission deadline." }
  );

const deliverableEditSchema = z
  .object({
    submissionDeadline: z.string().min(1, "Submission deadline is required."),
    reviewDeadline: z.string().min(1, "Review deadline is required."),
  })
  .refine(
    (v) => new Date(v.reviewDeadline).getTime() > new Date(v.submissionDeadline).getTime(),
    { path: ["reviewDeadline"], message: "Review deadline must be after submission deadline." }
  );

// State
const deliverables = ref<Deliverable[]>([]);
const isLoading = ref(true);
const fetchError = ref<string | null>(null);
const createError = ref<string | null>(null);
const editError = ref<string | null>(null);
const createMessage = ref("");
const editMessage = ref("");
const activeEditId = ref<string | null>(null);

// Create form
const createName = ref("");
const createType = ref<(typeof deliverableTypes)[number]>("Proposal");
const createSubmissionDeadline = ref("");
const createReviewDeadline = ref("");
const createSubmitting = ref(false);
const createErrors = ref<Record<string, string>>({});

// Edit form
const editSubmissionDeadline = ref("");
const editReviewDeadline = ref("");
const editSubmitting = ref(false);
const editErrors = ref<Record<string, string>>({});

function formatDeadline(value: string): string {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "Invalid date";
  return parsed.toLocaleString("en-US", { dateStyle: "medium", timeStyle: "short" });
}

onMounted(async () => {
  isLoading.value = true;
  fetchError.value = null;
  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required. Please log in.");
    deliverables.value = await fetchDeliverables(token);
  } catch (err) {
    const errorMsg = err && typeof err === "object" && "message" in err ? String(err.message) : "Failed to load deliverables";
    fetchError.value = errorMsg;
  } finally {
    isLoading.value = false;
  }
});

async function handleCreateSubmit() {
  createMessage.value = "";
  createError.value = null;
  createErrors.value = {};

  const result = deliverableCreateSchema.safeParse({
    name: createName.value,
    type: createType.value,
    submissionDeadline: createSubmissionDeadline.value,
    reviewDeadline: createReviewDeadline.value,
  });

  if (!result.success) {
    const fe = result.error.flatten().fieldErrors;
    createErrors.value = {
      name: fe.name?.[0] || "",
      type: fe.type?.[0] || "",
      submissionDeadline: fe.submissionDeadline?.[0] || "",
      reviewDeadline: fe.reviewDeadline?.[0] || "",
    };
    return;
  }

  createSubmitting.value = true;
  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required");

    const created = await createDeliverable(result.data, token);
    deliverables.value.unshift(created);

    createName.value = "";
    createType.value = "Proposal";
    createSubmissionDeadline.value = "";
    createReviewDeadline.value = "";
    createMessage.value = "✓ Deliverable created successfully";
    setTimeout(() => (createMessage.value = ""), 3000);
  } catch (err) {
    const errorMsg = err && typeof err === "object" && "message" in err ? String(err.message) : "Failed to create deliverable";
    createError.value = errorMsg;
  } finally {
    createSubmitting.value = false;
  }
}

function handleOpenEdit(item: Deliverable) {
  editMessage.value = "";
  editError.value = null;
  editErrors.value = {};
  activeEditId.value = item.id;
  editSubmissionDeadline.value = item.submissionDeadline;
  editReviewDeadline.value = item.reviewDeadline;
}

function handleCancelEdit() {
  activeEditId.value = null;
  editError.value = null;
}

async function handleEditSubmit() {
  if (!activeEditId.value) return;
  editMessage.value = "";
  editError.value = null;
  editErrors.value = {};

  const result = deliverableEditSchema.safeParse({
    submissionDeadline: editSubmissionDeadline.value,
    reviewDeadline: editReviewDeadline.value,
  });

  if (!result.success) {
    const fe = result.error.flatten().fieldErrors;
    editErrors.value = {
      submissionDeadline: fe.submissionDeadline?.[0] || "",
      reviewDeadline: fe.reviewDeadline?.[0] || "",
    };
    return;
  }

  editSubmitting.value = true;
  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required");

    const updated = await updateDeliverable(activeEditId.value, result.data, token);
    deliverables.value = deliverables.value.map((item) =>
      item.id === updated.id
        ? { ...item, submissionDeadline: updated.submissionDeadline, reviewDeadline: updated.reviewDeadline }
        : item
    );

    editMessage.value = "✓ Deliverable updated successfully";
    activeEditId.value = null;
    setTimeout(() => (editMessage.value = ""), 3000);
  } catch (err) {
    const errorMsg = err && typeof err === "object" && "message" in err ? String(err.message) : "Failed to update deliverable";
    editError.value = errorMsg;
  } finally {
    editSubmitting.value = false;
  }
}
</script>
