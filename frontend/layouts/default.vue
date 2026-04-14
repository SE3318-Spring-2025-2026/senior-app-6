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
						v-if="(authStore as any).user?.role === 'Student'"
						to="/student/dashboard"
						class="text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
					>
						Dashboard
					</NuxtLink>

					<!-- Invitations link with badge -->
					<NuxtLink
						v-if="(authStore as any).user?.role === 'Student'"
						to="/student/group/invitations"
						class="relative text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
					>
						<span class="flex items-center gap-1">
							Invitations
							<!-- Pending invitation badge -->
							<span
								v-if="pendingCount > 0"
								class="inline-flex h-5 w-5 items-center justify-center rounded-full bg-red-600 text-xs font-bold text-white dark:bg-red-500"
							>
								{{ pendingCount > 9 ? "9+" : pendingCount }}
							</span>
						</span>
					</NuxtLink>
					<!-- Theme toggle -->
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

	let interval: ReturnType<typeof setInterval> | null = null;
	/**
	 * Initializes the global invitation state on mount.
	 * Establishes the centralized 30-second polling interval to keep the
	 * navigation badge updated across all pages, preventing double-polling.
	 */
	onMounted(async () => {
		// @ts-ignore
		if (authStore.user?.role === "Student") {
			await fetchInvitations();

			interval = setInterval(async () => {
				await fetchInvitations();
			}, 30 * 1000);
		}
	});

	onBeforeUnmount(() => {
		if (interval) clearInterval(interval);
	});
</script>
