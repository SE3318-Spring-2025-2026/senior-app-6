<script setup lang="ts">
	import { AlertCircle, Crown, Mail, RefreshCw, ShieldAlert, Users, UserRoundPlus } from "lucide-vue-next";
	import type { GroupDetailResponse, GroupMember } from "~/types/group";
	import { useAuthStore } from "~/stores/auth";
	import { usePendingInvitations } from "~/composables/usePendingInvitations";

	definePageMeta({
		middleware: "auth",
		roles: ["Student"],
	});

	type HubState = "loading" | "no-group" | "leader" | "member" | "error";

	const { getAuthToken, fetchMyGroup } = useApiClient();
	const authStore = useAuthStore();

	const state = ref<HubState>("loading");
	const group = ref<GroupDetailResponse | null>(null);
	const errorMessage = ref("");

	const currentStudent = computed(() => (
		authStore.userInfo?.userType === "Student" ? authStore.userInfo : null
	));

	const currentMembership = computed<GroupMember | null>(() => {
		if (!group.value || !currentStudent.value) {
			return null;
		}

		return (
			group.value.members.find((member) =>
				member.id === currentStudent.value?.id ||
				member.studentId === currentStudent.value?.id ||
				member.studentId === currentStudent.value?.studentId
			) || null
		);
	});

	const createdAtLabel = computed(() => {
		if (!group.value?.createdAt) {
			return "Creation date unavailable";
		}

		return new Intl.DateTimeFormat("en-US", {
			dateStyle: "medium",
			timeStyle: "short",
		}).format(new Date(group.value.createdAt));
	});

	const leaderName = computed(() => {
		const leader = group.value?.members.find((member) => member.role === "TEAM_LEADER");
		return leader?.fullName?.trim() || leader?.studentId || "Team leader";
	});

	async function loadGroup() {
		state.value = "loading";
		errorMessage.value = "";

		try {
			const token = getAuthToken();
			if (!token) {
				throw new Error("Authentication required. Please log in again.");
			}

			const response = await fetchMyGroup(token);
			group.value = response;

			if (currentMembership.value?.role === "TEAM_LEADER") {
				state.value = "leader";
			} else {
				state.value = "member";
			}
		} catch (error: unknown) {
			const apiError = error as { status?: number; message?: string };

			if (apiError.status === 404) {
				group.value = null;
				state.value = "no-group";
				return;
			}

			group.value = null;
			state.value = "error";
			errorMessage.value = apiError.message || "We couldn't load your group right now.";
		}
	}

	const { fetchInvitations, count: pendingInvitationCount } = usePendingInvitations();

	onMounted(async () => {
		await Promise.all([loadGroup(), fetchInvitations()]);
	});
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-5xl space-y-6">
      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div>
            <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
              Group Hub
            </h1>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Check your team status, review the roster, and see what comes next.
            </p>
          </div>

          <button
            type="button"
            class="inline-flex items-center justify-center gap-2 self-start rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600"
            @click="loadGroup"
          >
            <RefreshCw class="h-4 w-4" />
            Refresh
          </button>
        </div>
      </header>

      <section
        v-if="state === 'loading'"
        class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800"
      >
        <div class="animate-pulse space-y-4">
          <div class="h-6 w-48 rounded bg-slate-200 dark:bg-slate-700"></div>
          <div class="h-4 w-full rounded bg-slate-200 dark:bg-slate-700"></div>
          <div class="h-4 w-2/3 rounded bg-slate-200 dark:bg-slate-700"></div>
          <div class="h-32 rounded-xl bg-slate-100 dark:bg-slate-900"></div>
        </div>
      </section>

      <section
        v-else-if="state === 'error'"
        class="rounded-2xl border border-red-300 bg-red-50 p-6 shadow-sm dark:border-red-800 dark:bg-red-950/40"
      >
        <div class="flex items-start gap-3">
          <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
          <div>
            <h2 class="text-lg font-semibold text-red-900 dark:text-red-100">
              Unable to load your group
            </h2>
            <p class="mt-2 text-sm text-red-800 dark:text-red-300">
              {{ errorMessage }}
            </p>
          </div>
        </div>
      </section>

      <template v-else-if="state === 'no-group'">
        <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
            <div class="flex items-start gap-4">
              <div class="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-amber-100 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300">
                <UserRoundPlus class="h-6 w-6" />
              </div>
              <div>
                <h2 class="text-xl font-semibold text-slate-900 dark:text-white">
                  You are not in a group yet
                </h2>
                <p class="mt-2 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
                  Create a new group to become the team leader and start building your project roster for the active term.
                </p>
              </div>
            </div>

            <NuxtLink
              to="/student/group/create"
              class="inline-flex items-center justify-center rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
            >
              Create a Group
            </NuxtLink>
          </div>
        </section>

        <NuxtLink
          to="/student/group/invitations"
          class="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:hover:bg-slate-700/60"
        >
          <div class="flex items-center gap-3">
            <div class="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-blue-100 dark:bg-blue-950/50">
              <Mail class="h-5 w-5 text-blue-700 dark:text-blue-300" />
            </div>
            <div>
              <p class="font-semibold text-slate-900 dark:text-white">Group Invitations</p>
              <p class="text-sm text-slate-600 dark:text-slate-400">
                Review invitations from team leaders
              </p>
            </div>
          </div>
          <span
            v-if="pendingInvitationCount > 0"
            class="inline-flex h-6 min-w-[1.5rem] items-center justify-center rounded-full bg-blue-600 px-1.5 text-xs font-semibold text-white dark:bg-blue-500"
          >
            {{ pendingInvitationCount }}
          </span>
          <span v-else class="text-sm text-slate-400 dark:text-slate-500">No pending</span>
        </NuxtLink>
      </template>

      <template v-else-if="group">
        <section class="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
          <article class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
            <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <p class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
                  Current Group
                </p>
                <h2 class="mt-2 text-2xl font-semibold text-slate-900 dark:text-white">
                  {{ group.groupName }}
                </h2>
              </div>
              <UiGroupStatusBadge :status="group.status" />
            </div>

            <dl class="mt-6 grid gap-4 rounded-xl bg-slate-50 p-4 dark:bg-slate-900/60 sm:grid-cols-2">
              <div>
                <dt class="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  Created
                </dt>
                <dd class="mt-1 text-sm font-medium text-slate-900 dark:text-white">
                  {{ createdAtLabel }}
                </dd>
              </div>
              <div>
                <dt class="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  Team Leader
                </dt>
                <dd class="mt-1 text-sm font-medium text-slate-900 dark:text-white">
                  {{ leaderName }}
                </dd>
              </div>
              <div>
                <dt class="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  Jira
                </dt>
                <dd class="mt-1 text-sm font-medium text-slate-900 dark:text-white">
                  {{ group.jiraBound ? "Connected" : "Not connected yet" }}
                </dd>
              </div>
              <div>
                <dt class="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  GitHub
                </dt>
                <dd class="mt-1 text-sm font-medium text-slate-900 dark:text-white">
                  {{ group.githubBound ? "Connected" : "Not connected yet" }}
                </dd>
              </div>
            </dl>
          </article>

          <article
            v-if="state === 'leader'"
            class="rounded-2xl border border-blue-200 bg-blue-50 p-6 shadow-sm dark:border-blue-800 dark:bg-blue-950/30"
          >
            <div class="flex items-start gap-3">
              <Crown class="mt-0.5 h-5 w-5 shrink-0 text-blue-700 dark:text-blue-300" />
              <div>
                <h2 class="text-lg font-semibold text-blue-950 dark:text-blue-100">
                  Leader View
                </h2>
                <p class="mt-2 text-sm text-blue-900 dark:text-blue-200">
                  Manage your group from here. Send invitations to teammates and connect your project tools below.
                </p>
              </div>
            </div>

            <div class="mt-5 flex flex-col gap-2 sm:flex-row">
              <NuxtLink
                to="/student/group/invitations"
                class="inline-flex items-center justify-center gap-2 rounded-lg border border-blue-300 bg-white/80 px-4 py-2 text-sm font-medium text-blue-800 transition hover:bg-blue-50 dark:border-blue-700 dark:bg-slate-900/50 dark:text-blue-200 dark:hover:bg-blue-950/40"
              >
                <Mail class="h-4 w-4" />
                Invitations
                <span
                  v-if="pendingInvitationCount > 0"
                  class="inline-flex h-5 min-w-[1.25rem] items-center justify-center rounded-full bg-blue-600 px-1 text-xs font-semibold text-white"
                >
                  {{ pendingInvitationCount }}
                </span>
              </NuxtLink>
              <NuxtLink
                to="/student/group/tools"
                class="inline-flex items-center justify-center gap-2 rounded-lg border border-blue-300 bg-white/80 px-4 py-2 text-sm font-medium text-blue-800 transition hover:bg-blue-50 dark:border-blue-700 dark:bg-slate-900/50 dark:text-blue-200 dark:hover:bg-blue-950/40"
              >
                Tool Binding
              </NuxtLink>
            </div>
          </article>

          <article
            v-else
            class="rounded-2xl border border-amber-200 bg-amber-50 p-6 shadow-sm dark:border-amber-800 dark:bg-amber-950/30"
          >
            <div class="flex items-start gap-3">
              <ShieldAlert class="mt-0.5 h-5 w-5 shrink-0 text-amber-700 dark:text-amber-300" />
              <div>
                <h2 class="text-lg font-semibold text-amber-950 dark:text-amber-100">
                  Member View
                </h2>
                <p class="mt-2 text-sm text-amber-900 dark:text-amber-200">
                  Only the team leader can manage invites and setup steps. You can use this page to monitor your group details.
                </p>
              </div>
            </div>
          </article>
        </section>

        <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
          <div class="mb-4 flex items-center gap-2">
            <Users class="h-5 w-5 text-slate-700 dark:text-slate-300" />
            <h2 class="text-lg font-semibold text-slate-900 dark:text-white">
              Group Members
            </h2>
          </div>

          <UiMemberList :members="group.members" />
        </section>

        <NuxtLink
          to="/student/group/invitations"
          class="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:hover:bg-slate-700/60"
        >
          <div class="flex items-center gap-3">
            <div class="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-blue-100 dark:bg-blue-950/50">
              <Mail class="h-5 w-5 text-blue-700 dark:text-blue-300" />
            </div>
            <div>
              <p class="font-semibold text-slate-900 dark:text-white">Group Invitations</p>
              <p class="text-sm text-slate-600 dark:text-slate-400">
                Review pending invitations from other team leaders
              </p>
            </div>
          </div>
          <span
            v-if="pendingInvitationCount > 0"
            class="inline-flex h-6 min-w-[1.5rem] items-center justify-center rounded-full bg-blue-600 px-1.5 text-xs font-semibold text-white dark:bg-blue-500"
          >
            {{ pendingInvitationCount }}
          </span>
          <span v-else class="text-sm text-slate-400 dark:text-slate-500">No pending</span>
        </NuxtLink>
      </template>
    </div>
  </main>
</template>
