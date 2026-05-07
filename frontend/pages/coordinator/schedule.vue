<script setup lang="ts">
import { ArrowLeft, Calendar, Clock, Edit2, X, AlertTriangle, CheckCircle, XCircle } from 'lucide-vue-next';
import type { ScheduleWindowItem, WindowType } from '~/types/scheduleWindow';

definePageMeta({
  middleware: 'auth',
  roles: ['Coordinator'],
});

const router = useRouter();
const { getAuthToken, fetchScheduleWindows, upsertScheduleWindow, deleteScheduleWindow } = useApiClient();

const windows = ref<ScheduleWindowItem[]>([]);
const isLoading = ref(false);
const error = ref('');
const successMessage = ref('');

// Modal state
const showWindowModal = ref(false);
const modalType = ref<WindowType>('GROUP_CREATION');
const modalOpensAt = ref('');
const modalClosesAt = ref('');
const modalIsEdit = ref(false);
const modalLoading = ref(false);
const modalError = ref('');

// Confirm dialog state
const showConfirmDialog = ref(false);
const confirmTarget = ref<ScheduleWindowItem | null>(null);
const confirmLoading = ref(false);
const confirmError = ref('');

let refreshInterval: ReturnType<typeof setInterval> | null = null;

const WINDOW_LABELS: Record<WindowType, string> = {
  GROUP_CREATION: 'Group Creation',
  ADVISOR_ASSOCIATION: 'Advisor Association',
};

const WINDOW_DESCRIPTIONS: Record<WindowType, string> = {
  GROUP_CREATION: 'Controls when students can create and join groups.',
  ADVISOR_ASSOCIATION: 'Controls when groups can request and receive advisors.',
};

function windowBadgeState(w: ScheduleWindowItem): 'OPEN' | 'CLOSED' | 'NOT_SET' {
  if (w.id === null) return 'NOT_SET';
  if (w.isActive) return 'OPEN';
  return 'CLOSED';
}

function formatDatetime(iso: string | null): string {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(iso));
}

