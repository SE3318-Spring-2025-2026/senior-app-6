<template>
	<div class="min-h-full flex flex-col bg-[var(--background)] text-[var(--foreground)]">
		<!-- Navigation bar with invitation badge -->
		<nav
			class="border-b border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800"
		>
			<div class="mx-auto flex max-w-7xl items-center justify-between px-4 py-3 sm:px-6 lg:px-8">
				<!-- Logo/Brand -->
				<NuxtLink to="/" class="font-bold text-lg text-slate-900 dark:text-white">
					Project Dashboard
				</NuxtLink>

				<!-- Navigation links -->
				<div class="flex items-center gap-6">
					<!-- Student navigation -->
					<NuxtLink
						v-if="authStore.user?.role === 'Student'"
						to="/student/dashboard"
						class="text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
					>
						Dashboard
					</NuxtLink>

				<!-- Invitations link with badge -->
				<NuxtLink
					v-if="authStore.user?.role === 'Student'"
					to="/student/group/invitations"
					class="relative text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
				>
					<span class="flex items-center gap-1">
						Invitations
						<!-- Pending invitation badge -->
						<span
							v-if="pendingInvitationCount > 0"
							class="inline-flex h-5 w-5 items-center justify-center rounded-full bg-red-600 text-xs font-bold text-white dark:bg-red-500"
						>
							{{ pendingInvitationCount }}
						</span>
					</span>
				</NuxtLink>					<!-- Theme toggle -->
					<UiThemeToggle />
				</div>
			</div>
		</nav>

		<!-- Main content -->
		<div class="flex-1">
			<slot />
		</div>
	</div>
</template>

<script setup lang="ts">
	import { computed, onMounted, onBeforeUnmount } from "vue";
	import { useAuthStore } from "~/stores/auth";
	import { usePendingInvitations } from "~/composables/usePendingInvitations";

	const authStore = useAuthStore();
	const { fetchInvitations, count: pendingCount } = usePendingInvitations();

	/**
	 * Computed property for pending invitation count badge
	 * Acceptance criteria: Badge updates when an invitation is accepted/declined without page reload
	 */
	const pendingInvitationCount = computed(() => pendingCount.value);

	/**
	 * Fetch invitations on mount
	 * This ensures the badge shows the correct count when the layout loads
	 */
	onMounted(async () => {
		if (authStore.user?.role === "Student") {
			await fetchInvitations();

			// Refresh invitation count every 30 seconds for real-time badge updates
			const interval = setInterval(async () => {
				await fetchInvitations();
			}, 30 * 1000);

			// Cleanup on unmount
			onBeforeUnmount(() => clearInterval(interval));
		}
	});
</script>
