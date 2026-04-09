<template>
	<div
		class="rounded-lg border border-slate-200 bg-white p-4 shadow-sm transition-all dark:border-slate-700 dark:bg-slate-800"
	>
		<!-- Header with sender info -->
		<div class="mb-3 flex items-start justify-between">
			<div>
				<h3 class="font-semibold text-slate-900 dark:text-white">
					{{ invitation.groupName }}
				</h3>
				<p class="text-sm text-slate-600 dark:text-slate-400">
					Invited by {{ invitation.senderName || "a team member" }}
				</p>
			</div>
			<span
				class="inline-flex items-center rounded-full bg-blue-100 px-2.5 py-0.5 text-xs font-medium text-blue-800 dark:bg-blue-900/30 dark:text-blue-300"
			>
				{{ invitation.status }}
			</span>
		</div>

		<!-- Invitation time -->
		<p class="mb-4 text-xs text-slate-500 dark:text-slate-400">
			{{ formatDate(invitation.sentAt) }}
		</p>

		<!-- Action buttons -->
		<div class="flex gap-2">
			<button
				@click="handleAccept"
				:disabled="isProcessing || invitation.status !== 'PENDING'"
				class="flex-1 rounded-md bg-emerald-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed dark:bg-emerald-700 dark:hover:bg-emerald-600"
			>
				<span
					v-if="isProcessing && activeAction === 'accept'"
					class="flex items-center justify-center gap-1"
				>
					<div
						class="h-3 w-3 animate-spin rounded-full border-2 border-white border-t-transparent"
					></div>
					Accepting...
				</span>
				<span v-else> Accept </span>
			</button>

			<button
				@click="handleDecline"
				:disabled="isProcessing || invitation.status !== 'PENDING'"
				class="flex-1 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
			>
				<span
					v-if="isProcessing && activeAction === 'decline'"
					class="flex items-center justify-center gap-1"
				>
					<div
						class="h-3 w-3 animate-spin rounded-full border-2 border-slate-700 border-t-transparent dark:border-slate-300"
					></div>
					Declining...
				</span>
				<span v-else> Decline </span>
			</button>
		</div>

		<!-- Error message -->
		<p v-if="errorMessage" class="mt-2 text-xs text-red-600 dark:text-red-400">
			{{ errorMessage }}
		</p>
	</div>
</template>

<script setup lang="ts">
	import { ref } from "vue";
	import { usePendingInvitations } from "~/composables/usePendingInvitations";
	import type { GroupInvitation } from "~/composables/useApiClient";

	interface Props {
		invitation: GroupInvitation;
	}

	defineProps<Props>();

	const emit = defineEmits<{
		accepted: [invitationId: string];
		declined: [invitationId: string];
	}>();

	const { acceptInvitation, declineInvitation } = usePendingInvitations();

	const isProcessing = ref(false);
	const activeAction = ref<"accept" | "decline" | null>(null);
	const errorMessage = ref<string>("");

	/**
	 * Format date to readable string
	 */
	const formatDate = (dateString: string) => {
		try {
			const date = new Date(dateString);
			return date.toLocaleDateString("en-US", {
				month: "short",
				day: "numeric",
				year: date.getFullYear() !== new Date().getFullYear() ? "numeric" : undefined,
				hour: "2-digit",
				minute: "2-digit"
			});
		} catch {
			return dateString;
		}
	};

	/**
	 * Handle accept button click
	 * Acceptance criteria: buttons disable during API calls
	 */
	const handleAccept = async () => {
		if (isProcessing.value) return;

		try {
			isProcessing.value = true;
			activeAction.value = "accept";
			errorMessage.value = "";

			await acceptInvitation(invitation.id);
			emit("accepted", invitation.id);
		} catch (err: unknown) {
			const errorMsg = err instanceof Error ? err.message : "Failed to accept invitation";
			errorMessage.value = errorMsg;
		} finally {
			isProcessing.value = false;
			activeAction.value = null;
		}
	};

	/**
	 * Handle decline button click
	 * Acceptance criteria: buttons disable during API calls
	 */
	const handleDecline = async () => {
		if (isProcessing.value) return;

		try {
			isProcessing.value = true;
			activeAction.value = "decline";
			errorMessage.value = "";

			await declineInvitation(invitation.id);
			emit("declined", invitation.id);
		} catch (err: unknown) {
			const errorMsg = err instanceof Error ? err.message : "Failed to decline invitation";
			errorMessage.value = errorMsg;
		} finally {
			isProcessing.value = false;
			activeAction.value = null;
		}
	};
</script>
