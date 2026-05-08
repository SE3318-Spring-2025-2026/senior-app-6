<script setup lang="ts">
import { Home, ArrowLeft, LayoutDashboard } from "lucide-vue-next";

const props = defineProps<{ error: { statusCode: number; statusMessage?: string; message?: string } }>();

const is404 = computed(() => props.error.statusCode === 404);

function goHome() {
  clearError({ redirect: "/" });
}

function goBack() {
  clearError();
  history.back();
}
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 flex items-center justify-center p-4">
    <div class="mx-auto w-full max-w-lg text-center">
      <div class="mb-6 inline-flex items-center justify-center rounded-2xl border border-slate-200 bg-white/90 p-4 shadow-sm backdrop-blur dark:border-slate-700 dark:bg-slate-800/90">
        <LayoutDashboard class="h-8 w-8 text-blue-600 dark:text-blue-400" />
      </div>

      <p class="text-8xl font-bold tracking-tighter text-slate-900 dark:text-white">
        {{ error.statusCode }}
      </p>

      <h1 class="mt-4 text-2xl font-semibold tracking-tight text-slate-800 dark:text-slate-100">
        {{ is404 ? "Page not found" : "Something went wrong" }}
      </h1>

      <p class="mt-2 text-sm text-slate-500 dark:text-slate-400">
        {{ is404
          ? "The page you're looking for doesn't exist or you don't have permission to view it."
          : (error.statusMessage || "An unexpected error occurred. Please try again.") }}
      </p>

      <div class="mt-8 flex flex-col items-center gap-3 sm:flex-row sm:justify-center">
        <button
          type="button"
          class="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-5 py-2.5 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700"
          @click="goBack"
        >
          <ArrowLeft class="h-4 w-4" />
          Go back
        </button>
        <button
          type="button"
          class="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-medium text-white shadow-sm transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
          @click="goHome"
        >
          <Home class="h-4 w-4" />
          Back to home
        </button>
      </div>
    </div>
  </main>
</template>
