<template>
	<main
		class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8"
	>
		<div class="mx-auto w-full max-w-4xl space-y-6">
			<NuxtLink
				to="/student/group"
				class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
			>
				<ArrowLeft class="h-4 w-4" />
				Back to group hub
			</NuxtLink>

			<!-- Header -->
			<header
				class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg"
			>
				<div class="flex items-center justify-between">
					<div>
						<h1
							class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl"
						>
							Group Invitations
						</h1>
						<p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
							Manage your pending group invitations from teammates.
						</p>
					</div>
					<div class="flex items-center gap-2 rounded-lg bg-slate-100 px-3 py-2 dark:bg-slate-700">
						<MailOpen class="h-5 w-5 text-slate-600 dark:text-slate-400" />
						<span class="font-semibold text-slate-900 dark:text-white">
							{{ pendingCount }}
						</span>
					</div>
				</div>
			</header>

			<!-- Loading state -->
			<div
				v-if="isLoading"
				class="rounded-2xl border border-slate-200 bg-white p-8 text-center dark:border-slate-700 dark:bg-slate-800"
			>
				<div
					class="inline-flex h-8 w-8 animate-spin rounded-full border-4 border-slate-300 border-t-blue-600 dark:border-slate-600 dark:border-t-blue-400"
				></div>
				<p class="mt-4 text-slate-600 dark:text-slate-400">Loading invitations...</p>
			</div>

			<!-- Error state -->
			<div
				v-else-if="error"
				class="rounded-2xl border border-red-200 bg-red-50 p-6 dark:border-red-900/50 dark:bg-red-900/20"
			>
				<h3 class="font-semibold text-red-900 dark:text-red-200">Error</h3>
				<p class="mt-2 text-sm text-red-700 dark:text-red-300">{{ error }}</p>
				<button
					@click="refreshInvitations"
					class="mt-4 rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-red-700 dark:bg-red-700 dark:hover:bg-red-600"
				>
					Try Again
				</button>
			</div>

			<!-- Empty state -->
			<div
				v-else-if="pendingCount === 0"
				class="rounded-2xl border-2 border-dashed border-slate-300 bg-slate-50 p-12 text-center dark:border-slate-600 dark:bg-slate-800"
			>
				<MailX class="mx-auto h-12 w-12 text-slate-400 dark:text-slate-500" />
				<h3 class="mt-4 font-semibold text-slate-900 dark:text-white">No pending invitations</h3>
				<p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
					You're all caught up! Check back later for new group invitations.
				</p>
			</div>

			<!-- Invitations list -->
			<div v-else class="space-y-4">
				<InvitationCard
					v-for="invitation in pendingInvitations"
					:key="invitation.invitationId"
					:invitation="invitation"
					@accept="handleAccept"
					@decline="handleDecline"
				/>
			</div>

			<!-- Refresh hint -->
			<p class="text-center text-sm text-slate-500 dark:text-slate-400">
				Last updated: {{ lastUpdateTime || "Just now" }}
			</p>
		</div>
	</main>
</template>

<script setup lang="ts">
	import { computed, onMounted, ref } from "vue";
	import { ArrowLeft, MailOpen, MailX } from "lucide-vue-next";
	import { usePendingInvitations } from "~/composables/usePendingInvitations";

	// Define page metadata for authentication middleware
	definePageMeta({
		middleware: "auth",
		roles: ["Student"]
	});

	const {
		fetchInvitations,
		invitations: pendingInvitations,
		count: pendingCount,
		isLoading,
		error,
		lastFetchTime,
		acceptInvitation,
		declineInvitation
	} = usePendingInvitations();

	const lastUpdateTime = ref<string>("");

	/**
	 * Refresh invitations from server
	 */
	const refreshInvitations = async () => {
		await fetchInvitations();
		updateLastUpdateTime();
	};

	/**
	 * Update last update time display
	 */
	const updateLastUpdateTime = () => {
		if (lastFetchTime.value) {
			const now = new Date();
			const diffSeconds = Math.floor((now.getTime() - lastFetchTime.value.getTime()) / 1000);

			if (diffSeconds < 60) {
				lastUpdateTime.value = "Just now";
			} else if (diffSeconds < 3600) {
				const minutes = Math.floor(diffSeconds / 60);
				lastUpdateTime.value = `${minutes}m ago`;
			} else if (diffSeconds < 86400) {
				const hours = Math.floor(diffSeconds / 3600);
				lastUpdateTime.value = `${hours}h ago`;
			} else {
				lastUpdateTime.value = lastFetchTime.value.toLocaleDateString();
			}
		}
	};

	/**
	 * Executes the API call to accept an invitation and updates the timestamp.
	 * Acceptance criteria: Navigation badge updates automatically without page reload
	 * via the shared composable state.
	 */
	const handleAccept = async (invitationId: string) => {
		try {
			await acceptInvitation(invitationId);
			updateLastUpdateTime();
		} catch (err) {
			console.error("Failed to accept invitation:", err);
		}
	};

	/**
	 * Executes the API call to decline an invitation and updates the timestamp.
	 * Acceptance criteria: Navigation badge updates automatically without page reload
	 * via the shared composable state.
	 */
	const handleDecline = async (invitationId: string) => {
		try {
			await declineInvitation(invitationId);
			updateLastUpdateTime();
		} catch (err) {
			console.error("Failed to decline invitation:", err);
		}
	};

	onMounted(async () => {
		await refreshInvitations();
	});
</script>
