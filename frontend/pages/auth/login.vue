<script setup lang="ts">
	import { z } from "zod";
	import { useAuthStore } from "~/stores/auth";
	import {
		AlertCircle,
		ArrowLeft,
		GraduationCap,
		LayoutDashboard,
		Mail,
		Shield,
		Lock
	} from "lucide-vue-next";

	import { GitHubIcon as Github } from "vue3-simple-icons";

	const router = useRouter();
	const route = useRoute();
	const authStore = useAuthStore();
	const { loginFaculty, initiateGithubOAuth } = useApiClient();

	function getDashboardPath(role: string): string {
		const roles: Set<string> = new Set(["Admin", "Professor", "Coordinator", "Student"]);
		if (roles.has(role)) {
			return `/${role.toLowerCase()}/dashboard`;
		}
		return "/auth/login";
	}

	// Form validation schemas
	const facultySchema = z.object({
		email: z.string().email("Please enter a valid email address."),
		password: z.string().min(1, "Password is required."),
	});

	const studentSchema = z.object({
		studentId: z
			.string()
			.trim()
			.min(1, "Student ID is required.")
			.regex(/^[0-9]{11}$/, "Student ID must be exactly 11 digits."),
	});

	type AuthTab = "students" | "faculty";

	const activeTab = ref<AuthTab>("students");
	const facultyErrorMessage = ref("");
	const studentErrorMessage = ref("");

	const showStudentNotRegisteredAlert = computed(() => route.query.error === "StudentNotRegistered");

	// Faculty form
	const facultyEmail = ref("");
	const facultyPassword = ref("");
	const facultySubmitting = ref(false);
	const facultyErrors = ref<{ email?: string; password?: string }>({});

	// Student form
	const studentId = ref("");
	const studentSubmitting = ref(false);
	const studentErrors = ref<{ studentId?: string }>({});

	const isStudentButtonDisabled = computed(() =>
	{
		if (!studentId.value)
		{
			return true;
		}
		return studentId.value.trim() === "" || studentId.value.length !== 11;
	});

	async function handleFacultySubmit() {
		facultyErrorMessage.value = "";
		facultyErrors.value = {};

		const result = facultySchema.safeParse({
			email: facultyEmail.value,
			password: facultyPassword.value,
		});

		if (!result.success) {
			const fieldErrors = result.error.flatten().fieldErrors;
			facultyErrors.value = {
				email: fieldErrors.email?.[0],
				password: fieldErrors.password?.[0],
			};
			return;
		}

		facultySubmitting.value = true;
		try {
			const response = await loginFaculty(result.data.email, result.data.password);
			authStore.login(response.token, {
				userType: 'Staff',
				id: response.userInfo.id,
				mail: response.userInfo.mail,
				role: response.userInfo.role,
				firstLogin: response.userInfo.firstLogin,
			} as StaffUser);
			router.push(getDashboardPath(response.userInfo.role));
		} catch (error: unknown) {
			const errorMsg =
				error && typeof error === "object" && "message" in error
					? String(error.message)
					: "Authentication failed. Please try again.";
			facultyErrorMessage.value = errorMsg;
		} finally {
			facultySubmitting.value = false;
		}
	}

	async function handleStudentSubmit() {
		studentErrorMessage.value = "";
		studentErrors.value = {};

		const result = studentSchema.safeParse({ studentId: studentId.value });

		if (!result.success) {
			const fieldErrors = result.error.flatten().fieldErrors;
			studentErrors.value = { studentId: fieldErrors.studentId?.[0] };
			return;
		}

		studentSubmitting.value = true;
		try {
			// Redirect to GitHub OAuth - backend will handle the rest
			initiateGithubOAuth(result.data.studentId);
		} catch (error: unknown) {
			const errorMsg =
				error && typeof error === "object" && "message" in error
					? String(error.message)
					: "GitHub login failed. Please try again.";
			studentErrorMessage.value = errorMsg;
			studentSubmitting.value = false;
		}
	}
</script>

