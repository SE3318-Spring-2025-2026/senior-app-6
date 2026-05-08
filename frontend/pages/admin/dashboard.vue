<script setup lang="ts">
import {
  LogOut, UserPlus, Shield, KeyRound, ClipboardList,
  Users, Activity, CheckCircle2, XCircle, Loader2, AlertCircle, ArrowRight,
} from "lucide-vue-next";
import { useAuthStore } from "~/stores/auth";
import type { AuditLogEntry } from "~/types/audit-log";

definePageMeta({
  middleware: "auth",
  roles: ["Admin"],
});

const router = useRouter();
const authStore = useAuthStore();
const { getAuthToken, fetchAuditLogs } = useApiClient();

const recentLogs = ref<AuditLogEntry[]>([]);
const logsLoading = ref(true);
const logsError = ref<string | null>(null);

function handleLogout() {
  authStore.logout();
  router.push("/auth/login");
}

function formatTime(dateStr: string): string {
  try {
    return new Intl.DateTimeFormat("tr-TR", {
      dateStyle: "short",
      timeStyle: "short",
    }).format(new Date(dateStr));
  } catch {
    return dateStr;
  }
}

function categoryColor(category: string): string {
  const map: Record<string, string> = {
    AUTH: "text-blue-600 bg-blue-50 dark:text-blue-300 dark:bg-blue-900/30",
    GROUP: "text-violet-600 bg-violet-50 dark:text-violet-300 dark:bg-violet-900/30",
    SUBMISSION: "text-emerald-600 bg-emerald-50 dark:text-emerald-300 dark:bg-emerald-900/30",
    GRADE: "text-fuchsia-600 bg-fuchsia-50 dark:text-fuchsia-300 dark:bg-fuchsia-900/30",
    ADMIN: "text-amber-600 bg-amber-50 dark:text-amber-300 dark:bg-amber-900/30",
  };
  return map[category] ?? "text-slate-600 bg-slate-100 dark:text-slate-300 dark:bg-slate-700";
}

