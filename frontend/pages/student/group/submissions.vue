<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { AlertCircle, ArrowLeft, Clock3, FileText, Loader2, PencilLine, Send, ShieldCheck, Sparkles } from "lucide-vue-next";
import { useAuthStore } from "~/stores/auth";
import type { StudentDeliverable } from "~/types/submission";
import type { DeliverableSubmissionResponse } from "~/types/deliverable";

import type { GroupDetailResponse, MemberResponse } from "~/types/group";

definePageMeta({
	middleware: "auth",
	roles: ["Student"],
});

type HubState = "loading" | "no-group" | "leader" | "member" | "error";

const { getAuthToken, fetchMyGroup, fetchStudentDeliverables, createDeliverableSubmission, updateDeliverableSubmission } = useApiClient();

const authStore = useAuthStore();

const state = ref<HubState>("loading");
const group = ref<GroupDetailResponse | null>(null);
const deliverables = ref<StudentDeliverable[]>([]);

const selectedDeliverableId = ref("");
const markdownContent = ref("");
const draftCache = ref<Record<string, string>>({});
const submissionByDeliverable = ref<Record<string, DeliverableSubmissionResponse>>({});
const loading = ref(true);
const saving = ref(false);
const formError = ref("");
const formSuccess = ref("");

const currentStudent = computed(() => (
	authStore.userInfo?.userType === "Student" ? authStore.userInfo : null
));

const currentMembership = computed<MemberResponse | null>(() => {
	if (!group.value || !currentStudent.value) {
		return null;
	}

	return group.value.members.find((member) => member.studentId === currentStudent.value?.studentId) || null;
});

const isLeader = computed(() => state.value === "leader");
const isDeadlinePassed = computed(() => {
	if (!selectedDeliverable.value?.submissionDeadline) return false;
	return new Date() > new Date(selectedDeliverable.value.submissionDeadline);
});
const canEdit = computed(() => isLeader.value && group.value?.status === "TOOLS_BOUND" && !!selectedDeliverable.value && !isDeadlinePassed.value);
const selectedDeliverable = computed(() => deliverables.value.find((deliverable) => deliverable.id === selectedDeliverableId.value) || null);
const selectedSubmission = computed(() => submissionByDeliverable.value[selectedDeliverableId.value] || null);

function formatTimeRemaining(deadline?: string | null): string {
	if (!deadline) return "No deadline";
	
	const now = new Date();
	const deadlineDate = new Date(deadline);
	const diff = deadlineDate.getTime() - now.getTime();
	
	if (diff < 0) {
		return "Deadline has passed";
	}
	
	const days = Math.floor(diff / (1000 * 60 * 60 * 24));
	const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
	const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
	
	if (days > 0) {
		return `${days}d ${hours}h remaining`;
	} else if (hours > 0) {
		return `${hours}h ${minutes}m remaining`;
	} else {
		return `${minutes}m remaining`;
	}
}

function parseErrorMessage(error: unknown, fallback: string): string {
	return (
		error &&
		typeof error === "object" &&
		"message" in error &&
		typeof (error as { message?: string }).message === "string"
	)
		? (error as { message: string }).message
		: fallback;
}

function formatDateTime(value?: string | null): string {
	if (!value) {
		return "Not available";
	}

	return new Intl.DateTimeFormat("en-US", {
		dateStyle: "medium",
		timeStyle: "short",
	}).format(new Date(value));
}

function selectDeliverable(deliverableId: string) {
	if (selectedDeliverableId.value) {
		draftCache.value[selectedDeliverableId.value] = markdownContent.value;
	}

	selectedDeliverableId.value = deliverableId;
	markdownContent.value = draftCache.value[deliverableId] || "";
	formError.value = "";
	formSuccess.value = "";
}

function submissionStatusLabel(status?: string | null): string {
	return status === "SUBMITTED" ? "Submitted" : "Not submitted";
}

