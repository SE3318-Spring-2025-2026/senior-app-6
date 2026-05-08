<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ArrowLeft, Search, X, AlertCircle, ClipboardList } from 'lucide-vue-next';
import type { AuditLogEntry, AuditLogQuery } from '~/types/audit-log';

definePageMeta({
  middleware: 'auth',
  roles: ['Coordinator'],
});

const { fetchAuditLogs, getAuthToken } = useApiClient();

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const PAGE_SIZE = 20;

const filterCategory = ref('');
const filterOutcome = ref('');
const filterUserType = ref('');
const filterUserId = ref('');
const filterFrom = ref('');
const filterTo = ref('');
const userIdError = ref('');

const appliedQuery = reactive<AuditLogQuery>({});

const currentPage = ref(0);
const entries = ref<AuditLogEntry[]>([]);
const totalPages = ref(0);
const totalElements = ref(0);
const isLoading = ref(false);
const fetchError = ref<string | null>(null);

async function load() {
  isLoading.value = true;
  fetchError.value = null;
  try {
    const token = getAuthToken();
    const result = await fetchAuditLogs(
      { ...appliedQuery, page: currentPage.value, size: PAGE_SIZE },
      token ?? undefined,
    );
    entries.value = result.content;
    totalPages.value = result.totalPages;
    totalElements.value = result.totalElements;
  } catch (err: unknown) {
    fetchError.value =
      err && typeof err === 'object' && 'message' in err
        ? String((err as { message: string }).message)
        : 'Failed to load audit logs.';
    entries.value = [];
  } finally {
    isLoading.value = false;
  }
}

function applyFilters() {
  if (filterUserId.value && !UUID_RE.test(filterUserId.value)) {
    userIdError.value = 'Invalid UUID format (e.g. 550e8400-e29b-41d4-a716-446655440000).';
    return;
  }
  userIdError.value = '';
  appliedQuery.category = filterCategory.value || undefined;
  appliedQuery.outcome = filterOutcome.value || undefined;
  appliedQuery.userType = filterUserType.value || undefined;
  appliedQuery.userId = filterUserId.value || undefined;
  appliedQuery.from = filterFrom.value || undefined;
  appliedQuery.to = filterTo.value || undefined;
  currentPage.value = 0;
  load();
}

function clearFilters() {
  filterCategory.value = '';
  filterOutcome.value = '';
  filterUserType.value = '';
  filterUserId.value = '';
  filterFrom.value = '';
  filterTo.value = '';
  userIdError.value = '';
  delete appliedQuery.category;
  delete appliedQuery.outcome;
  delete appliedQuery.userType;
  delete appliedQuery.userId;
  delete appliedQuery.from;
  delete appliedQuery.to;
  currentPage.value = 0;
  load();
}

function prevPage() {
  if (currentPage.value > 0) {
    currentPage.value--;
    load();
  }
}

function nextPage() {
  if (currentPage.value < totalPages.value - 1) {
    currentPage.value++;
    load();
  }
}

