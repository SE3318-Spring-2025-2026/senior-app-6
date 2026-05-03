<script setup lang="ts">
	import {
		AlertCircle,
		ArrowLeft,
		Calendar,
		ChevronDown,
		ChevronUp,
		Clock,
		Eye,
		Loader2,
		Scale,
		Users,
		UserCheck,
	} from "lucide-vue-next";
import type { ProfessorCommittee, ProfessorCommitteeRubricCriterion } from "~/types/committee";
import type { Deliverable } from "~/types/deliverable";

	definePageMeta({
		middleware: "auth",
		roles: ["Professor"],
	});

interface DeadlineInfo
{
	deliverableName: string;
	type: string;
	date: Date;
	dateStr: string
}

	const { getAuthToken, fetchProfessorCommittees, fetchDeliverables, fetchRubric } = useApiClient();

	const committees = ref<ProfessorCommittee[]>([]);
	const deliverables = ref<Deliverable[]>([]);
	const rubricsByDeliverable = ref<Record<string, ProfessorCommitteeRubricCriterion[]>>({});
	const loading = ref(true);
	const loadError = ref<string | null>(null);
	const expandedCommittees = ref<Set<string>>(new Set());
	const expandedDeliverables = ref<Set<string>>(new Set());

	const upcomingDeadlines = computed(() => {
		const now = new Date();
		return deliverables.value
			.map((d) => {
				const submission = new Date(d.submissionDeadline);
				const review = new Date(d.reviewDeadline);
				const deadlines: DeadlineInfo[] = [];
				if (submission > now) {
					deadlines.push({
						deliverableName: d.name,
						type: "Submission",
						date: submission,
						dateStr: d.submissionDeadline,
					});
				}
				if (review > now) {
					deadlines.push({
						deliverableName: d.name,
						type: "Review",
						date: review,
						dateStr: d.reviewDeadline,
					});
				}
				return deadlines;
			})
			.flat()
			.sort((a: DeadlineInfo, b: DeadlineInfo) => a.date.getTime() - b.date.getTime());
	});

	function toggleCommittee(id: string) {
		const next = new Set(expandedCommittees.value);
		if (next.has(id)) {
			next.delete(id);
		} else {
			next.add(id);
		}
		expandedCommittees.value = next;
	}

	function toggleDeliverable(id: string) {
		const next = new Set(expandedDeliverables.value);
		if (next.has(id)) {
			next.delete(id);
		} else {
			next.add(id);
			if (!rubricsByDeliverable.value[id]) {
				loadRubric(id);
			}
		}
		expandedDeliverables.value = next;
	}

	async function loadRubric(deliverableId: string) {
		try {
			const token = getAuthToken();
			if (!token) return;
			const criteria = await fetchRubric(deliverableId, token);
			rubricsByDeliverable.value[deliverableId] = criteria.map((c) => ({
				criterionName: c.criterionName,
				gradingType: c.gradingType,
				weight: c.weight,
			}));
		} catch {
			rubricsByDeliverable.value[deliverableId] = [];
		}
	}

	function formatDate(dateStr: string): string {
		return new Intl.DateTimeFormat("en-US", {
			dateStyle: "medium",
			timeStyle: "short",
		}).format(new Date(dateStr));
	}

	function daysUntil(dateStr: string): number {
		const now = new Date();
		const target = new Date(dateStr);
		return Math.ceil((target.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
	}

	function deadlineUrgency(dateStr: string): string {
		const days = daysUntil(dateStr);
		if (days <= 3) return "text-red-600 dark:text-red-400";
		if (days <= 7) return "text-amber-600 dark:text-amber-400";
		return "text-slate-600 dark:text-slate-400";
	}

	async function loadData() {
		loading.value = true;
		loadError.value = null;
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required");

			const [committeeList, deliverableList] = await Promise.all([
				fetchProfessorCommittees(token),
				fetchDeliverables(token),
			]);
			committees.value = committeeList;
			deliverables.value = deliverableList;

			// Expand first committee by default
			if (committeeList.length > 0) {
				expandedCommittees.value = new Set([committeeList[0].committeeId]);
			}
		} catch (err: unknown) {
			const msg =
				err && typeof err === "object" && "message" in err
					? String(err.message)
					: "Failed to load committee data.";
			loadError.value = msg;
		} finally {
			loading.value = false;
		}
	}

	onMounted(loadData);
</script>

<template>
  <main
    class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8"
  >
    <div class="mx-auto w-full max-w-5xl space-y-6">
      <!-- Header -->
      <header
        class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg"
      >
        <div class="flex items-center justify-between">
          <div>
            <h1
              class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl"
            >
              My Committees
            </h1>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              View your committee assignments, student groups, and upcoming deadlines.
            </p>
          </div>
          <NuxtLink
            to="/professor/dashboard"
            class="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
          >
            <ArrowLeft class="mr-2 inline h-4 w-4" />
            Back
          </NuxtLink>
        </div>
      </header>

      <!-- Loading -->
      <div
        v-if="loading"
        class="flex items-center justify-center rounded-2xl border border-slate-200 bg-white p-12 dark:border-slate-700 dark:bg-slate-800"
      >
        <Loader2 class="h-6 w-6 animate-spin text-blue-600 dark:text-blue-400" />
        <span class="ml-3 text-sm text-slate-600 dark:text-slate-400">Loading committees…</span>
      </div>

      <!-- Error -->
      <div
        v-else-if="loadError"
        class="flex items-start gap-3 rounded-2xl border border-red-300 bg-red-50 p-6 text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300"
      >
        <AlertCircle class="mt-0.5 h-5 w-5 shrink-0" />
        <div>
          <p class="font-medium">Failed to load data</p>
          <p class="mt-1 text-sm">{{ loadError }}</p>
        </div>
      </div>

      <template v-else>
        <!-- Upcoming Deadlines -->
        <section
          v-if="upcomingDeadlines.length > 0"
          class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800"
        >
          <h2 class="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
            <Calendar class="h-5 w-5 text-blue-600 dark:text-blue-400" />
            Upcoming Deadlines
          </h2>
          <div class="mt-4 divide-y divide-slate-100 dark:divide-slate-700">
            <div
              v-for="(deadline, idx) in upcomingDeadlines.slice(0, 6)"
              :key="idx"
              class="flex items-center justify-between py-3 first:pt-0 last:pb-0"
            >
              <div>
                <span class="text-sm font-medium text-slate-900 dark:text-white">
                  {{ deadline.deliverableName }}
                </span>
                <span
                  :class="[
                    'ml-2 inline-flex rounded-full px-2 py-0.5 text-xs font-medium',
                    deadline.type === 'Submission'
                      ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300'
                      : 'bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300',
                  ]"
                >
                  {{ deadline.type }}
                </span>
              </div>
              <div class="flex items-center gap-2 text-sm">
                <Clock class="h-3.5 w-3.5" :class="deadlineUrgency(deadline.dateStr)" aria-hidden="true" />
                <span :class="deadlineUrgency(deadline.dateStr)">
                  {{ formatDate(deadline.dateStr) }}
                  <span class="ml-1 text-xs">({{ daysUntil(deadline.dateStr) }}d)</span>
                </span>
              </div>
            </div>
          </div>
        </section>

        <!-- Empty state -->
        <div
          v-if="committees.length === 0"
          class="flex flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 bg-white py-16 dark:border-slate-600 dark:bg-slate-800"
        >
          <Users class="h-12 w-12 text-slate-300 dark:text-slate-600" />
          <p class="mt-4 text-sm text-slate-500 dark:text-slate-400">
            You are not assigned to any committees yet.
          </p>
        </div>

        <!-- Committee list -->
        <section
          v-for="committee in committees"
          :key="committee.committeeId"
          class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800"
        >
          <!-- Committee header -->
          <button
            type="button"
            @click="toggleCommittee(committee.committeeId)"
            class="flex w-full items-center justify-between p-6 text-left transition hover:bg-slate-50 dark:hover:bg-slate-700"
          >
            <div class="flex items-center gap-4">
              <div
                :class="[
                  'flex h-12 w-12 shrink-0 items-center justify-center rounded-xl border',
                  committee.professorRole === 'ADVISOR'
                    ? 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-400'
                    : 'border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-700 dark:bg-blue-950/50 dark:text-blue-400',
                ]"
              >
                <!-- translate-x-0.5 visually centers the person's head since the checkmark shifts the SVG bounds right -->
                <UserCheck v-if="committee.professorRole === 'ADVISOR'" class="h-[1.375rem] w-[1.375rem] translate-x-0.5" />
                <Scale v-else class="h-[1.375rem] w-[1.375rem]" />
              </div>
              <div class="flex flex-col justify-center">
                <h3 class="text-base font-semibold leading-none text-slate-900 dark:text-white mt-0.5">
                  {{ committee.committeeName }}
                </h3>
                <div class="mt-1.5 flex items-center gap-3 text-xs text-slate-500 dark:text-slate-400">
                  <span
                    :class="[
                      'rounded-full px-2 py-0.5 text-xs font-medium',
                      committee.professorRole === 'ADVISOR'
                        ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300'
                        : 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300',
                    ]"
                  >
                    {{ committee.professorRole }}
                  </span>
                  <span>{{ committee.groups.length }} group{{ committee.groups.length !== 1 ? 's' : '' }}</span>
                </div>
              </div>
            </div>
            <ChevronUp
              v-if="expandedCommittees.has(committee.committeeId)"
              class="h-5 w-5 text-slate-400"
            />
            <ChevronDown v-else class="h-5 w-5 text-slate-400" />
          </button>

          <!-- Committee detail -->
          <div
            v-if="expandedCommittees.has(committee.committeeId)"
            class="border-t border-slate-100 px-6 pb-6 dark:border-slate-700"
          >
            <!-- Groups table -->
            <h4 class="mb-3 mt-4 text-sm font-medium text-slate-700 dark:text-slate-300">
              Assigned Groups
            </h4>
            <div
              v-if="committee.groups.length === 0"
              class="rounded-lg border border-dashed border-slate-300 py-6 text-center text-sm text-slate-500 dark:border-slate-600 dark:text-slate-400"
            >
              No groups assigned yet.
            </div>
            <div v-else class="overflow-hidden rounded-lg border border-slate-200 dark:border-slate-600">
              <table class="w-full text-sm">
                <thead>
                  <tr class="border-b border-slate-200 bg-slate-50 dark:border-slate-600 dark:bg-slate-700/50">
                    <th class="px-4 py-2.5 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                      Group Name
                    </th>
                    <th class="px-4 py-2.5 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                      Status
                    </th>
                    <th class="px-4 py-2.5 text-right text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                      Actions
                    </th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-100 dark:divide-slate-700">
                  <tr
                    v-for="group in committee.groups"
                    :key="group.groupId"
                    class="transition hover:bg-slate-50 dark:hover:bg-slate-700"
                  >
                    <td class="px-4 py-3 font-medium text-slate-900 dark:text-white">
                      {{ group.groupName }}
                    </td>
                    <td class="px-4 py-3 text-slate-600 dark:text-slate-400">
                      {{ group.status }}
                    </td>
                    <td class="px-4 py-3 text-right">
                      <NuxtLink
                        v-if="group.submissionId"
                        :to="`/professor/submission-review/${group.submissionId}`"
                        class="inline-flex items-center gap-1.5 rounded-lg bg-indigo-50 px-3 py-1.5 text-xs font-medium text-indigo-700 transition hover:bg-indigo-100 dark:bg-indigo-900/30 dark:text-indigo-300 dark:hover:bg-indigo-900/50"
                      >
                        <Eye class="h-3.5 w-3.5" />
                        Teslimi İncele
                      </NuxtLink>
                      <span
                        v-else
                        class="text-xs text-slate-400 dark:text-slate-500 italic"
                      >
                        Teslim yok
                      </span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- Deliverables & Rubric -->
            <h4 class="mb-3 mt-6 text-sm font-medium text-slate-700 dark:text-slate-300">
              Deliverables & Rubric Criteria
            </h4>
            <div
              v-if="deliverables.length === 0"
              class="rounded-lg border border-dashed border-slate-300 py-6 text-center text-sm text-slate-500 dark:border-slate-600 dark:text-slate-400"
            >
              No deliverables configured yet.
            </div>
            <div v-else class="space-y-2">
              <div
                v-for="deliverable in deliverables"
                :key="deliverable.id"
                class="overflow-hidden rounded-lg border border-slate-200 dark:border-slate-600"
              >
                <button
                  type="button"
                  @click="toggleDeliverable(deliverable.id)"
                  class="flex w-full items-center justify-between px-4 py-3 text-left transition hover:bg-slate-50 dark:hover:bg-slate-700"
                >
                  <div>
                    <span class="text-sm font-medium text-slate-900 dark:text-white">
                      {{ deliverable.name }}
                    </span>
                    <span
                      :class="[
                        'ml-2 rounded-full px-2 py-0.5 text-xs font-medium',
                        deliverable.type === 'Proposal'
                          ? 'bg-cyan-100 text-cyan-700 dark:bg-cyan-900/40 dark:text-cyan-300'
                          : deliverable.type === 'SoW'
                            ? 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300'
                            : 'bg-violet-100 text-violet-700 dark:bg-violet-900/40 dark:text-violet-300',
                      ]"
                    >
                      {{ deliverable.type }}
                    </span>
                  </div>
                  <ChevronUp
                    v-if="expandedDeliverables.has(deliverable.id)"
                    class="h-4 w-4 text-slate-400"
                  />
                  <ChevronDown v-else class="h-4 w-4 text-slate-400" />
                </button>

                <div
                  v-if="expandedDeliverables.has(deliverable.id)"
                  class="border-t border-slate-100 px-4 pb-4 pt-3 dark:border-slate-700"
                >
                  <div class="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <span class="text-xs text-slate-500 dark:text-slate-400">Submission Deadline</span>
                      <p class="mt-0.5 font-medium" :class="deadlineUrgency(deliverable.submissionDeadline)">
                        {{ formatDate(deliverable.submissionDeadline) }}
                      </p>
                    </div>
                    <div>
                      <span class="text-xs text-slate-500 dark:text-slate-400">Review Deadline</span>
                      <p class="mt-0.5 font-medium" :class="deadlineUrgency(deliverable.reviewDeadline)">
                        {{ formatDate(deliverable.reviewDeadline) }}
                      </p>
                    </div>
                  </div>

                  <!-- Rubric criteria -->
                  <div class="mt-4">
                    <h5 class="text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                      Rubric Criteria
                    </h5>
                    <div
                      v-if="!rubricsByDeliverable[deliverable.id]"
                      class="mt-2 flex items-center gap-2 text-sm text-slate-500"
                    >
                      <Loader2 class="h-3.5 w-3.5 animate-spin" />
                      Loading…
                    </div>
                    <div
                      v-else-if="rubricsByDeliverable[deliverable.id].length === 0"
                      class="mt-2 text-sm text-slate-500 dark:text-slate-400"
                    >
                      No rubric criteria defined.
                    </div>
                    <div v-else class="mt-2 space-y-1.5">
                      <div
                        v-for="criterion in rubricsByDeliverable[deliverable.id]"
                        class="flex items-center justify-between rounded-md bg-slate-50 px-3 py-2 dark:bg-slate-700/50"
                      >
                        <span class="text-sm text-slate-900 dark:text-white">
                          {{ criterion.criterionName }}
                        </span>
                        <div class="flex items-center gap-3 text-xs">
                          <span
                            :class="[
                              'rounded-full px-2 py-0.5 font-medium',
                              criterion.gradingType === 'Binary'
                                ? 'bg-slate-200 text-slate-700 dark:bg-slate-600 dark:text-slate-300'
                                : 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300',
                            ]"
                          >
                            {{ criterion.gradingType }}
                          </span>
                          <span class="font-semibold text-slate-700 dark:text-slate-300">
                            {{ criterion.weight }}%
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </template>
    </div>
  </main>
</template>
