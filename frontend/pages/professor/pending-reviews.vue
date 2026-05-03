<script setup lang="ts">
import {
  ArrowLeft,
  AlertCircle,
  Loader2,
  FileText,
  Clock,
  MessageSquare,
  Eye,
  RefreshCw,
  CheckCircle2,
  InboxIcon,
} from "lucide-vue-next";
import type { ProfessorCommittee } from "~/types/committee";
import type { CommitteeSubmission } from "~/types/committee";

definePageMeta({
  middleware: "auth",
  roles: ["Professor"],
});

const { getAuthToken, fetchProfessorCommittees, fetchCommitteeSubmissions } = useApiClient();

// A submission row enriched with committee context
interface SubmissionRow extends CommitteeSubmission {
  committeeId: string;
  committeeName: string;
}

const committees = ref<ProfessorCommittee[]>([]);
const rows = ref<SubmissionRow[]>([]);
const loading = ref(true);
const loadError = ref<string | null>(null);

// Filters
const search = ref("");
const filterCommittee = ref<string>("all");

const filteredRows = computed(() => {
  let result = rows.value;
  if (filterCommittee.value !== "all") {
    result = result.filter((r) => r.committeeId === filterCommittee.value);
  }
  if (search.value.trim()) {
    const q = search.value.trim().toLowerCase();
    result = result.filter(
      (r) =>
        r.groupName.toLowerCase().includes(q) ||
        r.deliverableName.toLowerCase().includes(q) ||
        r.committeeName.toLowerCase().includes(q)
    );
  }
  return result;
});

function formatDate(dateStr: string): string {
  try {
    return new Intl.DateTimeFormat("tr-TR", {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new Date(dateStr));
  } catch {
    return dateStr;
  }
}

function timeAgo(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins} dk önce`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} sa önce`;
  const days = Math.floor(hours / 24);
  return `${days} gün önce`;
}