function formatDate(iso: string): string {
  return new Intl.DateTimeFormat('tr-TR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(iso));
}

function truncateUuid(uuid: string | null): string {
  if (!uuid) return '—';
  return uuid.length > 13 ? uuid.slice(0, 13) + '…' : uuid;
}

const CATEGORY_CLASSES: Record<string, string> = {
  AUTH: 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300',
  GROUP: 'bg-violet-100 text-violet-700 dark:bg-violet-900/40 dark:text-violet-300',
  ADVISOR: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
  GRADING: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
};

function categoryClass(cat: string): string {
  return (
    CATEGORY_CLASSES[cat] ??
    'bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-300'
  );
}

onMounted(load);
</script>

<template>
  <main
    class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8"
  >
    <div class="mx-auto w-full max-w-7xl space-y-6">
      <NuxtLink
        to="/admin/dashboard"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to dashboard
      </NuxtLink>

      <!-- Header -->
      <header
        class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90"
      >
        <div class="flex items-center gap-3">
          <ClipboardList class="h-7 w-7 text-indigo-600 dark:text-indigo-400" />
          <div>
            <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white">
              Audit Logs
            </h1>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
              Browse system activity records. Use filters to narrow results.
            </p>
          </div>
        </div>
      </header>

      <!-- Filter bar -->
      <section
        class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800"
      >
        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
          <!-- Category -->
          <div class="flex flex-col gap-1">
            <label class="text-xs font-medium text-slate-600 dark:text-slate-400">Category</label>
            <select
              v-model="filterCategory"
              class="rounded-lg border border-slate-300 bg-white px-2.5 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:ring-indigo-900"
            >
              <option value="">ALL</option>
              <option value="AUTH">AUTH</option>
              <option value="GROUP">GROUP</option>
              <option value="ADVISOR">ADVISOR</option>
              <option value="GRADING">GRADING</option>
            </select>
          </div>

          <!-- Outcome -->
          <div class="flex flex-col gap-1">
            <label class="text-xs font-medium text-slate-600 dark:text-slate-400">Outcome</label>
            <select
              v-model="filterOutcome"
              class="rounded-lg border border-slate-300 bg-white px-2.5 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:ring-indigo-900"
            >
              <option value="">ALL</option>
              <option value="SUCCESS">SUCCESS</option>
              <option value="FAILURE">FAILURE</option>
            </select>
          </div>

          <!-- UserType -->
          <div class="flex flex-col gap-1">
            <label class="text-xs font-medium text-slate-600 dark:text-slate-400">User Type</label>
            <select
              v-model="filterUserType"
              class="rounded-lg border border-slate-300 bg-white px-2.5 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:ring-indigo-900"
            >
              <option value="">ALL</option>
              <option value="STAFF">STAFF</option>
              <option value="STUDENT">STUDENT</option>
            </select>
          </div>

          <!-- User ID -->
          <div class="flex flex-col gap-1">
            <label class="text-xs font-medium text-slate-600 dark:text-slate-400">User ID</label>
            <input
              v-model="filterUserId"
              type="text"
              placeholder="xxxxxxxx-xxxx-…"
              class="rounded-lg border px-2.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-200 dark:focus:ring-indigo-900"
              :class="
                userIdError
                  ? 'border-red-400 bg-red-50 text-red-900 dark:border-red-600 dark:bg-red-950/30 dark:text-red-200'
                  : 'border-slate-300 bg-white text-slate-900 focus:border-indigo-500 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100'
              "
            />
          </div>

          <!-- From -->
          <div class="flex flex-col gap-1">
            <label class="text-xs font-medium text-slate-600 dark:text-slate-400">From</label>
            <input
              v-model="filterFrom"
              type="datetime-local"
              class="rounded-lg border border-slate-300 bg-white px-2.5 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:ring-indigo-900"
            />
          </div>

          <!-- To -->
          <div class="flex flex-col gap-1">
            <label class="text-xs font-medium text-slate-600 dark:text-slate-400">To</label>
            <input
              v-model="filterTo"
              type="datetime-local"
              class="rounded-lg border border-slate-300 bg-white px-2.5 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:ring-indigo-900"
            />
          </div>
        </div>

        <p v-if="userIdError" class="mt-2 text-xs text-red-600 dark:text-red-400">
          {{ userIdError }}
        </p>

        <div class="mt-4 flex gap-2">
          <button
            @click="applyFilters"
            class="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-indigo-700"
          >
            <Search class="h-4 w-4" />
            Apply
          </button>
          <button
            @click="clearFilters"
            class="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
          >
            <X class="h-4 w-4" />
            Clear
          </button>
        </div>
      </section>

      <!-- Results -->
      <section
        class="rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800"
      >
        <!-- Error -->
        <div
          v-if="fetchError"
          class="flex items-center gap-3 rounded-t-2xl border-b border-red-200 bg-red-50 px-5 py-3 dark:border-red-800 dark:bg-red-950/40"
        >
          <AlertCircle class="h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
          <p class="text-sm text-red-700 dark:text-red-300">{{ fetchError }}</p>
        </div>

        <!-- Table wrapper -->
        <div class="overflow-x-auto">
          <table class="w-full min-w-[800px] text-sm">
            <thead>
              <tr class="border-b border-slate-200 dark:border-slate-700">
                <th
                  class="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400"
                >
                  Occurred At
                </th>
                <th
                  class="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400"
                >
                  Category
                </th>
                <th
                  class="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400"
                >
                  Action
                </th>
                <th
                  class="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400"
                >
                  Outcome
                </th>
                <th
                  class="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400"
                >
                  User Type
                </th>
                <th
                  class="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400"
                >
                  User ID
                </th>
                <th
                  class="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400"
                >
                  IP Address
                </th>
              </tr>
            </thead>
            <tbody>
              <!-- Loading skeleton -->
              <template v-if="isLoading">
                <tr
                  v-for="n in 5"
                  :key="n"
                  class="border-b border-slate-100 dark:border-slate-700/60"
                >
                  <td class="px-4 py-3">
                    <div class="h-4 w-36 animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
                  </td>
                  <td class="px-4 py-3">
                    <div class="h-5 w-16 animate-pulse rounded-full bg-slate-200 dark:bg-slate-700" />
                  </td>
                  <td class="px-4 py-3">
                    <div class="h-4 w-28 animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
                  </td>
                  <td class="px-4 py-3">
                    <div class="h-5 w-20 animate-pulse rounded-full bg-slate-200 dark:bg-slate-700" />
                  </td>
                  <td class="px-4 py-3">
                    <div class="h-4 w-14 animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
                  </td>
                  <td class="px-4 py-3">
                    <div class="h-4 w-24 animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
                  </td>
                  <td class="px-4 py-3">
                    <div class="h-4 w-24 animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
                  </td>
                </tr>
              </template>

              <!-- Empty state -->
              <tr v-else-if="entries.length === 0 && !fetchError">
                <td colspan="7" class="px-4 py-16 text-center">
                  <div class="flex flex-col items-center gap-2">
                    <ClipboardList class="h-10 w-10 text-slate-300 dark:text-slate-600" />
                    <p class="text-sm font-medium text-slate-500 dark:text-slate-400">
                      No audit log entries found.
                    </p>
                    <p class="text-xs text-slate-400 dark:text-slate-500">
                      Try adjusting the filters.
                    </p>
                  </div>
                </td>
              </tr>

              <!-- Rows -->
              <template v-else>
                <tr
                  v-for="entry in entries"
                  :key="entry.id"
                  class="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-700/60 dark:hover:bg-slate-700/30"
                >
                  <td class="whitespace-nowrap px-4 py-3 font-mono text-xs text-slate-700 dark:text-slate-300">
                    {{ formatDate(entry.occurredAt) }}
                  </td>
                  <td class="px-4 py-3">
                    <span
                      class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
                      :class="categoryClass(entry.category)"
                    >
                      {{ entry.category }}
                    </span>
                  </td>
                  <td class="px-4 py-3 text-slate-800 dark:text-slate-200">
                    {{ entry.action }}
                  </td>
                  <td class="px-4 py-3">
                    <span
                      class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold"
                      :class="
                        entry.outcome === 'SUCCESS'
                          ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300'
                          : 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300'
                      "
                    >
                      {{ entry.outcome }}
                    </span>
                  </td>
                  <td class="px-4 py-3 text-slate-700 dark:text-slate-300">
                    {{ entry.userType }}
                  </td>
                  <td class="px-4 py-3">
                    <span
                      v-if="entry.userId"
                      :title="entry.userId"
                      class="cursor-default font-mono text-xs text-slate-700 dark:text-slate-300"
                    >
                      {{ truncateUuid(entry.userId) }}
                    </span>
                    <span v-else class="text-slate-400 dark:text-slate-500">—</span>
                  </td>
                  <td class="px-4 py-3 font-mono text-xs text-slate-700 dark:text-slate-300">
                    {{ entry.ipAddress ?? '—' }}
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>

        <!-- Pagination -->
        <div
          v-if="!isLoading && totalPages > 0"
          class="flex items-center justify-between border-t border-slate-200 px-5 py-3 dark:border-slate-700"
        >
          <p class="text-xs text-slate-500 dark:text-slate-400">
            {{ totalElements }} total record{{ totalElements !== 1 ? 's' : '' }}
          </p>
          <div class="flex items-center gap-3">
            <button
              @click="prevPage"
              :disabled="currentPage === 0"
              class="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
            >
              Prev
            </button>
            <span class="text-xs text-slate-600 dark:text-slate-400">
              Page {{ currentPage + 1 }} of {{ totalPages }}
            </span>
            <button
              @click="nextPage"
              :disabled="currentPage >= totalPages - 1"
              class="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
            >
              Next
            </button>
          </div>
        </div>
      </section>
    </div>
  </main>
</template>
