<script setup lang="ts">
import { AlertCircle, ArrowLeft, Loader2 } from "lucide-vue-next";
import type { FinalGradeResponse } from "~/composables/useApiClient";
import type { GroupDetailResponse, GroupSummaryResponse } from "~/types/group";

definePageMeta({
  middleware: "auth",
  roles: ["Coordinator"],
});

interface StudentGradeRow {
  studentId: string;
  groupId: string;
  groupName: string;
  grade: FinalGradeResponse | null;
  loading: boolean;
  error: string | null;
}

type SortKey = "studentId" | "groupName" | "weightedTotal" | "completionRatio" | "finalGrade";
type SortDirection = "asc" | "desc";

const { getAuthToken, fetchCoordinatorGroups, fetchCoordinatorGroupDetail, calculateStudentGrade } = useApiClient();

const rows = ref<StudentGradeRow[]>([]);
const isPageLoading = ref(true);
const pageError = ref<string | null>(null);
const selectedGroupId = ref("ALL");
const sortKey = ref<SortKey>("groupName");
const sortDirection = ref<SortDirection>("asc");

const availableGroups = computed(() => {
  const groups = new Map<string, string>();
  for (const row of rows.value) {
    if (!groups.has(row.groupId)) {
      groups.set(row.groupId, row.groupName);
    }
  }
  return Array.from(groups.entries()).map(([id, name]) => ({ id, name }));
});

const filteredRows = computed(() => {
  if (selectedGroupId.value === "ALL") {
    return rows.value;
  }
  return rows.value.filter((row) => row.groupId === selectedGroupId.value);
});

const sortedRows = computed(() => {
  const sorted = [...filteredRows.value];
  sorted.sort((left, right) => {
    if (sortKey.value === "studentId" || sortKey.value === "groupName") {
      const a = sortKey.value === "studentId" ? left.studentId : left.groupName;
      const b = sortKey.value === "studentId" ? right.studentId : right.groupName;
      const result = a.localeCompare(b, undefined, { sensitivity: "base" });
      return sortDirection.value === "asc" ? result : -result;
    }

    const leftValue = left.grade?.[sortKey.value] ?? Number.NEGATIVE_INFINITY;
    const rightValue = right.grade?.[sortKey.value] ?? Number.NEGATIVE_INFINITY;
    const result = leftValue - rightValue;
    return sortDirection.value === "asc" ? result : -result;
  });
  return sorted;
});

function toggleSort(key: SortKey) {
  if (sortKey.value === key) {
    sortDirection.value = sortDirection.value === "asc" ? "desc" : "asc";
    return;
  }

  sortKey.value = key;
  sortDirection.value = key === "studentId" ? "asc" : "desc";
}

function sortIndicator(key: SortKey): string {
  if (sortKey.value !== key) return "";
  return sortDirection.value === "asc" ? "↑" : "↓";
}

function formatDecimal(value: number | null | undefined, digits = 4): string {
  if (value === null || value === undefined) return "—";
  if (!Number.isFinite(value)) return "—";
  return value.toFixed(digits);
}

function toStudentRows(groupSummaries: GroupSummaryResponse[], details: GroupDetailResponse[]): StudentGradeRow[] {
  const nameById = new Map(groupSummaries.map((group) => [group.id, group.groupName]));
  return details.flatMap((detail) =>
    detail.members.map((member) => ({
      studentId: member.studentId,
      groupId: detail.id,
      groupName: nameById.get(detail.id) || detail.groupName,
      grade: null,
      loading: false,
      error: null,
    }))
  );
}

async function loadPageData() {
  isPageLoading.value = true;
  pageError.value = null;
  rows.value = [];

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required");

    const groups = await fetchCoordinatorGroups(token, "ALL");
    if (groups.length === 0) {
      rows.value = [];
      return;
    }

    const details = await Promise.all(groups.map((group) => fetchCoordinatorGroupDetail(group.id, token)));
    rows.value = toStudentRows(groups, details);
  } catch (err: unknown) {
    pageError.value =
      err && typeof err === "object" && "message" in err
        ? String(err.message)
        : "Failed to load students.";
  } finally {
    isPageLoading.value = false;
  }
}

async function handleCalculate(row: StudentGradeRow) {
  if (row.loading) return;

  row.loading = true;
  row.error = null;

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required");

    row.grade = await calculateStudentGrade(row.studentId, token);
  } catch (err: unknown) {
    row.error =
      err && typeof err === "object" && "message" in err
        ? String(err.message)
        : "Calculation failed.";
  } finally {
    row.loading = false;
  }
}

onMounted(loadPageData);
</script>