async function load() {
  loading.value = true;
  loadError.value = null;
  rows.value = [];

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Kimlik doğrulaması gerekiyor.");

    const committeeList = await fetchProfessorCommittees(token);
    committees.value = committeeList;

    if (committeeList.length === 0) {
      loading.value = false;
      return;
    }

    // Fetch submissions for all committees in parallel
    const results = await Promise.allSettled(
      committeeList.map(async (c) => {
        const subs = await fetchCommitteeSubmissions(c.committeeId, token);
        return subs.map((s) => ({
          ...s,
          committeeId: c.committeeId,
          committeeName: c.committeeName,
        }));
      })
    );

    const allRows: SubmissionRow[] = [];
    for (const result of results) {
      if (result.status === "fulfilled") {
        allRows.push(...result.value);
      }
    }

    // Sort by submittedAt desc (most recent first)
    allRows.sort(
      (a, b) => new Date(b.submittedAt).getTime() - new Date(a.submittedAt).getTime()
    );
    rows.value = allRows;
  } catch (err: unknown) {
    loadError.value =
      err && typeof err === "object" && "message" in err
        ? String((err as { message: string }).message)
        : "Veriler yüklenemedi.";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <main
    class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8"
  >
    <div class="mx-auto w-full max-w-6xl space-y-6">

      <!-- Header -->
      <header
        class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg"
      >
        <div class="flex items-center justify-between gap-4 flex-wrap">
          <div>
            <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
              Pending Reviews
            </h1>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Committee submissions waiting for your review.
            </p>
          </div>
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-600 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
              :disabled="loading"
              @click="load"
            >
              <RefreshCw class="h-4 w-4" :class="loading ? 'animate-spin' : ''" />
              Yenile
            </button>
            <NuxtLink
              to="/professor/dashboard"
              class="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
            >
              <ArrowLeft class="h-4 w-4" />
              Back
            </NuxtLink>
          </div>
        </div>
      </header>

      <!-- Loading -->
      <div
        v-if="loading"
        class="flex items-center justify-center rounded-2xl border border-slate-200 bg-white p-16 dark:border-slate-700 dark:bg-slate-800"
      >
        <Loader2 class="h-6 w-6 animate-spin text-blue-500" />
        <span class="ml-3 text-sm text-slate-500 dark:text-slate-400">Loading submissions…</span>
      </div>

      <!-- Error -->
      <div
        v-else-if="loadError"
        class="flex items-start gap-3 rounded-2xl border border-red-300 bg-red-50 p-6 text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300"
      >
        <AlertCircle class="mt-0.5 h-5 w-5 shrink-0" />
        <div>
          <p class="font-medium">Yükleme hatası</p>
          <p class="mt-1 text-sm">{{ loadError }}</p>
          <button class="mt-3 text-sm underline" @click="load">Tekrar dene</button>
        </div>
      </div>

      <template v-else>

        <!-- Filters row -->
        <div
          v-if="rows.length > 0"
          class="flex flex-wrap items-center gap-3"
        >
          <!-- Search -->
          <div class="relative flex-1 min-w-48">
            <FileText class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
            <input
              v-model="search"
              type="text"
              placeholder="Grup, teslim veya komite ara…"
              class="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-4 text-sm text-slate-900 placeholder-slate-400 transition focus:border-blue-500 focus:outline-none dark:border-slate-600 dark:bg-slate-800 dark:text-white dark:placeholder-slate-500"
            />
          </div>

          <!-- Committee filter -->
          <select
            v-model="filterCommittee"
            class="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 focus:border-blue-500 focus:outline-none dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200"
          >
            <option value="all">Tüm Komiteler</option>
            <option
              v-for="c in committees"
              :key="c.committeeId"
              :value="c.committeeId"
            >
              {{ c.committeeName }}
            </option>
          </select>

          <!-- Count badge -->
          <span class="ml-auto text-sm text-slate-500 dark:text-slate-400 whitespace-nowrap">
            <span class="font-semibold text-slate-700 dark:text-slate-200">{{ filteredRows.length }}</span>
            /{{ rows.length }} teslim
          </span>
        </div>

        <!-- Empty state: no submissions at all -->
        <div
          v-if="rows.length === 0"
          class="flex flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 bg-white py-20 dark:border-slate-600 dark:bg-slate-800"
        >
          <InboxIcon class="h-12 w-12 text-slate-300 dark:text-slate-600" />
          <p class="mt-4 text-sm font-medium text-slate-500 dark:text-slate-400">
            {{ committees.length === 0 ? 'Henüz bir komiteye atanmadınız.' : 'Bekleyen teslim yok.' }}
          </p>
        </div>

        <!-- Empty search result -->
        <div
          v-else-if="filteredRows.length === 0"
          class="flex flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 bg-white py-12 dark:border-slate-600 dark:bg-slate-800"
        >
          <p class="text-sm text-slate-400 dark:text-slate-500">Aramanızla eşleşen teslim bulunamadı.</p>
          <button class="mt-2 text-sm text-blue-500 underline" @click="search = ''; filterCommittee = 'all'">
            Filtreleri temizle
          </button>
        </div>

        <!-- Submissions table -->
        <div
          v-else
          class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800"
        >
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-700/50">
                <th class="px-5 py-3.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  Grup
                </th>
                <th class="px-5 py-3.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  Teslim Adı
                </th>
                <th class="px-5 py-3.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400 hidden md:table-cell">
                  Komite
                </th>
                <th class="px-5 py-3.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400 hidden lg:table-cell">
                  Teslim Tarihi
                </th>
                <th class="px-5 py-3.5 text-center text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  Yorum
                </th>
                <th class="px-5 py-3.5 text-right text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  İşlem
                </th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100 dark:divide-slate-700/60">
              <tr
                v-for="row in filteredRows"
                :key="row.submissionId"
                class="group transition-colors hover:bg-slate-50 dark:hover:bg-slate-700/40"
              >
                <!-- Group -->
                <td class="px-5 py-4">
                  <div class="flex items-center gap-2.5">
                    <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300 flex-shrink-0">
                      <span class="text-xs font-bold">{{ row.groupName.charAt(0).toUpperCase() }}</span>
                    </div>
                    <span class="font-medium text-slate-900 dark:text-white">{{ row.groupName }}</span>
                  </div>
                </td>

                <!-- Deliverable name -->
                <td class="px-5 py-4">
                  <div class="flex items-center gap-1.5">
                    <FileText class="h-3.5 w-3.5 text-slate-400 flex-shrink-0" />
                    <span class="text-slate-700 dark:text-slate-200">{{ row.deliverableName }}</span>
                  </div>
                </td>

                <!-- Committee (hidden on small screens) -->
                <td class="px-5 py-4 hidden md:table-cell">
                  <span class="inline-flex items-center rounded-full bg-cyan-100 px-2.5 py-0.5 text-xs font-medium text-cyan-700 dark:bg-cyan-900/40 dark:text-cyan-300">
                    {{ row.committeeName }}
                  </span>
                </td>

                <!-- Submitted at (hidden on medium screens) -->
                <td class="px-5 py-4 hidden lg:table-cell">
                  <div class="flex flex-col gap-0.5">
                    <span class="text-slate-700 dark:text-slate-300">{{ formatDate(row.submittedAt) }}</span>
                    <span class="text-xs text-slate-400 dark:text-slate-500 flex items-center gap-1">
                      <Clock class="h-3 w-3" />
                      {{ timeAgo(row.submittedAt) }}
                    </span>
                  </div>
                </td>

                <!-- Comment count -->
                <td class="px-5 py-4 text-center">
                  <div
                    class="inline-flex items-center gap-1 text-xs"
                    :class="row.commentCount > 0
                      ? 'text-indigo-600 dark:text-indigo-400'
                      : 'text-slate-400 dark:text-slate-500'"
                  >
                    <MessageSquare class="h-3.5 w-3.5" />
                    {{ row.commentCount }}
                  </div>
                </td>

                <!-- Action -->
                <td class="px-5 py-4 text-right">
                  <NuxtLink
                    :to="`/professor/submission-review/${row.submissionId}`"
                    class="inline-flex items-center gap-1.5 rounded-lg border border-blue-600 bg-blue-600 px-3 py-1.5 text-xs font-medium text-white shadow-sm transition hover:bg-blue-700 hover:border-blue-700"
                  >
                    <Eye class="h-3.5 w-3.5" />
                    İncele
                  </NuxtLink>
                </td>
              </tr>
            </tbody>
          </table>

          <!-- Table footer: summary -->
          <div class="border-t border-slate-100 dark:border-slate-700 px-5 py-3 flex items-center gap-2 bg-slate-50 dark:bg-slate-800/60">
            <CheckCircle2 class="h-4 w-4 text-slate-400" />
            <p class="text-xs text-slate-500 dark:text-slate-400">
              Toplam <span class="font-semibold text-slate-700 dark:text-slate-200">{{ rows.length }}</span> teslim,
              <span class="font-semibold text-slate-700 dark:text-slate-200">{{ committees.length }}</span> komitede.
            </p>
          </div>
        </div>

      </template>
    </div>
  </main>
</template>
