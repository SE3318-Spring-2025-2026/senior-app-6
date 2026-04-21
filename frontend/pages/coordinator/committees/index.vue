<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { ArrowLeft, Loader as LoaderIcon, Plus, AlertCircle, CheckCircle2, Search, Building, Users, UserCheck } from "lucide-vue-next";
import type { CoordinatorAdvisor } from "~/composables/useApiClient";
import type { Committee, CommitteeDetail } from "~/types/committee";

definePageMeta({
  middleware: "auth",
  roles: ["Coordinator"],
});

const {
  fetchCommittees,
  createCommittee,
  getAuthToken,
  fetchCoordinatorAdvisors,
  fetchCommitteeDetail,
  assignCommitteeProfessors,
} = useApiClient();

const committees = ref<Committee[]>([]);
const isLoading = ref(true);
const fetchError = ref<string | null>(null);

const showCreateModal = ref(false);
const newCommitteeName = ref("");
const selectedCreateTermId = ref("");
const isCreating = ref(false);
const createError = ref<string | null>(null);
const createSuccess = ref<string | null>(null);
const createErrorToast = ref<string | null>(null);

const showAssignmentModal = ref(false);
const selectedCommittee = ref<Committee | null>(null);
const committeeDetail = ref<CommitteeDetail | null>(null);
const professorOptions = ref<CoordinatorAdvisor[]>([]);
const detailLoading = ref(false);
const detailError = ref<string | null>(null);
const selectedAdvisorId = ref("");
const selectedJuryIds = ref<string[]>([]);
const assignmentError = ref<string | null>(null);
const assignmentSuccess = ref<string | null>(null);
const isAssigning = ref(false);

const availableTermIds = computed(() => {
  return Array.from(new Set(committees.value.map((committee) => committee.termId).filter(Boolean) as string[])).sort((a, b) => b.localeCompare(a));
});

function displayCommitteeName(committee: Committee): string {
  return committee.committeeName || committee.name;
}

function parseErrorMessage(error: unknown, fallback: string): string {
  return error && typeof error === "object" && "message" in error
    ? String(error.message)
    : fallback;
}

function parseAssignmentError(error: unknown): string {
  const rawMessage = parseErrorMessage(error, "Failed to assign professors to committee.");
  if (/(scheduling|schedule|conflict|overlap|timeslot|time slot)/i.test(rawMessage)) {
    return "Atama kaydedilemedi. Seçtiğiniz profesörlerden en az birinde takvim çakışması var. Lütfen farklı bir seçim yapın.";
  }
  return rawMessage;
}

function normalizeSelectionsFromDetail(detail: CommitteeDetail) {
  const advisor = detail.professors.find((item) => item.role === "ADVISOR");
  selectedAdvisorId.value = advisor?.professorId || "";
  selectedJuryIds.value = detail.professors
    .filter((item) => item.role === "JURY")
    .map((item) => item.professorId)
    .filter((professorId) => professorId !== selectedAdvisorId.value);
}

function isJurySelected(professorId: string): boolean {
  return selectedJuryIds.value.includes(professorId);
}

function toggleJurySelection(professorId: string, checked: boolean) {
  if (checked) {
    if (!selectedJuryIds.value.includes(professorId) && professorId !== selectedAdvisorId.value) {
      selectedJuryIds.value = [...selectedJuryIds.value, professorId];
    }
    return;
  }
  selectedJuryIds.value = selectedJuryIds.value.filter((id) => id !== professorId);
}

function handleAdvisorChange(newAdvisorId: string) {
  selectedAdvisorId.value = newAdvisorId;
  selectedJuryIds.value = selectedJuryIds.value.filter((juryId) => juryId !== newAdvisorId);
}

function professorLabelById(professorId?: string): string {
  if (!professorId) return "Not assigned";
  const match = professorOptions.value.find((professor) => professor.advisorId === professorId);
  if (!match) return professorId;
  return `${match.name || match.mail} (${match.mail})`;
}

