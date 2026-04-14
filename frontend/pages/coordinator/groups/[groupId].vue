<script setup lang="ts">
	import { z } from "zod";
	import {
		AlertCircle,
		ArrowLeft,
		GitBranch,
		Loader as LoaderIcon,
		Server,
		ShieldAlert,
		UserPlus,
		Users
	} from "lucide-vue-next";
	import type { GroupDetailResponse, GroupMember } from "~/types/group";

	definePageMeta({
		middleware: "auth",
		roles: ["Coordinator"],
	});

	const route = useRoute();
	const {
		getAuthToken,
		fetchCoordinatorGroupDetail,
		updateCoordinatorGroupMembers,
		disbandCoordinatorGroup
	} = useApiClient();

	const group = ref<GroupDetailResponse | null>(null);
	const isLoading = ref(true);
	const fetchError = ref<string | null>(null);

	const addStudentId = ref("");
	const addSubmitting = ref(false);
	const removeSubmittingStudentId = ref<string | null>(null);
	const disbandSubmitting = ref(false);
	const showDisbandConfirm = ref(false);

	const actionError = ref<string | null>(null);
	const actionMessage = ref("");

	const addMemberSchema = z.object({
		studentId: z
			.string()
			.trim()
			.min(1, "Student ID is required.")
			.regex(/^[0-9]{11}$/, "Student ID must be exactly 11 digits.")
	});

	const currentGroupId = computed(() => {
		const rawId = route.params.groupId;
		return typeof rawId === "string" ? rawId : rawId?.[0] || "";
	});

	const isDisbanded = computed(() => group.value?.status === "DISBANDED");

	const canEditMembers = computed(() => !isDisbanded.value && !disbandSubmitting.value);

	const createdAtLabel = computed(() => {
		if (!group.value?.createdAt) return "Creation date unavailable";
		return new Intl.DateTimeFormat("en-US", {
			dateStyle: "medium",
			timeStyle: "short"
		}).format(new Date(group.value.createdAt));
	});

	async function loadGroup() {
		isLoading.value = true;
		fetchError.value = null;
		actionError.value = null;
		actionMessage.value = "";
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required. Please log in.");
			if (!currentGroupId.value) throw new Error("Invalid group identifier.");
			group.value = await fetchCoordinatorGroupDetail(currentGroupId.value, token);
		} catch (error) {
			const message =
				error && typeof error === "object" && "message" in error
					? String(error.message)
					: "Failed to load group details";
			fetchError.value = message;
		} finally {
			isLoading.value = false;
		}
	}

	async function handleAddMember() {
		actionError.value = null;
		actionMessage.value = "";

		const result = addMemberSchema.safeParse({ studentId: addStudentId.value });
		if (!result.success) {
			actionError.value = result.error.flatten().fieldErrors.studentId?.[0] || "Invalid Student ID.";
			return;
		}

		addSubmitting.value = true;
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required. Please log in.");
			if (!currentGroupId.value) throw new Error("Invalid group identifier.");

			const updatedGroup = await updateCoordinatorGroupMembers(
				currentGroupId.value,
				{
					studentId: result.data.studentId,
					action: "ADD"
				},
				token
			);

			group.value = updatedGroup;
			addStudentId.value = "";
			actionMessage.value = "Member added successfully.";
		} catch (error) {
			actionError.value =
				error && typeof error === "object" && "message" in error
					? String(error.message)
					: "Failed to add member";
		} finally {
			addSubmitting.value = false;
		}
	}

	async function handleRemoveMember(member: GroupMember) {
		if (!member.studentId) {
			actionError.value = "Member student ID is missing.";
			return;
		}

		actionError.value = null;
		actionMessage.value = "";
		removeSubmittingStudentId.value = member.studentId;

		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required. Please log in.");
			if (!currentGroupId.value) throw new Error("Invalid group identifier.");

			const updatedGroup = await updateCoordinatorGroupMembers(
				currentGroupId.value,
				{
					studentId: member.studentId,
					action: "REMOVE"
				},
				token
			);

			group.value = updatedGroup;
			actionMessage.value = "Member removed successfully.";
		} catch (error) {
			actionError.value =
				error && typeof error === "object" && "message" in error
					? String(error.message)
					: "Failed to remove member";
		} finally {
			removeSubmittingStudentId.value = null;
		}
	}

	async function handleConfirmDisband() {
		actionError.value = null;
		actionMessage.value = "";
		disbandSubmitting.value = true;

		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required. Please log in.");
			if (!currentGroupId.value) throw new Error("Invalid group identifier.");

			const updatedGroup = await disbandCoordinatorGroup(currentGroupId.value, token);
			group.value = updatedGroup;
			showDisbandConfirm.value = false;
			actionMessage.value = "Group has been disbanded.";
		} catch (error) {
			actionError.value =
				error && typeof error === "object" && "message" in error
					? String(error.message)
					: "Failed to disband group";
		} finally {
			disbandSubmitting.value = false;
		}
	}

	onMounted(loadGroup);
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-6xl space-y-6">
      <NuxtLink
        to="/coordinator/groups"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to groups
      </NuxtLink>

      <section
        v-if="isLoading"
        class="flex items-center justify-center rounded-2xl border border-slate-200 bg-white p-12 shadow-sm dark:border-slate-700 dark:bg-slate-800"
      >
        <div class="text-center">
          <LoaderIcon class="mx-auto h-8 w-8 animate-spin text-blue-600 dark:text-blue-400" />
          <p class="mt-3 text-sm text-slate-600 dark:text-slate-400">Loading group details...</p>
        </div>
      </section>

      <section
        v-else-if="fetchError"
        class="rounded-2xl border border-red-300 bg-red-50 p-6 shadow-sm dark:border-red-800 dark:bg-red-950/40"
      >
        <div class="flex items-start gap-3">
          <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
          <div>
            <h2 class="text-lg font-semibold text-red-900 dark:text-red-200">Unable to load group</h2>
            <p class="mt-2 text-sm text-red-700 dark:text-red-300">{{ fetchError }}</p>
          </div>
        </div>
      </section>

      <template v-else-if="group">
        <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
          <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
                Group Detail
              </p>
              <h1 class="mt-2 text-2xl font-semibold text-slate-900 dark:text-white md:text-3xl">
                {{ group.groupName }}
              </h1>
              <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
                Created {{ createdAtLabel }} • Term {{ group.termId || "Unknown" }}
              </p>
            </div>

            <div class="flex items-center gap-2">
              <UiGroupStatusBadge :status="group.status" />
            </div>
          </div>
        </header>

        <section class="grid gap-6 lg:grid-cols-[1.5fr_1fr]">
          <article class="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
            <div class="flex items-center gap-2">
              <Users class="h-5 w-5 text-slate-700 dark:text-slate-300" />
              <h2 class="text-lg font-semibold text-slate-900 dark:text-white">Members</h2>
            </div>

            <div class="rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/50">
              <label class="block space-y-2">
                <span class="text-sm font-medium text-slate-700 dark:text-slate-300">Add student by ID</span>
                <div class="flex flex-col gap-2 sm:flex-row">
                  <input
                    v-model="addStudentId"
                    type="text"
                    maxlength="11"
                    :disabled="!canEditMembers || addSubmitting"
                    placeholder="Enter 11-digit student ID"
                    class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-slate-500 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-slate-400"
                  />
                  <button
                    type="button"
                    :disabled="!canEditMembers || addSubmitting"
                    class="inline-flex items-center justify-center gap-1 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
                    @click="handleAddMember"
                  >
                    <UserPlus class="h-4 w-4" />
                    {{ addSubmitting ? "Adding..." : "Add Member" }}
                  </button>
                </div>
              </label>
            </div>

            <UiMemberList
              :members="group.members"
              :removable="canEditMembers && !removeSubmittingStudentId"
              @remove="handleRemoveMember"
            />
          </article>

          <article class="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
            <h2 class="text-lg font-semibold text-slate-900 dark:text-white">Group Controls</h2>

            <div class="space-y-3">
              <div class="rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/50">
                <div class="flex items-center justify-between gap-3">
                  <div class="flex items-center gap-2">
                    <Server class="h-4 w-4 text-slate-600 dark:text-slate-300" />
                    <p class="text-sm font-medium text-slate-700 dark:text-slate-300">JIRA</p>
                  </div>
                  <span class="text-sm font-semibold text-slate-900 dark:text-slate-100">
                    {{ group.jiraBound ? "Bound" : "Not bound" }}
                  </span>
                </div>
              </div>

              <div class="rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/50">
                <div class="flex items-center justify-between gap-3">
                  <div class="flex items-center gap-2">
                    <GitBranch class="h-4 w-4 text-slate-600 dark:text-slate-300" />
                    <p class="text-sm font-medium text-slate-700 dark:text-slate-300">GitHub</p>
                  </div>
                  <span class="text-sm font-semibold text-slate-900 dark:text-slate-100">
                    {{ group.githubBound ? "Bound" : "Not bound" }}
                  </span>
                </div>
              </div>
            </div>

            <button
              type="button"
              :disabled="isDisbanded || disbandSubmitting"
              class="inline-flex w-full items-center justify-center gap-2 rounded-lg border border-red-300 bg-red-50 px-4 py-2.5 text-sm font-medium text-red-700 transition hover:bg-red-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-red-800 dark:bg-red-950/40 dark:text-red-300 dark:hover:bg-red-900/40"
              @click="showDisbandConfirm = true"
            >
              <ShieldAlert class="h-4 w-4" />
              {{ isDisbanded ? "Already Disbanded" : "Disband Group" }}
            </button>

            <p class="text-xs text-slate-500 dark:text-slate-400">
              Disbanding removes all members and auto-denies pending invitations.
            </p>
          </article>
        </section>

        <div
          v-if="actionError"
          class="rounded-lg border border-red-300 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300"
        >
          {{ actionError }}
        </div>

        <div
          v-if="actionMessage"
          class="rounded-lg border border-emerald-300 bg-emerald-50 p-4 text-sm text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-300"
        >
          {{ actionMessage }}
        </div>
      </template>
    </div>

    <div
      v-if="showDisbandConfirm"
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4"
    >
      <div class="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-xl dark:border-slate-700 dark:bg-slate-800">
        <h3 class="text-lg font-semibold text-slate-900 dark:text-white">
          Confirm Disband
        </h3>
        <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
          Are you sure you want to disband <span class="font-semibold">{{ group?.groupName }}</span>?
          This action cannot be undone.
        </p>
        <div class="mt-5 flex gap-2">
          <button
            type="button"
            :disabled="disbandSubmitting"
            class="flex-1 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600"
            @click="showDisbandConfirm = false"
          >
            Cancel
          </button>
          <button
            type="button"
            :disabled="disbandSubmitting"
            class="flex-1 rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-red-700 dark:hover:bg-red-600"
            @click="handleConfirmDisband"
          >
            {{ disbandSubmitting ? "Disbanding..." : "Confirm Disband" }}
          </button>
        </div>
      </div>
    </div>
  </main>
</template>