<template>
  <main
    class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8"
  >
    <div class="mx-auto w-full max-w-7xl space-y-6">
      <NuxtLink
        to="/coordinator/dashboard"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to Dashboard
      </NuxtLink>

      <header
        class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg"
      >
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
          Final Grade Dashboard
        </h1>
        <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
          Calculate per-student weighted total, completion ratio, and final grade.
        </p>
      </header>

      <section
        v-if="pageError"
        class="flex items-start gap-3 rounded-2xl border border-red-300 bg-red-50 p-4 text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300"
      >
        <AlertCircle class="mt-0.5 h-5 w-5 shrink-0" />
        <p class="text-sm">{{ pageError }}</p>
      </section>

      <section
        v-else-if="isPageLoading"
        class="flex items-center justify-center rounded-2xl border border-slate-200 bg-white p-12 dark:border-slate-700 dark:bg-slate-800"
      >
        <Loader2 class="h-6 w-6 animate-spin text-blue-600 dark:text-blue-400" />
        <span class="ml-3 text-sm text-slate-600 dark:text-slate-400">Loading students...</span>
      </section>

      <section
        v-else-if="rows.length === 0"
        class="rounded-2xl border border-dashed border-slate-300 bg-white py-12 text-center dark:border-slate-600 dark:bg-slate-800"
      >
        <p class="text-sm text-slate-600 dark:text-slate-400">No students found for grade calculation.</p>
      </section>

      <section
        v-else
        class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800"
      >
        <div class="border-b border-slate-200 bg-slate-50/80 px-4 py-3 dark:border-slate-700 dark:bg-slate-900/40">
          <label class="block max-w-xs space-y-1.5">
            <span class="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
              Group Filter
            </span>
            <select
              v-model="selectedGroupId"
              class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400"
            >
              <option value="ALL">All Groups</option>
              <option v-for="group in availableGroups" :key="group.id" :value="group.id">
                {{ group.name }}
              </option>
            </select>
          </label>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full table-fixed text-sm">
            <thead>
              <tr class="border-b border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-700/50">
                <th class="w-36 px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  <button type="button" class="inline-flex items-center gap-1 hover:text-slate-700 dark:hover:text-slate-200" @click="toggleSort('studentId')">
                    Student ID <span class="inline-block w-3 text-center text-[10px]">{{ sortIndicator("studentId") }}</span>
                  </button>
                </th>
                <th class="w-40 px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  <button type="button" class="inline-flex items-center gap-1 hover:text-slate-700 dark:hover:text-slate-200" @click="toggleSort('groupName')">
                    Group <span class="inline-block w-3 text-center text-[10px]">{{ sortIndicator("groupName") }}</span>
                  </button>
                </th>
                <th class="w-44 px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  <button type="button" class="inline-flex items-center gap-1 hover:text-slate-700 dark:hover:text-slate-200" @click="toggleSort('weightedTotal')">
                    Weighted Total <span class="inline-block w-3 text-center text-[10px]">{{ sortIndicator("weightedTotal") }}</span>
                  </button>
                </th>
                <th class="w-44 px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  <button type="button" class="inline-flex items-center gap-1 hover:text-slate-700 dark:hover:text-slate-200" @click="toggleSort('completionRatio')">
                    Completion Ratio <span class="inline-block w-3 text-center text-[10px]">{{ sortIndicator("completionRatio") }}</span>
                  </button>
                </th>
                <th class="w-44 px-3 py-2 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  <button type="button" class="inline-flex items-center gap-1 hover:text-slate-700 dark:hover:text-slate-200" @click="toggleSort('finalGrade')">
                    Final Grade <span class="inline-block w-3 text-center text-[10px]">{{ sortIndicator("finalGrade") }}</span>
                  </button>
                </th>
                <th class="w-32 px-3 py-2 text-right text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">Action</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100 dark:divide-slate-700">
              <template v-for="row in sortedRows" :key="row.studentId">
                <tr class="hover:bg-slate-50 dark:hover:bg-slate-700">
                  <td class="px-3 py-2 font-medium text-slate-900 dark:text-white">{{ row.studentId }}</td>
                  <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ row.groupName }}</td>
                  <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ formatDecimal(row.grade?.weightedTotal) }}</td>
                  <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ formatDecimal(row.grade?.completionRatio) }}</td>
                  <td class="px-3 py-2 text-slate-700 dark:text-slate-300">{{ formatDecimal(row.grade?.finalGrade) }}</td>
                  <td class="px-3 py-2 text-right">
                    <button
                      type="button"
                      class="inline-flex w-24 items-center justify-center rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
                      :class="row.loading ? 'opacity-80' : ''"
                      @click="handleCalculate(row)"
                    >
                      Calculate
                    </button>
                  </td>
                </tr>
                <tr v-if="row.error">
                  <td colspan="6" class="px-3 pb-3 pt-1">
                    <p class="text-xs text-red-600 dark:text-red-400">{{ row.error }}</p>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
        <div
          v-if="sortedRows.length === 0"
          class="border-t border-slate-200 px-4 py-4 text-sm text-slate-600 dark:border-slate-700 dark:text-slate-400"
        >
          No students found for the selected group.
        </div>
      </section>
    </div>
  </main>
</template>
