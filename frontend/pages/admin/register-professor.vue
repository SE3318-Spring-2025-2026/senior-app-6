<script setup lang="ts">
	import { z } from "zod";
	import { ArrowLeft, CheckCircle, Copy, Mail, UserPlus } from "lucide-vue-next";
	import { useAuthStore } from "~/stores/auth";

	definePageMeta({
		middleware: "auth",
		roles: ["Admin"],
	});

	const authStore = useAuthStore();
	const { registerProfessor, getAuthToken } = useApiClient();

	const emailSchema = z.object({
		mail: z.string().email("Please enter a valid email address."),
	});

	const email = ref("");
	const submitting = ref(false);
	const errors = ref<{ mail?: string }>({});
	const submitError = ref("");
	const resetToken = ref("");
	const resetLink = computed(() => {
		if (!resetToken.value) return "";
		const base = window.location.origin;
		return `${base}/auth/reset-password?token=${resetToken.value}`;
	});
	const copied = ref(false);

	async function handleSubmit() {
		errors.value = {};
		submitError.value = "";
		resetToken.value = "";

		const result = emailSchema.safeParse({ mail: email.value });

		if (!result.success) {
			const fieldErrors = result.error.flatten().fieldErrors;
			errors.value = { mail: fieldErrors.mail?.[0] };
			return;
		}

		submitting.value = true;
		try {
			const token = getAuthToken();
			const response = await registerProfessor(result.data.mail, token ?? undefined);
			resetToken.value = response.resetToken;
		} catch (error: unknown) {
			const errorMsg =
				error && typeof error === "object" && "message" in error
					? String(error.message)
					: "Failed to register professor. Please try again.";
			submitError.value = errorMsg;
		} finally {
			submitting.value = false;
		}
	}

	async function copyLink() {
		try {
			await navigator.clipboard.writeText(resetLink.value);
			copied.value = true;
			setTimeout(() => (copied.value = false), 2000);
		} catch {
			// fallback
		}
	}

	function registerAnother() {
		email.value = "";
		resetToken.value = "";
		submitError.value = "";
		errors.value = {};
	}
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-lg space-y-6">
      <!-- Back link -->
      <NuxtLink
        to="/admin/dashboard"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to Dashboard
      </NuxtLink>

      <!-- Card -->
      <section class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-xl backdrop-blur dark:border-slate-700 dark:bg-slate-900/90 dark:shadow-lg md:p-8">
        <!-- Header -->
        <header class="mb-6 space-y-2 text-center">
          <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-blue-50 text-blue-600 dark:bg-blue-950/50 dark:text-blue-400">
            <UserPlus class="h-6 w-6" aria-hidden="true" />
          </div>
          <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white">
            Register Professor
          </h1>
          <p class="text-sm text-slate-600 dark:text-slate-400">
            Add a new professor to the system. A password setup link will be generated.
          </p>
        </header>

        <!-- Success State -->
        <div v-if="resetToken" class="space-y-4">
          <div class="flex flex-col items-center gap-3 text-center">
            <div class="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-600 dark:bg-emerald-950/50 dark:text-emerald-400">
              <CheckCircle class="h-6 w-6" />
            </div>
            <h2 class="text-lg font-semibold text-slate-900 dark:text-white">
              Professor Registered Successfully
            </h2>
            <p class="text-sm text-slate-600 dark:text-slate-400">
              Share the link below with the professor to set their password.
            </p>
          </div>

          <div class="rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-700 dark:bg-slate-800">
            <p class="mb-2 text-xs font-medium text-slate-500 dark:text-slate-400">Password Setup Link</p>
            <div class="flex items-center gap-2">
              <code class="flex-1 break-all rounded bg-white px-2 py-1.5 text-xs text-slate-800 dark:bg-slate-900 dark:text-slate-200">
                {{ resetLink }}
              </code>
              <button
                @click="copyLink"
                class="shrink-0 rounded-lg border border-slate-300 bg-white p-2 text-slate-600 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
                :title="copied ? 'Copied!' : 'Copy link'"
              >
                <Copy class="h-4 w-4" />
              </button>
            </div>
            <p v-if="copied" class="mt-1.5 text-xs text-emerald-600 dark:text-emerald-400">
              Copied to clipboard!
            </p>
          </div>

          <button
            @click="registerAnother"
            class="inline-flex w-full items-center justify-center rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-800"
          >
            Register Another Professor
          </button>
        </div>

        <!-- Form -->
        <form v-else @submit.prevent="handleSubmit" class="space-y-4" novalidate>
          <label class="block space-y-1.5">
            <span class="text-sm font-medium text-slate-700 dark:text-slate-300">
              Professor Email
            </span>
            <div class="relative">
              <Mail
                class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400 dark:text-slate-500"
                aria-hidden="true"
              />
              <input
                v-model="email"
                type="email"
                placeholder="professor@university.edu"
                autocomplete="email"
                class="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 placeholder-slate-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-600 dark:bg-slate-800 dark:text-white dark:placeholder-slate-500 dark:focus:border-blue-400 dark:focus:ring-blue-400/20"
              />
            </div>
            <p v-if="errors.mail" class="text-xs text-red-600 dark:text-red-400">
              {{ errors.mail }}
            </p>
          </label>

          <div
            v-if="submitError"
            class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300"
          >
            {{ submitError }}
          </div>

          <button
            type="submit"
            :disabled="submitting"
            class="inline-flex w-full items-center justify-center rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
          >
            {{ submitting ? "Registering..." : "Register Professor" }}
          </button>
        </form>
      </section>
    </div>
  </main>
</template>
