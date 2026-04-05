<script setup lang="ts">
	import { z } from "zod";
	import { AlertCircle, KeyRound, Loader2, Lock, ShieldCheck } from "lucide-vue-next";

	const router = useRouter();
	const route = useRoute();
	const { validateResetToken, resetPassword } = useApiClient();

	const token = computed(() => (route.query.token as string) || null);

	const resetPasswordSchema = z
		.object({
			newPassword: z.string().min(8, "Password must be at least 8 characters long."),
			confirmPassword: z.string().min(1, "Please confirm your password."),
		})
		.refine((values) => values.newPassword === values.confirmPassword, {
			message: "Passwords do not match.",
			path: ["confirmPassword"],
		});

	type TokenValidationState = "loading" | "invalid" | "valid";

	const tokenValidationState = ref<TokenValidationState>("loading");
	const tokenErrorMessage = ref("");
	const newPassword = ref("");
	const confirmPassword = ref("");
	const successMessage = ref("");
	const submitError = ref("");
	const submitting = ref(false);
	const errors = ref<{ newPassword?: string; confirmPassword?: string }>({});

	onMounted(async () => {
		if (!token.value) {
			tokenValidationState.value = "invalid";
			tokenErrorMessage.value = "No reset token provided. Please use the link from your email.";
			return;
		}

		try {
			await validateResetToken(token.value);
			tokenValidationState.value = "valid";
		} catch (err: unknown) {
			tokenValidationState.value = "invalid";
			tokenErrorMessage.value =
				err && typeof err === "object" && "message" in err
					? String(err.message)
					: "This setup link is invalid or has expired. Please contact the Administrator for a new invitation.";
		}
	});

	async function handleSubmit() {
		errors.value = {};
		submitError.value = "";

		const result = resetPasswordSchema.safeParse({
			newPassword: newPassword.value,
			confirmPassword: confirmPassword.value,
		});

		if (!result.success) {
			const fieldErrors = result.error.flatten().fieldErrors;
			errors.value = {
				newPassword: fieldErrors.newPassword?.[0],
				confirmPassword: fieldErrors.confirmPassword?.[0],
			};
			return;
		}

		if (!token.value) return;

		submitting.value = true;
		try {
			await resetPassword(token.value, result.data.newPassword);
			successMessage.value = "Password set successfully. Redirecting to login...";
			setTimeout(() => {
				router.push("/auth/login");
			}, 1500);
		} catch (err: unknown) {
			submitError.value =
				err && typeof err === "object" && "message" in err
					? String(err.message)
					: "Failed to set password. Please try again.";
		} finally {
			submitting.value = false;
		}
	}
</script>

<template>
  <main class="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-50 via-white to-slate-100 px-4 py-10 dark:from-slate-950 dark:via-slate-900 dark:to-slate-950">
    <section class="w-full max-w-md rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-xl backdrop-blur dark:border-slate-700 dark:bg-slate-900/90 dark:shadow-lg md:p-8">
      <!-- Loading State -->
      <div v-if="tokenValidationState === 'loading'" class="flex flex-col items-center justify-center gap-3 py-8 text-center">
        <Loader2 class="h-8 w-8 animate-spin text-slate-700 dark:text-slate-300" aria-hidden="true" />
        <p class="text-sm text-slate-600 dark:text-slate-400">Validating your setup link...</p>
      </div>

      <!-- Invalid Token State -->
      <div v-else-if="tokenValidationState === 'invalid'" class="space-y-4 text-center">
        <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-red-600 dark:bg-red-950/50 dark:text-red-400">
          <AlertCircle class="h-6 w-6" aria-hidden="true" />
        </div>
        <h1 class="text-xl font-semibold tracking-tight text-slate-900 dark:text-white">
          Password Setup Unavailable
        </h1>
        <p class="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300">
          {{ tokenErrorMessage || 'This setup link is invalid or has expired. Please contact the Administrator for a new invitation.' }}
        </p>
        <NuxtLink
          to="/auth/login"
          class="inline-flex w-full items-center justify-center rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-800 dark:bg-slate-700 dark:hover:bg-slate-600"
        >
          Return to Login
        </NuxtLink>
      </div>

      <!-- Valid Token State -->
      <div v-else>
        <header class="mb-6 space-y-2 text-center">
          <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300">
            <ShieldCheck class="h-6 w-6" aria-hidden="true" />
          </div>
          <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white">
            Welcome! Please set your permanent password
          </h1>
          <p class="text-sm text-slate-600 dark:text-slate-400">
            Use a strong password you have not used before.
          </p>
        </header>

        <form @submit.prevent="handleSubmit" class="space-y-4" novalidate>
          <label class="block space-y-1.5">
            <span class="text-sm font-medium text-slate-700 dark:text-slate-300">New Password</span>
            <div class="relative">
              <Lock
                class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400 dark:text-slate-500"
                aria-hidden="true"
              />
              <input
                v-model="newPassword"
                type="password"
                autocomplete="new-password"
                placeholder="Enter your new password"
                class="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 outline-none ring-0 transition focus:border-slate-500 dark:border-slate-600 dark:bg-slate-800 dark:text-white dark:placeholder-slate-500 dark:focus:border-slate-400"
              />
            </div>
            <p v-if="errors.newPassword" class="text-xs text-red-600 dark:text-red-400">
              {{ errors.newPassword }}
            </p>
          </label>

          <label class="block space-y-1.5">
            <span class="text-sm font-medium text-slate-700 dark:text-slate-300">Confirm Password</span>
            <div class="relative">
              <KeyRound
                class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400 dark:text-slate-500"
                aria-hidden="true"
              />
              <input
                v-model="confirmPassword"
                type="password"
                autocomplete="new-password"
                placeholder="Confirm your new password"
                class="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 outline-none ring-0 transition focus:border-slate-500 dark:border-slate-600 dark:bg-slate-800 dark:text-white dark:placeholder-slate-500 dark:focus:border-slate-400"
              />
            </div>
            <p v-if="errors.confirmPassword" class="text-xs text-red-600 dark:text-red-400">
              {{ errors.confirmPassword }}
            </p>
          </label>

          <div
            v-if="submitError"
            class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300"
          >
            {{ submitError }}
          </div>

          <p
            v-if="successMessage"
            class="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-300"
          >
            {{ successMessage }}
          </p>

          <button
            type="submit"
            :disabled="submitting"
            class="inline-flex w-full items-center justify-center rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-slate-700 dark:hover:bg-slate-600"
          >
            {{ submitting ? "Saving password..." : "Set Password" }}
          </button>
        </form>
      </div>
    </section>
  </main>
</template>