<template>
  <main class="relative flex min-h-screen items-center justify-center overflow-hidden bg-slate-50 px-4 py-10 transition-colors dark:bg-slate-950">
    <!-- Background gradient -->
    <div
      class="pointer-events-none absolute inset-0 hidden dark:block"
      aria-hidden="true"
      :style="{
        background: 'radial-gradient(48rem 28rem at 50% 34%, rgba(59,130,246,0.14), rgba(15,23,42,0) 70%)',
        filter: 'blur(14px)',
      }">
		</div>

    <!-- Login card -->
    <section class="relative z-10 w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-lg transition-colors dark:border-slate-700 dark:bg-slate-900 dark:shadow-xl md:p-8">
      <!-- Header -->
      <header class="mb-6 space-y-3 text-center">
        <div class="mx-auto flex h-10 w-10 items-center justify-center rounded-xl border border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-400/30 dark:bg-blue-950/50 dark:text-blue-300">
          <LayoutDashboard class="h-5 w-5" aria-hidden="true" />
        </div>
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white">
          University Project Dashboard
        </h1>
        <p class="text-sm text-slate-600 dark:text-slate-400">
          Sign in to access your project workspace.
        </p>
      </header>

      <!-- Student not registered alert -->
      <div
        v-if="showStudentNotRegisteredAlert"
        class="mb-4 flex items-start gap-2 rounded-lg border border-red-300 bg-red-50 p-3 text-sm text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300"
      >
        <AlertCircle class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
        <p>Your student ID is not registered in the system.</p>
      </div>

      <!-- Auth tabs -->
      <div class="mb-6 grid grid-cols-2 gap-2 rounded-lg border border-slate-200 bg-slate-100 p-1 dark:border-slate-700 dark:bg-slate-800">
        <button
          type="button"
          @click="activeTab = 'students'; facultyErrorMessage = ''"
          :class="[
            'rounded-md px-3 py-2 text-sm font-medium transition',
            activeTab === 'students'
              ? 'bg-white text-slate-900 shadow-sm dark:bg-slate-700 dark:text-white'
              : 'text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-200',
          ]"
          :aria-pressed="activeTab === 'students'"
        >
          <GraduationCap class="mr-2 inline h-4 w-4" aria-hidden="true" />
          Students
        </button>
        <button
          type="button"
          @click="activeTab = 'faculty'; studentErrorMessage = ''"
          :class="[
            'rounded-md px-3 py-2 text-sm font-medium transition',
            activeTab === 'faculty'
              ? 'bg-white text-slate-900 shadow-sm dark:bg-slate-700 dark:text-white'
              : 'text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-200',
          ]"
          :aria-pressed="activeTab === 'faculty'"
        >
          <Shield class="mr-2 inline h-4 w-4" aria-hidden="true" />
          Faculty/Admin
        </button>
      </div>

      <!-- Faculty login form -->
      <form v-if="activeTab === 'faculty'" @submit.prevent="handleFacultySubmit" class="space-y-4" novalidate>
        <label class="block space-y-1.5">
          <span class="text-sm font-medium text-slate-700 dark:text-slate-300">
            Email Address
          </span>
          <div class="relative">
            <Mail
              class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400 dark:text-slate-500"
              aria-hidden="true"
            />
            <input
              v-model="facultyEmail"
              type="email"
              placeholder="you@university.edu"
              autocomplete="email"
              class="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 placeholder-slate-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-600 dark:bg-slate-800 dark:text-white dark:placeholder-slate-500 dark:focus:border-blue-400 dark:focus:ring-blue-400/20"
            />
          </div>
          <p v-if="facultyErrors.email" class="text-xs text-red-600 dark:text-red-400">
            {{ facultyErrors.email }}
          </p>
        </label>

        <label class="block space-y-1.5">
          <span class="text-sm font-medium text-slate-700 dark:text-slate-300">
            Password
          </span>
          <div class="relative">
            <Lock
              class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400 dark:text-slate-500"
              aria-hidden="true"
            />
            <input
              v-model="facultyPassword"
              type="password"
              placeholder="Enter your password"
              autocomplete="current-password"
              class="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 placeholder-slate-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-600 dark:bg-slate-800 dark:text-white dark:placeholder-slate-500 dark:focus:border-blue-400 dark:focus:ring-blue-400/20"
            />
          </div>
          <p v-if="facultyErrors.password" class="text-xs text-red-600 dark:text-red-400">
            {{ facultyErrors.password }}
          </p>
        </label>

        <div
          v-if="facultyErrorMessage"
          class="rounded-lg border border-red-300 bg-red-50 p-3 text-sm text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300"
        >
          {{ facultyErrorMessage }}
        </div>

        <button
          type="submit"
          :disabled="facultySubmitting"
          class="inline-flex w-full items-center justify-center rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600 dark:focus-visible:ring-blue-500/40"
        >
          {{ facultySubmitting ? "Signing in..." : "Sign In" }}
        </button>
      </form>

      <!-- Student GitHub login form -->
      <form v-else @submit.prevent="handleStudentSubmit" class="space-y-4" novalidate>
        <label class="block space-y-1.5">
          <span class="text-sm font-medium text-slate-700 dark:text-slate-300">
            Student ID
          </span>
          <NumericInput
            v-model="studentId"
            placeholder="e.g., 202400123"
						maxlength="11"
            class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder-slate-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-600 dark:bg-slate-800 dark:text-white dark:placeholder-slate-500 dark:focus:border-blue-400 dark:focus:ring-blue-400/20"
          />
          <p v-if="studentErrors.studentId" class="text-xs text-red-600 dark:text-red-400">
            {{ studentErrors.studentId }}
          </p>
        </label>

        <div
          v-if="studentErrorMessage"
          class="rounded-lg border border-red-300 bg-red-50 p-3 text-sm text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300"
        >
          {{ studentErrorMessage }}
        </div>

        <button
          type="submit"
          :disabled="isStudentButtonDisabled || studentSubmitting"
          class="inline-flex w-full items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-900 transition hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/35 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-800 dark:text-white dark:hover:bg-slate-700 dark:focus-visible:ring-blue-500/35"
        >
          <Github class="h-4 w-4" aria-hidden="true" />
          {{ studentSubmitting ? "Redirecting to GitHub..." : "Sign in with GitHub" }}
        </button>
      </form>

      <!-- Back to home -->
      <div class="mt-4 text-center">
        <NuxtLink
          to="/"
          class="inline-flex items-center gap-1.5 text-sm text-slate-500 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
        >
          <ArrowLeft class="h-3.5 w-3.5" />
          Back to Home
        </NuxtLink>
      </div>
    </section>
  </main>
</template>
