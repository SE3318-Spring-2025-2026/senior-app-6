
<script setup lang="ts">
	import { computed, onMounted, ref } from "vue";
	import { AlertCircle, ArrowLeft, MailOpen, MailX, Search, Send, UserRoundPlus, XCircle } from "lucide-vue-next";
	import { usePendingInvitations } from "~/composables/usePendingInvitations";
	import type { GroupDetailResponse } from "~/types/group";
	import { useAuthStore } from "~/stores/auth";
import type { SentGroupInvitation } from "~/types/invitation";
import type { StudentSearchResult } from "~/types/student";

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

	const {
		getAuthToken,
		fetchMyGroup,
		searchStudents,
		sendGroupInvitation,
		fetchGroupInvitations,
		cancelGroupInvitation
	} = useApiClient();

	const authStore = useAuthStore();

	// ─── Group + leader state ────────────────────────────────────────────
	const myGroup = ref<GroupDetailResponse | null>(null);

	const isLeader = computed(() => {
		if (!myGroup.value || !authStore.userInfo) return false;
		const userInfo = authStore.userInfo;
		const me = myGroup.value.members.find(
			(m) => m.studentId === (userInfo.userType === "Student" ? userInfo.studentId : undefined)
		);
		return me?.role === "TEAM_LEADER";
	});

	const canSendInvitations = computed(() => {
		if (!myGroup.value) return false;
		const { status } = myGroup.value;
		return status === "FORMING" || status === "TOOLS_PENDING";
	});

	const sendBlockedReason = computed(() => {
		if (!myGroup.value) return "";
		switch (myGroup.value.status) {
			case "DISBANDED": return "This group has been disbanded.";
			case "TOOLS_BOUND":
			case "ADVISOR_ASSIGNED": return "Roster is locked after tool binding.";
			default: return "";
		}
	});

	// ─── Search state ────────────────────────────────────────────────────
	const searchQuery = ref("");
	const searchResults = ref<StudentSearchResult[]>([]);
	const isSearching = ref(false);
	const selectedStudent = ref<StudentSearchResult | null>(null);
	const sendError = ref("");
	const sendSuccess = ref(false);
	const isSending = ref(false);
	let searchTimeout: ReturnType<typeof setTimeout> | null = null;

	const onSearchInput = () => {
		sendError.value = "";
		sendSuccess.value = false;
		selectedStudent.value = null;
		searchResults.value = [];
		if (searchTimeout) clearTimeout(searchTimeout);
		if (searchQuery.value.trim().length < 3) return;
		searchTimeout = setTimeout(async () => {
			isSearching.value = true;
			try {
				const token = getAuthToken();
				searchResults.value = await searchStudents(searchQuery.value.trim(), token ?? undefined);
			} catch {
				searchResults.value = [];
			} finally {
				isSearching.value = false;
			}
		}, 300);
	};

	const selectStudent = (student: StudentSearchResult) => {
		selectedStudent.value = student;
		searchQuery.value = student.studentId;
		searchResults.value = [];
	};

	const clearSelection = () => {
		selectedStudent.value = null;
		searchQuery.value = "";
		searchResults.value = [];
	};

	// ─── Send invitation ─────────────────────────────────────────────────
	const sentInvitations = ref<SentGroupInvitation[]>([]);
	const isSentLoading = ref(false);

	const handleSendInvitation = async () => {
		if (!selectedStudent.value || !myGroup.value) return;
		isSending.value = true;
		sendError.value = "";
		sendSuccess.value = false;
		try {
			const token = getAuthToken();
			await sendGroupInvitation(myGroup.value.id, selectedStudent.value.studentId, token ?? undefined);
			sendSuccess.value = true;
			clearSelection();
			await loadSentInvitations();
		} catch (err: unknown) {
			const apiErr = err as { message?: string };
			sendError.value = apiErr?.message ?? "Failed to send invitation.";
		} finally {
			isSending.value = false;
		}
	};

	// ─── Sent invitations list ───────────────────────────────────────────
	const loadSentInvitations = async () => {
		if (!myGroup.value) return;
		isSentLoading.value = true;
		try {
			const token = getAuthToken();
			sentInvitations.value = await fetchGroupInvitations(myGroup.value.id, token ?? undefined);
		} catch {
			sentInvitations.value = [];
		} finally {
			isSentLoading.value = false;
		}
	};

	const cancellingId = ref<string | null>(null);

	const handleCancelInvitation = async (invitationId: string) => {
		cancellingId.value = invitationId;
		try {
			const token = getAuthToken();
			await cancelGroupInvitation(invitationId, token ?? undefined);
			await loadSentInvitations();
		} catch {
			// list unchanged — user can retry
		} finally {
			cancellingId.value = null;
		}
	};

	// ─── Status badge helpers ─────────────────────────────────────────────
	const statusClass = (status: string) => {
		switch (status) {
			case "PENDING":
				return "bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300";
			case "ACCEPTED":
				return "bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300";
			default:
				return "bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-400";
		}
	};

	const formatDate = (iso: string) =>
		new Date(iso).toLocaleDateString(undefined, {
			month: "short",
			day: "numeric",
			hour: "2-digit",
			minute: "2-digit"
		});

	// ─── Inbox ────────────────────────────────────────────────────────────
	const lastUpdateTime = ref<string>("");

	const refreshInvitations = async () => {
		await fetchInvitations();
		updateLastUpdateTime();
	};

	const updateLastUpdateTime = () => {
		if (lastFetchTime.value) {
			const diffSeconds = Math.floor((new Date().getTime() - lastFetchTime.value.getTime()) / 1000);
			if (diffSeconds < 60) lastUpdateTime.value = "Just now";
			else if (diffSeconds < 3600) lastUpdateTime.value = `${Math.floor(diffSeconds / 60)}m ago`;
			else if (diffSeconds < 86400) lastUpdateTime.value = `${Math.floor(diffSeconds / 3600)}h ago`;
			else lastUpdateTime.value = lastFetchTime.value.toLocaleDateString();
		}
	};

	const handleAccept = async (invitationId: string) => {
		try {
			await acceptInvitation(invitationId);
			updateLastUpdateTime();
		} catch (err) {
			console.error("Failed to accept invitation:", err);
		}
	};

	const handleDecline = async (invitationId: string) => {
		try {
			await declineInvitation(invitationId);
			updateLastUpdateTime();
		} catch (err) {
			console.error("Failed to decline invitation:", err);
		}
	};

	// ─── Mount ────────────────────────────────────────────────────────────
	onMounted(async () => {
		await refreshInvitations();
		try {
			const token = getAuthToken();
			myGroup.value = await fetchMyGroup(token ?? undefined);
			if (isLeader.value) {
				await loadSentInvitations();
			}
		} catch {
			// no group or not in group — leader section hidden
		}
	});