async function openAssignmentModal(committee: Committee) {
  showAssignmentModal.value = true;
  selectedCommittee.value = committee;
  committeeDetail.value = null;
  detailError.value = null;
  assignmentError.value = null;
  assignmentSuccess.value = null;
  detailLoading.value = true;

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required. Please log in.");

    const [detail, professors] = await Promise.all([
      fetchCommitteeDetail(committee.id, token),
      fetchCoordinatorAdvisors(token),
    ]);

    committeeDetail.value = detail;
    professorOptions.value = professors;
    normalizeSelectionsFromDetail(detail);
  } catch (error) {
    detailError.value = parseErrorMessage(error, "Failed to load committee details.");
  } finally {
    detailLoading.value = false;
  }
}

function closeAssignmentModal() {
  showAssignmentModal.value = false;
  selectedCommittee.value = null;
  committeeDetail.value = null;
  selectedAdvisorId.value = "";
  selectedJuryIds.value = [];
  assignmentError.value = null;
  assignmentSuccess.value = null;
  detailError.value = null;
}

async function handleAssignProfessors() {
  if (!selectedCommittee.value) return;

  if (!selectedAdvisorId.value) {
    assignmentError.value = "Tam olarak 1 birincil danışman seçmeniz gerekiyor.";
    return;
  }

  const juryProfessorIds = selectedJuryIds.value.filter((id) => id !== selectedAdvisorId.value);
  const professors = [
    { professorId: selectedAdvisorId.value, role: "ADVISOR" as const },
    ...juryProfessorIds.map((professorId) => ({ professorId, role: "JURY" as const })),
  ];

  assignmentError.value = null;
  assignmentSuccess.value = null;
  isAssigning.value = true;

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required. Please log in.");

    await assignCommitteeProfessors(selectedCommittee.value.id, { professors }, token);
    assignmentSuccess.value = "Professor assignments saved successfully.";

    const refreshedDetail = await fetchCommitteeDetail(selectedCommittee.value.id, token);
    committeeDetail.value = refreshedDetail;
    normalizeSelectionsFromDetail(refreshedDetail);
  } catch (error) {
    assignmentError.value = parseAssignmentError(error);
  } finally {
    isAssigning.value = false;
  }
}

async function loadCommittees() {
  isLoading.value = true;
  fetchError.value = null;
  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required. Please log in.");
    committees.value = await fetchCommittees(token);
  } catch (error) {
    const message =
      error && typeof error === "object" && "message" in error
        ? String(error.message)
        : "Failed to load committees";
    fetchError.value = message;
  } finally {
    isLoading.value = false;
  }
}

async function handleCreateCommittee() {
  if (!newCommitteeName.value.trim()) return;
  
  isCreating.value = true;
  createError.value = null;
  createErrorToast.value = null;
  createSuccess.value = null;

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required. Please log in.");

    const payload = {
      name: newCommitteeName.value.trim(),
      committeeName: newCommitteeName.value.trim(),
      termId: selectedCreateTermId.value.trim() || undefined,
    };

    const newCommittee = await createCommittee(payload, token);

    // Some backend variants return only id on create; fallback to a lightweight refresh.
    if (displayCommitteeName(newCommittee)) {
      committees.value.push(newCommittee);
      createSuccess.value = `Committee "${displayCommitteeName(newCommittee)}" created successfully.`;
    } else {
      await loadCommittees();
      createSuccess.value = `Committee "${newCommitteeName.value.trim()}" created successfully.`;
    }
    newCommitteeName.value = "";
    selectedCreateTermId.value = "";
    showCreateModal.value = false;

    // clear success toast after 3s
    setTimeout(() => {
      createSuccess.value = null;
    }, 3000);
  } catch (error) {
    const message =
      error && typeof error === "object" && "message" in error
        ? String(error.message)
        : "Failed to create committee";
    createError.value = message;
    createErrorToast.value = message;
  } finally {
    isCreating.value = false;
  }
}