async function loadGroup() {
	try {
		const token = getAuthToken();
		if (!token) {
			throw new Error("Authentication required. Please log in again.");
		}


		const response = await fetchMyGroup(token);
		group.value = response;
		state.value = currentMembership.value?.role === "TEAM_LEADER" ? "leader" : "member";
	} catch (error: unknown) {
		const apiError = error as { status?: number; message?: string };
		if (apiError.status === 404) {
			state.value = "no-group";
			group.value = null;
			return;
		}

		state.value = "error";
		formError.value = apiError.message || "We couldn't load your group right now.";
		group.value = null;
	}
}

async function loadDeliverables() {
	const token = getAuthToken();
	if (!token) {
		throw new Error("Authentication required. Please log in again.");
	}

	deliverables.value = await fetchStudentDeliverables(token);


	if (!selectedDeliverableId.value && deliverables.value.length > 0) {
		selectDeliverable(deliverables.value[0].id);
	}
}

async function handleSave() {
	if (!selectedDeliverable.value || !group.value || !canEdit.value) {
		return;
	}

	const token = getAuthToken();
	if (!token) {
		formError.value = "Authentication required. Please log in again.";
		return;
	}

	const content = markdownContent.value.trim();
	if (!content) {
		formError.value = "Deliverable content cannot be empty.";
		return;
	}

	saving.value = true;
	formError.value = "";
	formSuccess.value = "";

	try {

		const existingSubmission = selectedSubmission.value;
		const response = existingSubmission
			? await updateDeliverableSubmission(existingSubmission.submissionId, { markdownContent: content }, token)
			: await createDeliverableSubmission(selectedDeliverable.value.id, { markdownContent: content }, token);

		submissionByDeliverable.value[selectedDeliverable.value.id] = response;
		draftCache.value[selectedDeliverable.value.id] = content;
		formSuccess.value = existingSubmission
			? `Submission updated for ${selectedDeliverable.value.name}.`
			: `Submission created for ${selectedDeliverable.value.name}.`;
	} catch (error: unknown) {
		formError.value = parseErrorMessage(error, "Failed to submit the deliverable.");
	} finally {
		saving.value = false;
	}
}

watch(selectedDeliverableId, (newDeliverableId, oldDeliverableId) => {
	if (oldDeliverableId) {
		draftCache.value[oldDeliverableId] = markdownContent.value;
	}

	markdownContent.value = draftCache.value[newDeliverableId] || "";
	formError.value = "";
	formSuccess.value = "";
});

onMounted(async () => {
	loading.value = true;
	try {
		await loadGroup();
		await loadDeliverables();
	} catch (error: unknown) {
		state.value = state.value === "no-group" ? state.value : "error";
		formError.value = parseErrorMessage(error, formError.value || "We couldn't load the submission workspace.");
	} finally {
		loading.value = false;
	}
});
</script>

