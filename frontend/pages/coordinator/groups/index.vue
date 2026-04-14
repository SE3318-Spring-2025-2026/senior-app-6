<script setup lang="ts">
	import { AlertCircle, ArrowLeft, ArrowUpDown, Loader as LoaderIcon, Search, Users } from "lucide-vue-next";
	import type { CoordinatorGroupSummary } from "~/composables/useApiClient";

	definePageMeta({
		middleware: "auth",
		roles: ["Coordinator"],
	});

	const { fetchCoordinatorGroups, getAuthToken } = useApiClient();

	const groups = ref<CoordinatorGroupSummary[]>([]);
	const isLoading = ref(true);
	const fetchError = ref<string | null>(null);
	const selectedTerm = ref("ALL");
	type SortKey = "groupName" | "termId" | "status" | "memberCount" | "jiraBound" | "githubBound";
	const sortKey = ref<SortKey>("status");
	const sortDirection = ref<"asc" | "desc">("desc");

	const statusPriority: Record<string, number> = {
		TOOLS_BOUND: 4,
		ADVISOR_ASSIGNED: 4,
		TOOLS_PENDING: 3,
		FORMING: 2,
		DISBANDED: 1
	};

	const availableTerms = computed(() => {
		const terms = Array.from(new Set(groups.value.map((group) => group.termId)));
		return terms.sort((a, b) => b.localeCompare(a));
	});

	const filteredGroups = computed(() => {
		if (selectedTerm.value === "ALL") {
			return groups.value;
		}
		return groups.value.filter((group) => group.termId === selectedTerm.value);
	});

	const sortedGroups = computed(() => {
		const list = [...filteredGroups.value];
		return list.sort((a, b) => {
			let left: string | number;
			let right: string | number;

			switch (sortKey.value) {
				case "memberCount":
					left = a.memberCount;
					right = b.memberCount;
					break;
				case "jiraBound":
					left = a.jiraBound ? 1 : 0;
					right = b.jiraBound ? 1 : 0;
					break;
				case "githubBound":
					left = a.githubBound ? 1 : 0;
					right = b.githubBound ? 1 : 0;
					break;
				case "termId":
					left = a.termId;
					right = b.termId;
					break;
				case "status":
					left = statusPriority[a.status] ?? 0;
					right = statusPriority[b.status] ?? 0;
					break;
				default:
					left = a.groupName;
					right = b.groupName;
					break;
			}

			if (typeof left === "number" && typeof right === "number") {
				return sortDirection.value === "asc" ? left - right : right - left;
			}

			const compared = String(left).localeCompare(String(right), undefined, {
				sensitivity: "base"
			});
			return sortDirection.value === "asc" ? compared : -compared;
		});
	});

	function toggleSort(key: SortKey) {
		if (sortKey.value === key) {
			sortDirection.value = sortDirection.value === "asc" ? "desc" : "asc";
			return;
		}
		sortKey.value = key;
		sortDirection.value = "desc";
	}

	function sortIndicator(key: SortKey) {
		if (sortKey.value !== key) return "";
		return sortDirection.value === "asc" ? "↑" : "↓";
	}

	async function loadGroups() {
		isLoading.value = true;
		fetchError.value = null;
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required. Please log in.");
			groups.value = await fetchCoordinatorGroups(token, "ALL");
		} catch (error) {
			const message =
				error && typeof error === "object" && "message" in error
					? String(error.message)
					: "Failed to load groups";
			fetchError.value = message;
		} finally {
			isLoading.value = false;
		}
	}

	onMounted(loadGroups);
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-7xl space-y-6">
      <NuxtLink
        to="/coordinator/dashboard"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to dashboard
      </NuxtLink>

      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div>
            <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
              Groups Management
            </h1>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Review all groups for the active term and open detail pages for coordinator overrides.
            </p>
          </div>

          <div class="w-full md:w-56">
            <label class="block space-y-1.5">
              <span class="text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Term Filter
              </span>
              <select
                v-model="selectedTerm"
                class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-slate-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-slate-400"
              >
                <option value="ALL">All terms</option>
                <option v-for="term in availableTerms" :key="term" :value="term">
                  {{ term }}
                </option>
              </select>
            </label>
          </div>
        </div>
      </header>

      <div
        v-if="fetchError"
        class="flex items-start gap-3 rounded-lg border border-red-300 bg-red-50 p-4 dark:border-red-800 dark:bg-red-950/50"
      >
        <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
        <div>
          <p class="font-medium text-red-900 dark:text-red-300">Error loading groups</p>
          <p class="text-sm text-red-700 dark:text-red-400">{{ fetchError }}</p>
        </div>
      </div>

      <div
        v-if="isLoading && !fetchError"
        class="flex items-center justify-center rounded-lg border border-slate-200 bg-white p-12 dark:border-slate-700 dark:bg-slate-800"
      >
        <div class="text-center">
          <LoaderIcon class="mx-auto h-8 w-8 animate-spin text-blue-600 dark:text-blue-400" />
          <p class="mt-3 text-sm text-slate-600 dark:text-slate-400">Loading groups...</p>
        </div>
      </div>

      <section
        v-if="!isLoading && !fetchError"
        class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg"
      >
        <div class="mb-4 flex items-center justify-between gap-3">
            <h2 class="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
            <Users class="h-5 w-5 text-slate-700 dark:text-slate-300" />
            Groups ({{ sortedGroups.length }})
          </h2>
        </div>

        <div
          v-if="sortedGroups.length === 0"
          class="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-8 text-center dark:border-slate-600 dark:bg-slate-900/50"
        >
          <Search class="mx-auto h-8 w-8 text-slate-500 dark:text-slate-400" />
          <p class="mt-3 text-sm font-medium text-slate-700 dark:text-slate-300">
            No groups found for this filter.
          </p>
        </div>

        <div v-else class="overflow-x-auto">
          <table class="min-w-full divide-y divide-slate-200 text-sm dark:divide-slate-700">
            <thead>
              <tr class="text-left text-xs uppercase tracking-wide text-slate-500 dark:text-slate-400">
                <th class="px-3 py-3 font-semibold">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 transition hover:text-slate-700 dark:hover:text-slate-200"
                    @click="toggleSort('groupName')"
                  >
                    Group Name
                    <ArrowUpDown class="h-3.5 w-3.5" />
                    <span class="text-[11px]">{{ sortIndicator("groupName") }}</span>
                  </button>
                </th>
                <th class="px-3 py-3 font-semibold">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 transition hover:text-slate-700 dark:hover:text-slate-200"
                    @click="toggleSort('termId')"
                  >
                    Term
                    <ArrowUpDown class="h-3.5 w-3.5" />
                    <span class="text-[11px]">{{ sortIndicator("termId") }}</span>
                  </button>
                </th>
                <th class="px-3 py-3 font-semibold">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 transition hover:text-slate-700 dark:hover:text-slate-200"
                    @click="toggleSort('status')"
                  >
                    Status
                    <ArrowUpDown class="h-3.5 w-3.5" />
                    <span class="text-[11px]">{{ sortIndicator("status") }}</span>
                  </button>
                </th>
                <th class="px-3 py-3 font-semibold">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 transition hover:text-slate-700 dark:hover:text-slate-200"
                    @click="toggleSort('memberCount')"
                  >
                    Members
                    <ArrowUpDown class="h-3.5 w-3.5" />
                    <span class="text-[11px]">{{ sortIndicator("memberCount") }}</span>
                  </button>
                </th>
                <th class="px-3 py-3 font-semibold">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 transition hover:text-slate-700 dark:hover:text-slate-200"
                    @click="toggleSort('jiraBound')"
                  >
                    JIRA
                    <ArrowUpDown class="h-3.5 w-3.5" />
                    <span class="text-[11px]">{{ sortIndicator("jiraBound") }}</span>
                  </button>
                </th>
                <th class="px-3 py-3 font-semibold">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 transition hover:text-slate-700 dark:hover:text-slate-200"
                    @click="toggleSort('githubBound')"
                  >
                    GitHub
                    <ArrowUpDown class="h-3.5 w-3.5" />
                    <span class="text-[11px]">{{ sortIndicator("githubBound") }}</span>
                  </button>
                </th>
                <th class="px-3 py-3 font-semibold text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-200 dark:divide-slate-700">
              <tr
                v-for="group in sortedGroups"
                :key="group.id"
                class="transition hover:bg-slate-50 dark:hover:bg-slate-900/50"
              >
                <td class="px-3 py-3 font-medium text-slate-900 dark:text-white">
                  {{ group.groupName }}
                </td>
                <td class="px-3 py-3 text-slate-700 dark:text-slate-300">
                  {{ group.termId }}
                </td>
                <td class="px-3 py-3">
                  <UiGroupStatusBadge :status="group.status" />
                </td>
                <td class="px-3 py-3 text-slate-700 dark:text-slate-300">
                  {{ group.memberCount }}
                </td>
                <td class="px-3 py-3">
                  <span
                    class="rounded-full px-2 py-1 text-xs font-medium"
                    :class="group.jiraBound
                      ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300'
                      : 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300'"
                  >
                    {{ group.jiraBound ? "Bound" : "Pending" }}
                  </span>
                </td>
                <td class="px-3 py-3">
                  <span
                    class="rounded-full px-2 py-1 text-xs font-medium"
                    :class="group.githubBound
                      ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300'
                      : 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300'"
                  >
                    {{ group.githubBound ? "Bound" : "Pending" }}
                  </span>
                </td>
                <td class="px-3 py-3 text-right">
                  <NuxtLink
                    :to="`/coordinator/groups/${group.id}`"
                    class="inline-flex items-center rounded-md bg-blue-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
                  >
                    View Details
                  </NuxtLink>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </main>
</template>