</script>

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
							Manage your group invitations.
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

			<!-- ── LEADER SECTION ─────────────────────────────────────────────── -->
			<template v-if="isLeader">

				<!-- Send Invitation card -->
				<section
					class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg"
				>
					<div class="mb-4 flex items-center gap-3">
						<UserRoundPlus class="h-5 w-5 text-blue-600 dark:text-blue-400" />
						<h2 class="text-lg font-semibold text-slate-900 dark:text-white">Send Invitation</h2>
					</div>

					<!-- Locked notice -->
					<div
						v-if="!canSendInvitations"
						class="flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-800/40 dark:bg-amber-900/20 dark:text-amber-300"
					>
						<AlertCircle class="h-4 w-4 shrink-0" />
						{{ sendBlockedReason }}
					</div>

					<!-- Search + send form -->
					<div v-else class="space-y-4">
						<!-- Search input with dropdown -->
						<div class="relative">
							<div class="relative">
								<Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
								<input
									v-model="searchQuery"
									type="text"
									placeholder="Search by student ID (min 3 characters)…"
									class="w-full rounded-lg border border-slate-200 bg-white py-2.5 pl-9 pr-4 text-sm text-slate-900 placeholder-slate-400 transition focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:placeholder-slate-400 dark:focus:border-blue-400"
									@input="onSearchInput"
								/>
							</div>

							<!-- Dropdown results -->
							<div
								v-if="isSearching || searchResults.length > 0"
								class="absolute z-10 mt-1 w-full rounded-lg border border-slate-200 bg-white shadow-lg dark:border-slate-600 dark:bg-slate-800"
							>
								<div
									v-if="isSearching"
									class="px-4 py-3 text-sm text-slate-500 dark:text-slate-400"
								>
									Searching…
								</div>
								<button
									v-for="student in searchResults"
									:key="student.studentId"
									type="button"
									class="flex w-full items-center gap-3 px-4 py-3 text-left text-sm transition hover:bg-slate-50 dark:hover:bg-slate-700/60"
									@click="selectStudent(student)"
								>
									<span class="font-mono font-medium text-slate-900 dark:text-white">{{ student.studentId }}</span>
									<span v-if="student.githubUsername" class="text-slate-500 dark:text-slate-400">
										({{ student.githubUsername }})
									</span>
								</button>
							</div>
						</div>

						<!-- Selected student chip + Send button -->
						<div v-if="selectedStudent" class="flex items-center gap-3">
							<div class="flex items-center gap-2 rounded-lg bg-blue-50 px-3 py-2 dark:bg-blue-900/30">
								<span class="font-mono text-sm font-medium text-blue-800 dark:text-blue-300">
									{{ selectedStudent.studentId }}
								</span>
								<span v-if="selectedStudent.githubUsername" class="text-xs text-blue-600 dark:text-blue-400">
									({{ selectedStudent.githubUsername }})
								</span>
								<button
									type="button"
									class="ml-1 text-blue-500 hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-200"
									@click="clearSelection"
								>
									<XCircle class="h-4 w-4" />
								</button>
							</div>
							<button
								type="button"
								:disabled="isSending"
								class="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-blue-600 dark:hover:bg-blue-500"
								@click="handleSendInvitation"
							>
								<Send class="h-4 w-4" />
								{{ isSending ? "Sending…" : "Send Invitation" }}
							</button>
						</div>

						<!-- Feedback -->
						<p v-if="sendError" class="text-sm text-red-600 dark:text-red-400">{{ sendError }}</p>
						<p v-if="sendSuccess" class="text-sm text-green-600 dark:text-green-400">
							Invitation sent successfully.
						</p>
					</div>
				</section>

				<!-- Sent Invitations list -->
				<section
					class="rounded-2xl border border-slate-200 bg-white/90 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg"
				>
					<div class="border-b border-slate-100 p-6 dark:border-slate-700">
						<div class="flex items-center gap-3">
							<Send class="h-5 w-5 text-slate-500 dark:text-slate-400" />
							<h2 class="text-lg font-semibold text-slate-900 dark:text-white">Sent Invitations</h2>
						</div>
					</div>

					<!-- Loading -->
					<div
						v-if="isSentLoading"
						class="p-8 text-center"
					>
						<div class="inline-flex h-6 w-6 animate-spin rounded-full border-4 border-slate-300 border-t-blue-600 dark:border-slate-600 dark:border-t-blue-400"></div>
						<p class="mt-3 text-sm text-slate-500 dark:text-slate-400">Loading…</p>
					</div>

					<!-- Empty -->
					<div
						v-else-if="sentInvitations.length === 0"
						class="p-8 text-center"
					>
						<p class="text-sm text-slate-500 dark:text-slate-400">No invitations sent yet.</p>
					</div>

					<!-- List -->
					<ul v-else class="divide-y divide-slate-100 dark:divide-slate-700">
						<li
							v-for="inv in sentInvitations"
							:key="inv.invitationId"
							class="flex items-center justify-between gap-4 px-6 py-4"
						>
							<div class="min-w-0 space-y-0.5">
								<p class="font-mono text-sm font-medium text-slate-900 dark:text-white">
									{{ inv.targetStudentId }}
								</p>
								<p class="text-xs text-slate-500 dark:text-slate-400">
									Sent {{ formatDate(inv.sentAt) }}
									<template v-if="inv.respondedAt">
										· Responded {{ formatDate(inv.respondedAt) }}
									</template>
								</p>
							</div>
							<div class="flex shrink-0 items-center gap-3">
								<span
									class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
									:class="statusClass(inv.status)"
								>
									{{ inv.status }}
								</span>
								<button
									v-if="inv.status === 'PENDING'"
									type="button"
									:disabled="cancellingId === inv.invitationId"
									class="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-600 transition hover:border-red-300 hover:bg-red-50 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:border-red-600 dark:hover:bg-red-900/20 dark:hover:text-red-400"
									@click="handleCancelInvitation(inv.invitationId)"
								>
									<XCircle class="h-3 w-3" />
									{{ cancellingId === inv.invitationId ? "Cancelling…" : "Cancel" }}
								</button>
							</div>
						</li>
					</ul>
				</section>

			</template>

			<!-- ── INBOX (all students) ───────────────────────────────────────── -->
			<section>
				<div class="mb-3 flex items-center gap-3 px-1">
					<MailOpen class="h-5 w-5 text-slate-500 dark:text-slate-400" />
					<h2 class="text-base font-semibold text-slate-700 dark:text-slate-300">Received Invitations</h2>
				</div>

				<!-- Loading -->
				<div
					v-if="isLoading"
					class="rounded-2xl border border-slate-200 bg-white p-8 text-center dark:border-slate-700 dark:bg-slate-800"
				>
					<div class="inline-flex h-8 w-8 animate-spin rounded-full border-4 border-slate-300 border-t-blue-600 dark:border-slate-600 dark:border-t-blue-400"></div>
					<p class="mt-4 text-slate-600 dark:text-slate-400">Loading invitations…</p>
				</div>

				<!-- Error -->
				<div
					v-else-if="error"
					class="rounded-2xl border border-red-200 bg-red-50 p-6 dark:border-red-900/50 dark:bg-red-900/20"
				>
					<h3 class="font-semibold text-red-900 dark:text-red-200">Error</h3>
					<p class="mt-2 text-sm text-red-700 dark:text-red-300">{{ error }}</p>
					<button
						class="mt-4 rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-red-700 dark:bg-red-700 dark:hover:bg-red-600"
						@click="refreshInvitations"
					>
						Try Again
					</button>
				</div>

				<!-- Empty -->
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

				<!-- Invitation cards -->
				<div v-else class="space-y-4">
					<InvitationCard
						v-for="invitation in pendingInvitations"
						:key="invitation.invitationId"
						:invitation="invitation"
						@accept="handleAccept"
						@decline="handleDecline"
					/>
				</div>
			</section>

			<p class="text-center text-sm text-slate-500 dark:text-slate-400">
				Last updated: {{ lastUpdateTime || "Just now" }}
			</p>
		</div>
	</main>
</template>