function toLocalDatetimeValue(iso: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

async function loadWindows() {
  isLoading.value = true;
  error.value = '';
  try {
    const token = getAuthToken();
    if (!token) throw new Error('Authentication required. Please log in again.');
    windows.value = await fetchScheduleWindows(token);
  } catch (e: unknown) {
    error.value = (e as { message?: string }).message || 'Failed to load schedule windows.';
  } finally {
    isLoading.value = false;
  }
}

function openWindowModal(type: WindowType, existing: ScheduleWindowItem | null) {
  modalType.value = type;
  modalIsEdit.value = existing !== null && existing.id !== null;
  modalOpensAt.value = toLocalDatetimeValue(existing?.opensAt ?? null);
  modalClosesAt.value = toLocalDatetimeValue(existing?.closesAt ?? null);
  modalError.value = '';
  showWindowModal.value = true;
}

function closeWindowModal() {
  showWindowModal.value = false;
  modalError.value = '';
}

async function submitWindowModal() {
  modalError.value = '';

  if (!modalOpensAt.value || !modalClosesAt.value) {
    modalError.value = 'Both open and close times are required.';
    return;
  }

  const opensDate = new Date(modalOpensAt.value);
  const closesDate = new Date(modalClosesAt.value);

  if (closesDate <= opensDate) {
    modalError.value = 'Close time must be after open time.';
    return;
  }

  modalLoading.value = true;
  try {
    const token = getAuthToken();
    if (!token) throw new Error('Authentication required. Please log in again.');

    await upsertScheduleWindow(
      {
        type: modalType.value,
        opensAt: opensDate.toISOString(),
        closesAt: closesDate.toISOString(),
      },
      token
    );

    successMessage.value = `${WINDOW_LABELS[modalType.value]} window ${modalIsEdit.value ? 'updated' : 'set'} successfully.`;
    setTimeout(() => { successMessage.value = ''; }, 3000);
    closeWindowModal();
    await loadWindows();
  } catch (e: unknown) {
    modalError.value = (e as { message?: string }).message || 'Failed to save schedule window.';
  } finally {
    modalLoading.value = false;
  }
}

function openConfirmDialog(w: ScheduleWindowItem) {
  confirmTarget.value = w;
  confirmError.value = '';
  showConfirmDialog.value = true;
}

function closeConfirmDialog() {
  showConfirmDialog.value = false;
  confirmTarget.value = null;
  confirmError.value = '';
}

async function confirmDelete() {
  if (!confirmTarget.value?.id) return;
  confirmLoading.value = true;
  confirmError.value = '';
  try {
    const token = getAuthToken();
    if (!token) throw new Error('Authentication required. Please log in again.');
    await deleteScheduleWindow(confirmTarget.value.id, token);
    successMessage.value = `${WINDOW_LABELS[confirmTarget.value.type]} window closed successfully.`;
    setTimeout(() => { successMessage.value = ''; }, 3000);
    closeConfirmDialog();
    await loadWindows();
  } catch (e: unknown) {
    confirmError.value = (e as { message?: string }).message || 'Failed to close schedule window.';
  } finally {
    confirmLoading.value = false;
  }
}

onMounted(() => {
  loadWindows();
  refreshInterval = setInterval(loadWindows, 60_000);
});

onUnmounted(() => {
  if (refreshInterval !== null) {
    clearInterval(refreshInterval);
  }
});
</script>

<template>
  <div class="min-h-screen bg-slate-50 dark:bg-slate-950 p-6">
    <!-- Header -->
    <div class="mb-6 flex items-center gap-3">
      <button
        class="flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-600 shadow-sm transition hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400 dark:hover:bg-slate-800"
        @click="router.back()"
      >
        <ArrowLeft class="h-4 w-4" />
        Back
      </button>
      <div>
        <h1 class="text-xl font-semibold text-slate-900 dark:text-white">Schedule Window Management</h1>
        <p class="text-sm text-slate-500 dark:text-slate-400">Control when students can create groups and request advisors.</p>
      </div>
    </div>

    <!-- Success message -->
    <div
      v-if="successMessage"
      class="mb-4 flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 dark:border-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-300"
    >
      <CheckCircle class="h-4 w-4 shrink-0" />
      {{ successMessage }}
    </div>

    <!-- Error message -->
    <div
      v-if="error"
      class="mb-4 flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 dark:border-red-800 dark:bg-red-900/30 dark:text-red-300"
    >
      <AlertTriangle class="h-4 w-4 shrink-0" />
      {{ error }}
    </div>

    <!-- Loading skeleton -->
    <div v-if="isLoading && windows.length === 0" class="space-y-4">
      <div
        v-for="i in 2"
        :key="i"
        class="h-32 animate-pulse rounded-2xl border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900"
      />
    </div>

    <!-- Window cards -->
    <div v-else class="space-y-4">
      <div
        v-for="w in windows"
        :key="w.type"
        class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-900"
      >
        <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <!-- Left: info -->
          <div class="flex-1">
            <div class="mb-1 flex items-center gap-3">
              <h2 class="text-base font-semibold text-slate-900 dark:text-white">
                {{ WINDOW_LABELS[w.type] }}
              </h2>

              <!-- Badge -->
              <span
                v-if="windowBadgeState(w) === 'OPEN'"
                class="inline-flex items-center rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-0.5 text-xs font-medium text-emerald-700 dark:border-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400"
              >
                <span class="mr-1.5 h-1.5 w-1.5 rounded-full bg-emerald-500" />
                OPEN
              </span>
              <span
                v-else-if="windowBadgeState(w) === 'CLOSED'"
                class="inline-flex items-center rounded-full border border-amber-200 bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-700 dark:border-amber-700 dark:bg-amber-900/30 dark:text-amber-400"
              >
                <span class="mr-1.5 h-1.5 w-1.5 rounded-full bg-amber-500" />
                CLOSED
              </span>
              <span
                v-else
                class="inline-flex items-center rounded-full border border-slate-200 bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-400"
              >
                NOT SET
              </span>
            </div>

            <p class="mb-3 text-sm text-slate-500 dark:text-slate-400">
              {{ WINDOW_DESCRIPTIONS[w.type] }}
            </p>

            <div v-if="w.id !== null" class="flex flex-wrap gap-4 text-sm text-slate-600 dark:text-slate-400">
              <span class="flex items-center gap-1.5">
                <Clock class="h-4 w-4 text-slate-400" />
                Opens: <span class="font-medium text-slate-800 dark:text-slate-200">{{ formatDatetime(w.opensAt) }}</span>
              </span>
              <span class="flex items-center gap-1.5">
                <Calendar class="h-4 w-4 text-slate-400" />
                Closes: <span class="font-medium text-slate-800 dark:text-slate-200">{{ formatDatetime(w.closesAt) }}</span>
              </span>
            </div>
            <p v-else class="text-sm text-slate-400 dark:text-slate-500 italic">No window has been set for this type.</p>
          </div>

          <!-- Right: actions -->
          <div class="flex shrink-0 gap-2">
            <button
              class="flex items-center gap-1.5 rounded-lg border border-blue-200 bg-blue-50 px-3 py-1.5 text-sm font-medium text-blue-700 transition hover:bg-blue-100 dark:border-blue-700 dark:bg-blue-900/30 dark:text-blue-400 dark:hover:bg-blue-900/50"
              @click="openWindowModal(w.type, w.id !== null ? w : null)"
            >
              <Edit2 class="h-4 w-4" />
              {{ w.id !== null ? 'Edit Window' : 'Open Window' }}
            </button>

            <button
              v-if="w.id !== null"
              class="flex items-center gap-1.5 rounded-lg border border-red-200 bg-red-50 px-3 py-1.5 text-sm font-medium text-red-700 transition hover:bg-red-100 dark:border-red-700 dark:bg-red-900/30 dark:text-red-400 dark:hover:bg-red-900/50"
              @click="openConfirmDialog(w)"
            >
              <XCircle class="h-4 w-4" />
              Close
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Window upsert modal -->
    <div
      v-if="showWindowModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
    >
      <div class="w-full max-w-md rounded-2xl border border-slate-200 bg-white shadow-xl dark:border-slate-700 dark:bg-slate-900">
        <div class="flex items-center justify-between border-b border-slate-200 px-6 py-4 dark:border-slate-700">
          <h2 id="modal-title" class="text-base font-semibold text-slate-900 dark:text-white">
            {{ modalIsEdit ? 'Edit' : 'Set' }} {{ WINDOW_LABELS[modalType] }} Window
          </h2>
          <button
            class="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-800"
            @click="closeWindowModal"
          >
            <X class="h-5 w-5" />
          </button>
        </div>

        <div class="space-y-4 px-6 py-5">
          <div v-if="modalError" class="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-300">
            <AlertTriangle class="h-4 w-4 shrink-0" />
            {{ modalError }}
          </div>

          <div>
            <label for="opensAt" class="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">
              Opens At
            </label>
            <input
              id="opensAt"
              v-model="modalOpensAt"
              type="datetime-local"
              class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 dark:border-slate-600 dark:bg-slate-800 dark:text-white dark:focus:border-blue-400"
            />
          </div>

          <div>
            <label for="closesAt" class="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">
              Closes At
            </label>
            <input
              id="closesAt"
              v-model="modalClosesAt"
              type="datetime-local"
              class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 dark:border-slate-600 dark:bg-slate-800 dark:text-white dark:focus:border-blue-400"
            />
          </div>
        </div>

        <div class="flex justify-end gap-2 border-t border-slate-200 px-6 py-4 dark:border-slate-700">
          <button
            class="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
            @click="closeWindowModal"
          >
            Cancel
          </button>
          <button
            :disabled="modalLoading"
            class="flex items-center gap-1.5 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
            @click="submitWindowModal"
          >
            <span v-if="modalLoading" class="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
            {{ modalIsEdit ? 'Update' : 'Set Window' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Confirm close dialog -->
    <div
      v-if="showConfirmDialog"
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-title"
    >
      <div class="w-full max-w-sm rounded-2xl border border-slate-200 bg-white shadow-xl dark:border-slate-700 dark:bg-slate-900">
        <div class="px-6 py-5">
          <div class="mb-3 flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-full bg-red-100 dark:bg-red-900/30">
              <AlertTriangle class="h-5 w-5 text-red-600 dark:text-red-400" />
            </div>
            <h2 id="confirm-title" class="text-base font-semibold text-slate-900 dark:text-white">Close Window</h2>
          </div>
          <p class="text-sm text-slate-600 dark:text-slate-400">
            Are you sure you want to close the
            <span class="font-medium text-slate-900 dark:text-white">{{ confirmTarget ? WINDOW_LABELS[confirmTarget.type] : '' }}</span>
            window? Students will no longer be able to perform this action.
          </p>

          <div v-if="confirmError" class="mt-3 flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-300">
            <AlertTriangle class="h-4 w-4 shrink-0" />
            {{ confirmError }}
          </div>
        </div>

        <div class="flex justify-end gap-2 border-t border-slate-200 px-6 py-4 dark:border-slate-700">
          <button
            class="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
            @click="closeConfirmDialog"
          >
            Cancel
          </button>
          <button
            :disabled="confirmLoading"
            class="flex items-center gap-1.5 rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-60"
            @click="confirmDelete"
          >
            <span v-if="confirmLoading" class="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
            Close Window
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