function openCreateModal() {
  newCommitteeName.value = "";
  selectedCreateTermId.value = availableTermIds.value[0] || "";
  createError.value = null;
  createErrorToast.value = null;
  showCreateModal.value = true;
}

onMounted(loadCommittees);
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-7xl space-y-6">
      <NuxtLink
        to="/coordinator/dashboard"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to dashboard
      </NuxtLink>

      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div>
            <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
              Committees Management
            </h1>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Create and manage committees for project jury evaluations.
            </p>
          </div>

          <div>
            <button
              @click="openCreateModal"
              class="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
            >
              <Plus class="h-4 w-4" />
              New Committee
            </button>
          </div>
        </div>
      </header>

      <!-- Global Success Toast -->
      <div
        v-if="createSuccess"
        class="flex items-center gap-3 rounded-lg border border-emerald-300 bg-emerald-50 p-4 text-emerald-900 shadow-sm transition-all dark:border-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-100"
      >
        <CheckCircle2 class="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
        <p class="text-sm font-medium">{{ createSuccess }}</p>
      </div>

      <div
        v-if="createErrorToast"
        class="flex items-center gap-3 rounded-lg border border-red-300 bg-red-50 p-4 text-red-900 shadow-sm transition-all dark:border-red-800 dark:bg-red-950/50 dark:text-red-100"
      >
        <AlertCircle class="h-5 w-5 text-red-600 dark:text-red-400" />
        <p class="text-sm font-medium">{{ createErrorToast }}</p>
      </div>

      <!-- Errors -->
      <div
        v-if="fetchError"
        class="flex items-start gap-3 rounded-lg border border-red-300 bg-red-50 p-4 dark:border-red-800 dark:bg-red-950/50"
      >
        <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
        <div>
          <p class="font-medium text-red-900 dark:text-red-300">Error loading committees</p>
          <p class="text-sm text-red-700 dark:text-red-400">{{ fetchError }}</p>
        </div>
      </div>

      <div
        v-if="isLoading && !fetchError"
        class="flex items-center justify-center rounded-lg border border-slate-200 bg-white p-12 dark:border-slate-700 dark:bg-slate-800"
      >
        <div class="text-center">
          <LoaderIcon class="mx-auto h-8 w-8 animate-spin text-blue-600 dark:text-blue-400" />
          <p class="mt-3 text-sm text-slate-600 dark:text-slate-400">Loading committees...</p>
        </div>
      </div>

      <section
        v-if="!isLoading && !fetchError"
        class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg"
      >
        <div class="mb-4 flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
          <Building class="h-5 w-5 text-slate-700 dark:text-slate-300" />
          Committees ({{ committees.length }})
        </div>

        <div
          v-if="committees.length === 0"
          class="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-8 text-center dark:border-slate-600 dark:bg-slate-900/50"
        >
          <Search class="mx-auto h-8 w-8 text-slate-500 dark:text-slate-400" />
          <p class="mt-3 text-sm font-medium text-slate-700 dark:text-slate-300">
            No committees found. Create one to get started.
          </p>
        </div>

        <div v-else class="overflow-x-auto">
          <table class="min-w-full divide-y divide-slate-200 text-sm dark:divide-slate-700">
            <thead>
              <tr class="text-left text-xs uppercase tracking-wide text-slate-500 dark:text-slate-400">
                <th class="px-3 py-3 font-semibold">Committee Name</th>
                <th class="px-3 py-3 font-semibold">Term</th>
                <th class="px-3 py-3 font-semibold">Created At</th>
                <th class="px-3 py-3 font-semibold">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-200 dark:divide-slate-700">
              <tr
                v-for="committee in committees"
                :key="committee.id"
                class="transition hover:bg-slate-50 dark:hover:bg-slate-900/50"
              >
                <td class="px-3 py-3 font-medium text-slate-900 dark:text-white">
                  {{ displayCommitteeName(committee) }}
                </td>
                <td class="px-3 py-3 text-slate-700 dark:text-slate-300">
                  {{ committee.termId || 'Current' }}
                </td>
                <td class="px-3 py-3 text-slate-700 dark:text-slate-300">
                  {{ committee.createdAt ? new Date(committee.createdAt).toLocaleDateString() : 'N/A' }}
                </td>
                <td class="px-3 py-3">
                  <button
                    @click="openAssignmentModal(committee)"
                    class="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 py-2 text-xs font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600"
                  >
                    <Users class="h-4 w-4" />
                    Assign Professors
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- Create Modal -->
      <div
        v-if="showCreateModal"
        class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm transition-opacity"
      >
        <div class="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-xl dark:border-slate-700 dark:bg-slate-800">
          <h2 class="text-xl font-semibold text-slate-900 dark:text-white">Create New Committee</h2>
          <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
            Enter a unique name for the new committee.
          </p>

          <form @submit.prevent="handleCreateCommittee" class="mt-6 space-y-4">
            <div>
              <label for="committeeName" class="block text-sm font-medium text-slate-700 dark:text-slate-300">
                Committee Name
              </label>
              <input
                id="committeeName"
                v-model="newCommitteeName"
                type="text"
                required
                class="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-1 focus:ring-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400 dark:focus:ring-blue-400"
                placeholder="e.g. Jury Alpha"
              />
            </div>

            <div>
              <label for="committeeTerm" class="block text-sm font-medium text-slate-700 dark:text-slate-300">
                Term ID (optional)
              </label>
              <input
                id="committeeTerm"
                v-model="selectedCreateTermId"
                type="text"
                class="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-1 focus:ring-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400 dark:focus:ring-blue-400"
                placeholder="e.g. 2025-FALL"
                list="knownCommitteeTerms"
              />
              <datalist id="knownCommitteeTerms">
                <option v-for="term in availableTermIds" :key="term" :value="term" />
              </datalist>
            </div>

            <div v-if="createError" class="rounded-lg bg-red-50 p-3 text-sm text-red-700 dark:bg-red-900/30 dark:text-red-400">
              {{ createError }}
            </div>

            <div class="flex justify-end gap-3 pt-2">
              <button
                type="button"
                @click="showCreateModal = false"
                class="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
                :disabled="isCreating"
              >
                Cancel
              </button>
              <button
                type="submit"
                class="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-700 dark:hover:bg-blue-600"
                :disabled="isCreating || !newCommitteeName.trim()"
              >
                <LoaderIcon v-if="isCreating" class="h-4 w-4 animate-spin" />
                <span>{{ isCreating ? 'Creating...' : 'Create Committee' }}</span>
              </button>
            </div>
          </form>
        </div>
      </div>

      <!-- Assignment Modal -->
      <div
        v-if="showAssignmentModal"
        class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm transition-opacity"
      >
        <div class="w-full max-w-3xl rounded-2xl border border-slate-200 bg-white p-6 shadow-xl dark:border-slate-700 dark:bg-slate-800">
          <div class="flex items-start justify-between gap-4">
            <div>
              <h2 class="text-xl font-semibold text-slate-900 dark:text-white">Committee Assignment</h2>
              <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
                {{ selectedCommittee ? displayCommitteeName(selectedCommittee) : "Selected committee" }}
              </p>
            </div>
            <button
              type="button"
              @click="closeAssignmentModal"
              class="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
            >
              Close
            </button>
          </div>

          <div v-if="detailLoading" class="mt-6 flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
            <LoaderIcon class="h-4 w-4 animate-spin" />
            Loading committee details...
          </div>

          <div
            v-else-if="detailError"
            class="mt-6 rounded-lg border border-red-300 bg-red-50 p-4 text-sm text-red-800 dark:border-red-800 dark:bg-red-900/30 dark:text-red-300"
          >
            {{ detailError }}
          </div>

          <div v-else class="mt-6 space-y-6">
            <div class="rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/40">
              <p class="text-sm font-semibold text-slate-900 dark:text-white">Current Assignment</p>
              <p class="mt-2 text-sm text-slate-700 dark:text-slate-300">
                Advisor:
                <span class="font-medium">
                  {{ professorLabelById(committeeDetail?.professors.find((item) => item.role === "ADVISOR")?.professorId) }}
                </span>
              </p>
              <p class="mt-1 text-sm text-slate-700 dark:text-slate-300">
                Jury Count: {{ committeeDetail?.professors.filter((item) => item.role === "JURY").length || 0 }}
              </p>
            </div>

            <div class="grid gap-5 md:grid-cols-2">
              <div>
                <label for="primaryAdvisorSelect" class="block text-sm font-medium text-slate-700 dark:text-slate-300">
                  Primary Advisor <span class="text-red-600">*</span>
                </label>
                <select
                  id="primaryAdvisorSelect"
                  v-model="selectedAdvisorId"
                  @change="handleAdvisorChange(selectedAdvisorId)"
                  class="mt-2 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-1 focus:ring-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white"
                >
                  <option value="">Select one advisor</option>
                  <option
                    v-for="professor in professorOptions"
                    :key="professor.advisorId"
                    :value="professor.advisorId"
                  >
                    {{ professor.name || professor.mail }} ({{ professor.mail }})
                  </option>
                </select>
              </div>

              <div>
                <p class="text-sm font-medium text-slate-700 dark:text-slate-300">Jury Members (optional)</p>
                <div class="mt-2 max-h-52 space-y-2 overflow-y-auto rounded-lg border border-slate-300 bg-white p-3 dark:border-slate-600 dark:bg-slate-700">
                  <label
                    v-for="professor in professorOptions"
                    :key="`${professor.advisorId}-jury`"
                    class="flex cursor-pointer items-start gap-2 rounded-md px-2 py-1.5 transition hover:bg-slate-50 dark:hover:bg-slate-600"
                  >
                    <input
                      type="checkbox"
                      :checked="isJurySelected(professor.advisorId)"
                      :disabled="professor.advisorId === selectedAdvisorId || isAssigning"
                      class="mt-0.5 h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500 dark:border-slate-500"
                      @change="toggleJurySelection(professor.advisorId, ($event.target as HTMLInputElement).checked)"
                    />
                    <span class="text-sm text-slate-700 dark:text-slate-200">
                      {{ professor.name || professor.mail }}
                      <span class="text-slate-500 dark:text-slate-400">({{ professor.mail }})</span>
                    </span>
                  </label>

                  <p v-if="professorOptions.length === 0" class="text-sm text-slate-500 dark:text-slate-400">
                    No professors available for selection.
                  </p>
                </div>
              </div>
            </div>

            <div
              v-if="assignmentError"
              class="rounded-lg border border-red-300 bg-red-50 p-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-300"
            >
              {{ assignmentError }}
            </div>

            <div
              v-if="assignmentSuccess"
              class="rounded-lg border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-800 dark:border-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-300"
            >
              {{ assignmentSuccess }}
            </div>

            <div class="flex justify-end gap-3">
              <button
                type="button"
                @click="closeAssignmentModal"
                class="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
                :disabled="isAssigning"
              >
                Cancel
              </button>
              <button
                type="button"
                @click="handleAssignProfessors"
                class="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-blue-700 dark:hover:bg-blue-600"
                :disabled="!selectedAdvisorId || isAssigning"
              >
                <LoaderIcon v-if="isAssigning" class="h-4 w-4 animate-spin" />
                <UserCheck v-else class="h-4 w-4" />
                <span>{{ isAssigning ? "Saving..." : "Save Assignment" }}</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </main>
</template>
