/**
 * Composable for managing pending invitations count and real-time updates
 * Used by layout to display persistent notification badge
 */

import { ref, computed } from "vue";
import { useApiClient, type GroupInvitation } from "./useApiClient";
import { useAuthStore } from "~/stores/auth";

const pendingInvitations = ref<GroupInvitation[]>([]);
const isLoading = ref(false);
const error = ref<string | null>(null);
const lastFetchTime = ref<Date | null>(null);

export const usePendingInvitations = () => {
	const apiClient = useApiClient();
	const authStore = useAuthStore();

	/**
	 * Fetch pending invitations from the server
	 */
	const fetchInvitations = async () => {
		if (isLoading.value) return;

		try {
			isLoading.value = true;
			error.value = null;

			const token = authStore.token;
			if (!token) {
				pendingInvitations.value = [];
				return;
			}

			const invitations = await apiClient.fetchPendingInvitations(token);
			pendingInvitations.value = invitations;
			lastFetchTime.value = new Date();
		} catch (err: unknown) {
			const errorMessage = err instanceof Error ? err.message : "Failed to fetch invitations";
			error.value = errorMessage;
			console.error("Error fetching pending invitations:", err);
		} finally {
			isLoading.value = false;
		}
	};

	/**
	 * Accept an invitation and update the local state
	 */
	const acceptInvitation = async (invitationId: string) => {
		try {
			const token = authStore.token;
			if (!token) throw new Error("Not authenticated");

			await apiClient.respondToInvitation(invitationId, "ACCEPT", token);

			// Remove from pending list
			pendingInvitations.value = pendingInvitations.value.filter((inv) => inv.id !== invitationId);
		} catch (err: unknown) {
			const errorMessage = err instanceof Error ? err.message : "Failed to accept invitation";
			error.value = errorMessage;
			console.error("Error accepting invitation:", err);
			throw err;
		}
	};

	/**
	 * Decline an invitation and update the local state
	 */
	const declineInvitation = async (invitationId: string) => {
		try {
			const token = authStore.token;
			if (!token) throw new Error("Not authenticated");

			await apiClient.respondToInvitation(invitationId, "DECLINE", token);

			// Remove from pending list
			pendingInvitations.value = pendingInvitations.value.filter((inv) => inv.id !== invitationId);
		} catch (err: unknown) {
			const errorMessage = err instanceof Error ? err.message : "Failed to decline invitation";
			error.value = errorMessage;
			console.error("Error declining invitation:", err);
			throw err;
		}
	};

	/**
	 * Get the count of pending invitations
	 */
	const count = computed(() => pendingInvitations.value.length);

	/**
	 * Check if there are pending invitations
	 */
	const hasPending = computed(() => count.value > 0);

	/**
	 * Get all pending invitations
	 */
	const invitations = computed(() => pendingInvitations.value);

	return {
		// State
		pendingInvitations,
		isLoading,
		error,
		lastFetchTime,

		// Computed
		count,
		hasPending,
		invitations,

		// Methods
		fetchInvitations,
		acceptInvitation,
		declineInvitation
	};
};
