<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ArrowLeft, Eye, EyeOff, KeyRound, Loader2, Save } from "lucide-vue-next";
import type { LlmConfigResponse } from "~/types/api";

definePageMeta({
  middleware: "auth",
  roles: ["Admin"],
});

const { getAuthToken, fetchLlmConfig, updateLlmKey } = useApiClient();

const isLoading = ref(true);
const config = ref<LlmConfigResponse | null>(null);
const pageError = ref<string | null>(null);

const apiKeyInput = ref("");
const showKey = ref(false);
const isSaving = ref(false);
const saveError = ref<string | null>(null);
const saveSuccess = ref<string | null>(null);

async function loadConfig() {
  isLoading.value = true;
  pageError.value = null;
  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required");
    config.value = await fetchLlmConfig(token);
  } catch (err: unknown) {
    pageError.value =
      err && typeof err === "object" && "message" in err
        ? String(err.message)
        : "Failed to load LLM configuration.";
  } finally {
    isLoading.value = false;
  }
}

async function handleSubmit() {
  if (!apiKeyInput.value.trim()) return;

  isSaving.value = true;
  saveError.value = null;
  saveSuccess.value = null;

  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required");
    await updateLlmKey(apiKeyInput.value.trim(), token);
    apiKeyInput.value = "";
    showKey.value = false;
    saveSuccess.value = "API key saved successfully.";
    await loadConfig();
  } catch (err: unknown) {
    saveError.value =
      err && typeof err === "object" && "message" in err
        ? String(err.message)
        : "Failed to save API key.";
  } finally {
    isSaving.value = false;
  }
}

onMounted(loadConfig);
</script>

<template>
  <main
    class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8"
  >
    <div class="mx-auto w-full max-w-2xl space-y-6">
      <NuxtLink
        to="/admin/dashboard"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to Dashboard
      </NuxtLink>

      <header
        class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90"
      >
        <div class="flex items-center gap-3">
          <KeyRound class="h-7 w-7 text-purple-600 dark:text-purple-400" />
          <div>
            <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white">
              LLM API Key
            </h1>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
              Set the Google AI Studio API key used for AI-assisted sprint validation.
            </p>
          </div>
        </div>

        <div class="mt-5">
          <div v-if="isLoading" class="flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
            <Loader2 class="h-4 w-4 animate-spin" />
            Loading status...
          </div>
          <div v-else-if="pageError" class="text-sm text-red-600 dark:text-red-400">{{ pageError }}</div>
          <div v-else-if="config" class="flex items-center gap-3">
            <span
              v-if="config.configured"
              class="inline-flex items-center gap-1.5 rounded-full bg-emerald-100 px-3 py-1 text-xs font-medium text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300"
            >
              Configured
            </span>
            <span
              v-else
              class="inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-3 py-1 text-xs font-medium text-amber-700 dark:bg-amber-900/40 dark:text-amber-300"
            >
              Not configured
            </span>
            <span v-if="config.maskedKey" class="font-mono text-sm text-slate-700 dark:text-slate-300">
              {{ config.maskedKey }}
            </span>
          </div>
        </div>
      </header>

      <section
        class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800"
      >
        <h2 class="text-base font-semibold text-slate-900 dark:text-white">
          {{ config?.configured ? "Rotate API Key" : "Set API Key" }}
        </h2>
        <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Obtain your key from
          <a
            href="https://aistudio.google.com/apikey"
            target="_blank"
            rel="noopener noreferrer"
            class="text-blue-600 underline hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300"
          >Google AI Studio</a>.
          Keys start with <code class="rounded bg-slate-100 px-1 dark:bg-slate-700">AIza</code>.
        </p>

        <form class="mt-4 space-y-4" @submit.prevent="handleSubmit">
          <div>
            <label class="block text-sm font-medium text-slate-700 dark:text-slate-300">
              Google AI Studio API Key
            </label>
            <div class="relative mt-1">
              <input
                v-model="apiKeyInput"
                :type="showKey ? 'text' : 'password'"
                placeholder="AIza..."
                autocomplete="off"
                class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 pr-10 text-sm text-slate-900 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:ring-blue-900"
              />
              <button
                type="button"
                @click="showKey = !showKey"
                class="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300"
              >
                <Eye v-if="!showKey" class="h-4 w-4" />
                <EyeOff v-else class="h-4 w-4" />
              </button>
            </div>
          </div>

          <div>
            <button
              type="submit"
              :disabled="isSaving || !apiKeyInput.trim()"
              class="inline-flex items-center gap-2 rounded-lg bg-purple-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-purple-700 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <Loader2 v-if="isSaving" class="h-4 w-4 animate-spin" />
              <Save v-else class="h-4 w-4" />
              Save API Key
            </button>
          </div>

          <p v-if="saveError" class="text-sm text-red-600 dark:text-red-400">{{ saveError }}</p>
          <p v-else-if="saveSuccess" class="text-sm text-emerald-600 dark:text-emerald-400">{{ saveSuccess }}</p>
        </form>
      </section>
    </div>
  </main>
</template>