onMounted(async () => {
  try {
    const token = getAuthToken();
    if (!token) return;
    const resp = await fetchAuditLogs({ size: 10, page: 0 }, token);
    recentLogs.value = resp.content;
  } catch {
    logsError.value = "Failed to load recent activity.";
  } finally {
    logsLoading.value = false;
  }
});
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-5xl space-y-6">

      <!-- Header -->
      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
              Admin Dashboard
            </h1>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Manage staff accounts, system settings, and monitor activity.
            </p>
          </div>
          <button
            @click="handleLogout"
            class="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
          >
            <LogOut class="mr-2 inline h-4 w-4" />
            Sign Out
          </button>
        </div>
      </header>

      <!-- Quick Actions (3×2) -->
      <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">

        <!-- Row 1 -->
        <NuxtLink
          to="/admin/register-professor"
          class="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-blue-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-blue-600"
        >
          <UserPlus class="h-8 w-8 text-blue-600 dark:text-blue-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white group-hover:text-blue-600 dark:group-hover:text-blue-400">
            Register Professor
          </h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Add a new professor or coordinator to the system.
          </p>
        </NuxtLink>

        <NuxtLink
          to="/admin/audit-logs"
          class="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-indigo-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-indigo-600"
        >
          <ClipboardList class="h-8 w-8 text-indigo-600 dark:text-indigo-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400">
            Audit Logs
          </h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Browse full system activity records and filter by category.
          </p>
        </NuxtLink>

        <NuxtLink
          to="/admin/llm-config"
          class="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-purple-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-purple-600"
        >
          <KeyRound class="h-8 w-8 text-purple-600 dark:text-purple-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white group-hover:text-purple-600 dark:group-hover:text-purple-400">
            LLM Configuration
          </h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Set the AI API key for sprint validation.
          </p>
        </NuxtLink>

        <!-- Row 2 -->
        <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
          <Shield class="h-8 w-8 text-emerald-600 dark:text-emerald-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white">System Status</h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            All systems operational.
          </p>
        </div>

        <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
          <Activity class="h-8 w-8 text-amber-600 dark:text-amber-400" />
          <h3 class="mt-3 font-semibold text-slate-900 dark:text-white">Recent Activity</h3>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            <template v-if="logsLoading">Loading…</template>
            <template v-else-if="recentLogs.length > 0">
              {{ recentLogs.filter(l => l.outcome === 'SUCCESS').length }} of {{ recentLogs.length }} recent events succeeded.
            </template>
            <template v-else>No recent events.</template>
          </p>
        </div>

        <!-- Admin info card -->
        <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800 flex flex-col justify-between">
          <div class="flex items-center gap-3">
            <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-rose-100 dark:bg-rose-900/40">
              <span class="text-sm font-bold text-rose-700 dark:text-rose-300">
                {{ (authStore.userInfo?.mail ?? 'A').charAt(0).toUpperCase() }}
              </span>
            </div>
            <div class="min-w-0">
              <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 dark:text-slate-500">Signed in as</p>
              <p class="truncate text-sm font-medium text-slate-700 dark:text-slate-200">
                {{ authStore.userInfo?.mail ?? '—' }}
              </p>
            </div>
          </div>
          <p class="mt-4 text-sm text-slate-600 dark:text-slate-400">
            Full system access.
          </p>
        </div>

      </div>

      <!-- Latest Activity -->
      <section class="rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800 overflow-hidden">
        <div class="flex items-center gap-2 border-b border-slate-100 dark:border-slate-700 px-6 py-4 bg-slate-50 dark:bg-slate-800/60">
          <h2 class="text-sm font-semibold text-slate-700 dark:text-slate-200">Latest Activity</h2>
          <NuxtLink
            to="/admin/audit-logs"
            class="ml-auto inline-flex items-center gap-1 text-xs text-indigo-500 transition hover:text-indigo-700 dark:hover:text-indigo-300"
          >
            View all
            <ArrowRight class="h-3 w-3" />
          </NuxtLink>
        </div>

        <div v-if="logsLoading" class="flex items-center justify-center px-6 py-10">
          <Loader2 class="h-5 w-5 animate-spin text-slate-400" />
          <span class="ml-2 text-sm text-slate-500 dark:text-slate-400">Loading…</span>
        </div>

        <div v-else-if="logsError" class="flex items-center gap-3 px-6 py-5 text-sm text-red-600 dark:text-red-400">
          <AlertCircle class="h-4 w-4 flex-shrink-0" />
          {{ logsError }}
        </div>

        <div v-else-if="recentLogs.length === 0" class="px-6 py-10 text-center text-sm text-slate-400 dark:text-slate-500">
          No activity recorded yet.
        </div>

        <ul v-else class="divide-y divide-slate-100 dark:divide-slate-700/60">
          <li
            v-for="log in recentLogs"
            :key="log.id"
            class="flex items-center gap-3 px-6 py-3"
          >
            <!-- Outcome icon -->
            <CheckCircle2
              v-if="log.outcome === 'SUCCESS'"
              class="h-4 w-4 shrink-0 text-emerald-500"
            />
            <XCircle v-else class="h-4 w-4 shrink-0 text-red-400" />

            <!-- Category badge -->
            <span
              class="shrink-0 rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide"
              :class="categoryColor(log.category)"
            >
              {{ log.category }}
            </span>

            <!-- Action -->
            <span class="flex-1 truncate text-sm text-slate-700 dark:text-slate-200">
              {{ log.action }}
            </span>

            <!-- User type -->
            <span class="hidden shrink-0 text-xs text-slate-400 dark:text-slate-500 sm:block">
              {{ log.userType }}
            </span>

            <!-- Time -->
            <span class="shrink-0 text-xs text-slate-400 dark:text-slate-500">
              {{ formatTime(log.occurredAt) }}
            </span>
          </li>
        </ul>
      </section>

    </div>
  </main>
</template>