<template>
	<main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
		<div class="mx-auto w-full max-w-6xl space-y-6">
			<NuxtLink
				to="/student/dashboard"
				class="inline-flex items-center gap-2 text-sm font-medium text-slate-700 transition hover:text-slate-900 dark:text-slate-300 dark:hover:text-white"
			>
				<ArrowLeft class="h-4 w-4" />
				Back to Dashboard
			</NuxtLink>

			<header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
				<div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
					<div>
						<p class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
							Process 6 • Deliverable Submission
						</p>
						<h1 class="mt-2 text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
							Deliverable Workspace
						</h1>
						<p class="mt-2 max-w-3xl text-sm text-slate-600 dark:text-slate-400">
							Write rich-text deliverables, preview embedded images, and submit markdown that the backend can store and grade.
						</p>
					</div>

					<div class="flex flex-wrap items-center gap-2 self-start">
						<span class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">
							<ShieldCheck class="h-3.5 w-3.5" />
							Team Leader only
						</span>
						<span class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">
							<Sparkles class="h-3.5 w-3.5" />
							Image preview supported
						</span>
					</div>
				</div>
			</header>

			<section v-if="state === 'no-group'" class="rounded-2xl border border-amber-300 bg-amber-50 p-6 text-amber-950 shadow-sm dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-100">
				<p class="font-semibold">You are not in a group yet.</p>
				<p class="mt-2 text-sm">Create a group first, then come back here to submit deliverables.</p>
			</section>

			<section v-else-if="state === 'member'" class="rounded-2xl border border-blue-300 bg-blue-50 p-6 text-blue-950 shadow-sm dark:border-blue-800 dark:bg-blue-950/40 dark:text-blue-100">
				<p class="font-semibold">Only the Team Leader can submit deliverables.</p>
				<p class="mt-2 text-sm">You can still use the Group Hub, but submission actions are locked for non-leaders.</p>
			</section>

			<section v-else-if="state === 'error'" class="rounded-2xl border border-red-300 bg-red-50 p-6 shadow-sm dark:border-red-800 dark:bg-red-950/40">
				<div class="flex items-start gap-3">
					<AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
					<div>
						<h2 class="text-lg font-semibold text-red-900 dark:text-red-100">Unable to load submission workspace</h2>
						<p class="mt-2 text-sm text-red-800 dark:text-red-300">{{ formError }}</p>
					</div>
				</div>
			</section>

			<section v-else-if="loading" class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
				<div class="animate-pulse space-y-4">
					<div class="h-6 w-72 rounded bg-slate-200 dark:bg-slate-700"></div>
					<div class="h-4 w-full rounded bg-slate-200 dark:bg-slate-700"></div>
					<div class="h-72 rounded-2xl bg-slate-100 dark:bg-slate-900"></div>
				</div>
			</section>

			<template v-else-if="group">
				<section class="grid gap-6 xl:grid-cols-[320px_minmax(0,1fr)]">
					<aside class="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
						<div>
							<p class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">Deliverables</p>
							<h2 class="mt-2 text-lg font-semibold text-slate-900 dark:text-white">Select a document</h2>
						</div>

						<div v-if="deliverables.length === 0" class="rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-900/50 dark:text-slate-400">
							No deliverables have been configured yet.
						</div>

						<button
							v-for="deliverable in deliverables"
							:key="deliverable.id"
							type="button"
							class="w-full rounded-2xl border p-4 text-left transition"
							:class="selectedDeliverableId === deliverable.id ? 'border-blue-300 bg-blue-50 dark:border-blue-700 dark:bg-blue-950/30' : 'border-slate-200 bg-white hover:border-slate-300 dark:border-slate-700 dark:bg-slate-900/50 dark:hover:border-slate-600'"
							@click="selectDeliverable(deliverable.id)"
						>
							<div class="flex items-start justify-between gap-3">
								<div>
									<p class="font-semibold text-slate-900 dark:text-white">{{ deliverable.name }}</p>
									<p class="mt-1 text-xs text-slate-500 dark:text-slate-400">{{ deliverable.type }}</p>
								</div>
								<span class="rounded-full px-2.5 py-1 text-[11px] font-semibold" :class="deliverable.submissionStatus === 'SUBMITTED' ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-200' : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300'">
									{{ submissionStatusLabel(deliverable.submissionStatus) }}
								</span>
							</div>
							<div class="mt-3 flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
								<Clock3 class="h-3.5 w-3.5" />
								Submission: {{ formatDateTime(deliverable.submissionDeadline) }}
							</div>
						</button>
					</aside>

					<div class="space-y-6">
						<section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
							<div v-if="selectedDeliverable" class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
								<div>
									<div class="flex flex-wrap items-center gap-2">
										<FileText class="h-5 w-5 text-blue-600 dark:text-blue-400" />
										<h2 class="text-2xl font-semibold text-slate-900 dark:text-white">{{ selectedDeliverable.name }}</h2>
									</div>
									<p class="mt-2 text-sm text-slate-600 dark:text-slate-400">{{ selectedDeliverable.type }} • Due {{ formatDateTime(selectedDeliverable.submissionDeadline) }}</p>
								</div>
								<div class="flex flex-col gap-4 sm:flex-row">
									<div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700 dark:border-slate-700 dark:bg-slate-900/50 dark:text-slate-300">
										<p class="font-semibold text-slate-900 dark:text-white">Current status</p>
										<p class="mt-1">{{ submissionStatusLabel(selectedDeliverable.submissionStatus) }}</p>
										<p v-if="selectedSubmission" class="mt-1 text-xs text-slate-500 dark:text-slate-400">Saved submission ID: {{ selectedSubmission.submissionId }}</p>
									</div>
									<div :class="[
										'rounded-2xl border px-4 py-3 text-sm font-semibold',
										isDeadlinePassed 
											? 'border-red-300 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950/40 dark:text-red-200'
											: 'border-green-300 bg-green-50 text-green-700 dark:border-green-800 dark:bg-green-950/40 dark:text-green-200'
									]">
										<div class="flex items-center gap-2">
											<Clock3 class="h-4 w-4" />
											<span>{{ formatTimeRemaining(selectedDeliverable.submissionDeadline) }}</span>
										</div>
									</div>
								</div>
							</div>
							<div v-else class="rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-900/50 dark:text-slate-400">
								Pick a deliverable from the left to start writing.
							</div>
						</section>

						<section v-if="selectedDeliverable" class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
							<div class="mb-4 flex items-center justify-between gap-3">
								<div>
									<h3 class="text-lg font-semibold text-slate-900 dark:text-white">Editor</h3>
									<p class="mt-1 text-sm text-slate-600 dark:text-slate-400">Use the toolbar for headings, lists, code, links, and embedded images.</p>
								</div>
								<span class="inline-flex items-center gap-2 rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-semibold text-blue-800 dark:border-blue-800 dark:bg-blue-950/40 dark:text-blue-200">
									<PencilLine class="h-3.5 w-3.5" />
									Markdown syncs live
								</span>
							</div>

							<div v-if="isDeadlinePassed && isLeader" class="mb-4 rounded-xl border border-red-300 bg-red-50 p-4 text-sm text-red-800 dark:border-red-800 dark:bg-red-950/40 dark:text-red-300">
								<p class="font-semibold">Submission deadline has passed</p>
								<p class="mt-1">The deadline for this deliverable was {{ formatDateTime(selectedDeliverable.submissionDeadline) }}. You can no longer submit or update this deliverable.</p>
							</div>
							<div v-if="formError" class="mb-4 rounded-xl border border-red-300 bg-red-50 p-4 text-sm text-red-800 dark:border-red-800 dark:bg-red-950/40 dark:text-red-300">{{ formError }}</div>
							<div v-if="formSuccess" class="mb-4 rounded-xl border border-emerald-300 bg-emerald-50 p-4 text-sm text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300">{{ formSuccess }}</div>

							<ClientOnly>
								<DeliverableMarkdownEditor v-model="markdownContent" height="620px" />
							</ClientOnly>

							<div class="mt-6 flex flex-col gap-3 border-t border-slate-200 pt-6 dark:border-slate-700 sm:flex-row sm:items-center sm:justify-between">
								<div class="text-xs text-slate-500 dark:text-slate-400">Images are stored as embedded base64 data when inserted in the editor.</div>
								<button
									type="button"
									class="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
									:disabled="saving || !canEdit"
									:title="isDeadlinePassed ? 'Submission deadline has passed' : !isLeader ? 'Only Team Leaders can submit' : ''"
									@click="handleSave"
								>
									<Loader2 v-if="saving" class="h-4 w-4 animate-spin" />
									<Send v-else class="h-4 w-4" />
									{{ saving ? (selectedSubmission ? 'Updating...' : 'Submitting...') : (selectedSubmission ? 'Update Submission' : 'Submit Deliverable') }}
								</button>
							</div>

							<div v-if="selectedSubmission" class="mt-6">
								<SubmissionCommentThread
									:submission-id="selectedSubmission.submissionId"
									:can-comment="false"
									title="Committee comments"
									description="Read-only feedback from the committee. Students can view comments but cannot reply here."
								/>
							</div>
						</section>
					</div>
				</section>
			</template>
		</div>
	</main>
</template>