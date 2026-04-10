<script setup lang="ts">
	import { z } from "zod";
	import { AlertCircle, ArrowLeft, Loader2, Users } from "lucide-vue-next";

	definePageMeta({
		middleware: "auth",
		roles: ["Student"],
	});

	const router = useRouter();
	const { getAuthToken, createGroup } = useApiClient();

	const groupName = ref("");
	const submitting = ref(false);
	const fieldErrors = ref<{ groupName?: string }>({});
	const formError = ref("");

	const createGroupSchema = z.object({
		groupName: z
			.string()
			.trim()
			.min(1, "Group name is required.")
			.max(100, "Group name must be 100 characters or fewer."),
	});

	async function handleSubmit() {
		fieldErrors.value = {};
		formError.value = "";

		const result = createGroupSchema.safeParse({
			groupName: groupName.value,
		});

		if (!result.success) {
			const flattened = result.error.flatten().fieldErrors;
			fieldErrors.value = {
				groupName: flattened.groupName?.[0],
			};
			return;
		}

		submitting.value = true;

		try {
			const token = getAuthToken();
			if (!token) {
				throw new Error("Authentication required. Please log in again.");
			}

			await createGroup(result.data, token);
			router.push("/student/group");
		} catch (error: unknown) {
			const apiError = error as { status?: number; message?: string };
			const message = apiError.message || "Failed to create group. Please try again.";

			if (apiError.status === 409) {
				fieldErrors.value = { groupName: message };
			} else {
				formError.value = message;
			}
		} finally {
			submitting.value = false;
		}
	}
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-3xl space-y-6">
      <NuxtLink
        to="/student/group"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to group hub
      </NuxtLink>

      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <div class="flex items-start gap-4">
          <div class="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-blue-100 text-blue-700 dark:bg-blue-950/50 dark:text-blue-300">
            <Users class="h-6 w-6" />
          </div>
          <div>
            <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
              Create Your Group
            </h1>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Pick a clear team name. If the name is already taken for the active term, you will see the conflict inline.
            </p>
          </div>
        </div>
      </header>

      <form
        class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg"
        novalidate
        @submit.prevent="handleSubmit"
      >
        <div
          v-if="formError"
          class="mb-6 flex items-start gap-3 rounded-xl border border-red-300 bg-red-50 p-4 text-sm text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300"
        >
          <AlertCircle class="mt-0.5 h-4 w-4 shrink-0" />
          <p>{{ formError }}</p>
        </div>

        <label class="block space-y-2">
          <span class="text-sm font-semibold text-slate-900 dark:text-white">Group Name</span>
          <p class="text-xs text-slate-600 dark:text-slate-400">
            This name will identify your team throughout the term.
          </p>
          <input
            v-model="groupName"
            type="text"
            maxlength="100"
            placeholder="Enter a unique group name"
            class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:placeholder:text-slate-500 dark:focus:border-blue-400 dark:focus:ring-blue-400/20"
            :class="fieldErrors.groupName ? 'border-red-400 focus:border-red-500 focus:ring-red-500/20 dark:border-red-500 dark:focus:border-red-400 dark:focus:ring-red-400/20' : ''"
          />
          <p v-if="fieldErrors.groupName" class="text-xs text-red-600 dark:text-red-400">
            {{ fieldErrors.groupName }}
          </p>
        </label>

        <div class="mt-6 flex flex-col gap-3 border-t border-slate-200 pt-6 dark:border-slate-700 sm:flex-row sm:items-center sm:justify-between">
          <p class="text-xs text-slate-500 dark:text-slate-400">
            You will be added as the team leader automatically after creation.
          </p>
          <button
            type="submit"
            :disabled="submitting"
            class="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
          >
            <Loader2 v-if="submitting" class="h-4 w-4 animate-spin" />
            <span>{{ submitting ? "Creating Group..." : "Create Group" }}</span>
          </button>
        </div>
      </form>
    </div>
  </main>
</template>
