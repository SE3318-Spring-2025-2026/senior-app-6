<script setup lang="ts">
	import { Loader2 } from "lucide-vue-next";
	import { useAuthStore } from "~/stores/auth";

	const router = useRouter();
	const route = useRoute();
	const authStore = useAuthStore();
	const { completeGithubLogin } = useApiClient();

	const status = ref<"loading" | "error" | "success">("loading");
	const message = ref("");

	onMounted(async () => {
		try {
			const code = route.query.code as string | undefined;
			const state = route.query.state as string | undefined;
			const error = route.query.error as string | undefined;

			// Check for OAuth error from GitHub
			if (error) {
				status.value = "error";
				message.value = `GitHub authorization failed: ${error}`;
				return;
			}

			// Validate code is present
			if (!code) {
				status.value = "error";
				message.value = "No authorization code received from GitHub. Please try again.";
				return;
			}

			// CSRF: validate state parameter
			const savedState = sessionStorage.getItem("github_oauth_state");
			if (!state || state !== savedState) {
				status.value = "error";
				message.value = "Invalid state parameter. This may be a CSRF attack. Please try again.";
				return;
			}

			// Retrieve stored studentId
			const studentId = sessionStorage.getItem("github_student_id");
			if (!studentId) {
				status.value = "error";
				message.value = "Student ID not found. Please go back and enter your Student ID.";
				return;
			}

			// Clean up session storage
			sessionStorage.removeItem("github_oauth_state");
			sessionStorage.removeItem("github_student_id");

			// Send code + studentId to backend; backend exchanges code for token
			const response = await completeGithubLogin(code, studentId);

			// Store auth state via Pinia store
			authStore.login(response.token, {
				userType: 'Student',
				id: response.userInfo.id,
				studentId,
				githubUsername: response.userInfo.githubUsername,
			} as StudentUser);

			status.value = "success";
			message.value = "Authentication successful. Redirecting...";

			setTimeout(() => {
				router.push("/student/dashboard");
			}, 1500);
		} catch (err: unknown) {
			status.value = "error";
			const errorMsg =
				err && typeof err === "object" && "message" in err
					? String(err.message)
					: "An unexpected error occurred. Please try again.";
			message.value = errorMsg;
		}
	});
</script>

<template>
  <main class="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-50 via-white to-slate-100 px-4 py-10 dark:from-slate-950 dark:via-slate-900 dark:to-slate-950">
    <!-- Background gradient for dark mode -->
    <div
      class="pointer-events-none absolute inset-0 hidden dark:block"
      aria-hidden="true"
      :style="{
        background: 'radial-gradient(48rem 28rem at 50% 34%, rgba(59,130,246,0.14), rgba(15,23,42,0) 70%)',
        filter: 'blur(14px)',
      }"
    />

    <section class="relative z-10 w-full max-w-md space-y-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-lg dark:border-slate-700 dark:bg-slate-900 dark:shadow-xl md:p-8">
      <!-- Loading State -->
      <div v-if="status === 'loading'" class="flex flex-col items-center justify-center gap-4 text-center">
        <div class="inline-flex h-12 w-12 items-center justify-center rounded-full bg-blue-100 dark:bg-blue-950/50">
          <Loader2 class="h-6 w-6 animate-spin text-blue-600 dark:text-blue-400" />
        </div>
        <h1 class="text-2xl font-semibold text-slate-900 dark:text-white">
          Authenticating...
        </h1>
        <p class="text-slate-600 dark:text-slate-400">
          Please wait while we complete your GitHub login
        </p>
      </div>

      <!-- Success State -->
      <div v-else-if="status === 'success'" class="flex flex-col items-center justify-center gap-4 text-center">
        <div class="inline-flex h-12 w-12 items-center justify-center rounded-full bg-green-100 dark:bg-green-950/50">
          <svg class="h-6 w-6 text-green-600 dark:text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" :stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
        </div>
        <h1 class="text-2xl font-semibold text-slate-900 dark:text-white">
          {{ message }}
        </h1>
        <p class="text-slate-600 dark:text-slate-400">
          You will be redirected to your dashboard shortly.
        </p>
      </div>

      <!-- Error State -->
      <div v-else-if="status === 'error'" class="flex flex-col items-center justify-center gap-4 text-center">
        <div class="inline-flex h-12 w-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950/50">
          <svg class="h-6 w-6 text-red-600 dark:text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" :stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </div>
        <h1 class="text-2xl font-semibold text-red-900 dark:text-red-100">
          Authentication Failed
        </h1>
        <p class="text-red-700 dark:text-red-300">{{ message }}</p>
        <div class="pt-2">
          <NuxtLink
            to="/auth/login?tab=students"
            class="inline-flex items-center justify-center rounded-lg bg-red-600 px-6 py-2 text-sm font-medium text-white transition hover:bg-red-700 dark:bg-red-700 dark:hover:bg-red-600"
          >
            Back to Login
          </NuxtLink>
        </div>
      </div>
    </section>
  </main>
</template>
