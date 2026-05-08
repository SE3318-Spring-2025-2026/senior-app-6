<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ArrowLeft, Loader as LoaderIcon, AlertCircle, Users } from "lucide-vue-next";
import type { AdvisorCapacityResponse } from "~/types/advisor";

definePageMeta({
  middleware: "auth",
  roles: ["Coordinator"],
});

const { fetchCoordinatorAdvisors, updateAdvisorCapacity, getAuthToken } = useApiClient();

const advisors = ref<AdvisorCapacityResponse[]>([]);
const isLoading = ref(true);
const fetchError = ref<string | null>(null);

type RowEditState = { editing: boolean; inputValue: string; saving: boolean; error: string | null };
const editState = ref<Record<string, RowEditState>>({});

function getEdit(advisorId: string): RowEditState {
  if (!editState.value[advisorId]) {
    editState.value[advisorId] = { editing: false, inputValue: "", saving: false, error: null };
  }
  return editState.value[advisorId];
}

function openEdit(advisor: AdvisorCapacityResponse) {
  const state = getEdit(advisor.advisorId);
  state.inputValue = String(advisor.capacity ?? 5);
  state.editing = true;
  state.error = null;
}

function cancelEdit(advisorId: string) {
  const state = getEdit(advisorId);
  state.editing = false;
  state.error = null;
}

async function saveCapacity(advisor: AdvisorCapacityResponse) {
  const state = getEdit(advisor.advisorId);
  const parsed = Number(state.inputValue);

  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 20) {
    state.error = "Capacity must be a whole number between 1 and 20.";
    return;
  }

  state.saving = true;
  state.error = null;

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required.");
    const updated = await updateAdvisorCapacity(advisor.advisorId, parsed, token);
    const idx = advisors.value.findIndex((a) => a.advisorId === advisor.advisorId);
    if (idx !== -1) advisors.value[idx] = updated;
    state.editing = false;
  } catch (error: unknown) {
    state.error =
      error && typeof error === "object" && "message" in error
        ? String(error.message)
        : "Failed to update capacity.";
  } finally {
    state.saving = false;
  }
}

async function loadAdvisors() {
  isLoading.value = true;
  fetchError.value = null;
  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required. Please log in.");
    advisors.value = await fetchCoordinatorAdvisors(token);
  } catch (error) {
    fetchError.value =
      error && typeof error === "object" && "message" in error
        ? String(error.message)
        : "Failed to load advisors.";
  } finally {
    isLoading.value = false;
  }
}

onMounted(loadAdvisors);
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-5xl space-y-6">
      <NuxtLink
        to="/coordinator/dashboard"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to dashboard
      </NuxtLink>

      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
          Advisor Management
        </h1>
        <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
          View and adjust advisor group capacities.
        </p>
      </header>

      <div
        v-if="fetchError"
        class="flex items-start gap-3 rounded-lg border border-red-300 bg-red-50 p-4 dark:border-red-800 dark:bg-red-950/50"
      >
        <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
        <div>
          <p class="font-medium text-red-900 dark:text-red-300">Error loading advisors</p>
          <p class="text-sm text-red-700 dark:text-red-400">{{ fetchError }}</p>
        </div>
      </div>

      <div
        v-if="isLoading && !fetchError"
        class="flex items-center justify-center rounded-lg border border-slate-200 bg-white p-12 dark:border-slate-700 dark:bg-slate-800"
      >
        <div class="text-center">
          <LoaderIcon class="mx-auto h-8 w-8 animate-spin text-blue-600 dark:text-blue-400" />
          <p class="mt-3 text-sm text-slate-600 dark:text-slate-400">Loading advisors...</p>
        </div>
      </div>

      <section
        v-if="!isLoading && !fetchError"
        class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg"
      >
        <div class="mb-4 flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
          <Users class="h-5 w-5 text-slate-700 dark:text-slate-300" />
          Advisors ({{ advisors.length }})
        </div>

        <div
          v-if="advisors.length === 0"
          class="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-8 text-center dark:border-slate-600 dark:bg-slate-900/50"
        >
          <p class="text-sm font-medium text-slate-700 dark:text-slate-300">No advisors found.</p>
        </div>

        <div v-else class="overflow-x-auto">
          <table class="min-w-full divide-y divide-slate-200 text-sm dark:divide-slate-700">
            <thead>
              <tr class="text-left text-xs uppercase tracking-wide text-slate-500 dark:text-slate-400">
                <th class="px-3 py-3 font-semibold">Name</th>
                <th class="px-3 py-3 font-semibold">Email</th>
                <th class="px-3 py-3 font-semibold">Groups</th>
                <th class="px-3 py-3 font-semibold">Capacity</th>
                <th class="px-3 py-3 font-semibold">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-200 dark:divide-slate-700">
              <tr
                v-for="advisor in advisors"
                :key="advisor.advisorId"
                class="transition hover:bg-slate-50 dark:hover:bg-slate-900/50"
              >
                <td class="px-3 py-3 font-medium text-slate-900 dark:text-white">
                  {{ advisor.name || "—" }}
                </td>
                <td class="px-3 py-3 text-slate-700 dark:text-slate-300">
                  {{ advisor.mail }}
                </td>
                <td class="px-3 py-3 text-slate-700 dark:text-slate-300">
                  {{ advisor.currentGroupCount }}
                </td>
                <td class="px-3 py-3">
                  <template v-if="getEdit(advisor.advisorId).editing">
                    <div class="flex flex-wrap items-center gap-2">
                      <input
                        v-model="getEdit(advisor.advisorId).inputValue"
                        type="number"
                        min="1"
                        max="20"
                        class="w-20 rounded-md border border-slate-300 bg-white px-2 py-1 text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white"
                      />
                      <button
                        type="button"
                        :disabled="getEdit(advisor.advisorId).saving"
                        @click="saveCapacity(advisor)"
                        class="inline-flex items-center gap-1 rounded-md bg-blue-600 px-2.5 py-1 text-xs font-medium text-white transition hover:bg-blue-700 disabled:opacity-50"
                      >
                        <LoaderIcon v-if="getEdit(advisor.advisorId).saving" class="h-3 w-3 animate-spin" />
                        Save
                      </button>
                      <button
                        type="button"
                        :disabled="getEdit(advisor.advisorId).saving"
                        @click="cancelEdit(advisor.advisorId)"
                        class="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
                      >
                        Cancel
                      </button>
                    </div>
                    <p v-if="getEdit(advisor.advisorId).error" class="mt-1 text-xs text-red-600 dark:text-red-400">
                      {{ getEdit(advisor.advisorId).error }}
                    </p>
                  </template>
                  <span v-else class="text-slate-700 dark:text-slate-300">
                    {{ advisor.capacity }}
                  </span>
                </td>
                <td class="px-3 py-3">
                  <button
                    v-if="!getEdit(advisor.advisorId).editing"
                    type="button"
                    @click="openEdit(advisor)"
                    class="inline-flex items-center rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600"
                  >
                    Edit
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </main>
</template>
