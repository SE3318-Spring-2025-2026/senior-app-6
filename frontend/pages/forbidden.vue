<template>
  <main class="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-50 via-white to-slate-100 px-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950">
    <div class="w-full max-w-md text-center">
      <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full border border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-950/50">
        <ShieldX class="h-8 w-8 text-red-600 dark:text-red-400" />
      </div>
      <h1 class="mt-6 text-3xl font-semibold tracking-tight text-slate-900 dark:text-white">
        403 – Forbidden
      </h1>
      <p class="mt-3 text-sm text-slate-600 dark:text-slate-400">
        You do not have permission to access this page.
      </p>
      <div class="mt-8 flex justify-center gap-3">
        <button
          @click="goBack"
          class="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
        >
          Go Back
        </button>
        <NuxtLink
          to="/auth/login"
          class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
        >
          Sign In
        </NuxtLink>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ShieldX } from "lucide-vue-next";

const router = useRouter();

function goBack() {
  if (window.history.length > 1) {
    router.back();
  } else {
    router.push("/auth/login");
  }
}
</script>
