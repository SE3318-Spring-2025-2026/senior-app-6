<script setup lang="ts">
	import {
		AlertCircle,
		ArrowLeft,
		CheckCircle2,
		ChevronDown,
		Loader2,
		Users,
		UserCheck,
		Plus,
	} from "lucide-vue-next";

	definePageMeta({
		middleware: "auth",
		roles: ["Coordinator"],
	});

	const {
		getAuthToken,
		fetchCommittees,
		fetchCommittee,
		fetchUnassignedGroups,
		assignGroupsToCommittee,
	} = useApiClient();

	// State
	const committees = ref<Committee[]>([]);
	const unassignedGroups = ref<StudentGroup[]>([]);
	const selectedCommitteeId = ref("");
	const selectedCommittee = ref<Committee | null>(null);
	const selectedGroupIds = ref<Set<string>>(new Set());

	const loading = ref(true);
	const loadError = ref<string | null>(null);
	const submitting = ref(false);
	const successMessage = ref("");
	const errorMessage = ref("");

	// Computed
	const hasSelection = computed(() => selectedGroupIds.value.size > 0);

	const selectableGroups = computed(() =>
		unassignedGroups.value.filter((g: StudentGroup) => g.advisorApproved)
	);

	const allSelected = computed(
		() =>
			selectableGroups.value.length > 0 &&
			selectableGroups.value.every((g: StudentGroup) => selectedGroupIds.value.has(g.id))
	);

	// Methods
	function toggleGroup(groupId: string) {
		const next = new Set(selectedGroupIds.value);
		if (next.has(groupId)) {
			next.delete(groupId);
		} else {
			next.add(groupId);
		}
		selectedGroupIds.value = next;
	}

	function toggleAll() {
		if (allSelected.value) {
			selectedGroupIds.value = new Set();
		} else {
			selectedGroupIds.value = new Set(selectableGroups.value.map((g: StudentGroup) => g.id));
		}
	}

	async function loadData() {
		loading.value = true;
		loadError.value = null;
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required");

			const [committeeList, groupList] = await Promise.all([
				fetchCommittees(token),
				fetchUnassignedGroups(token),
			]);
			committees.value = committeeList;
			unassignedGroups.value = groupList;
		} catch (err: unknown) {
			const msg =
				err && typeof err === "object" && "message" in err
					? String(err.message)
					: "Failed to load data.";
			loadError.value = msg;
		} finally {
			loading.value = false;
		}
	}

	async function loadCommitteeDetail(id: string) {
		if (!id) {
			selectedCommittee.value = null;
			return;
		}
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required");
			selectedCommittee.value = await fetchCommittee(id, token);
		} catch {
			selectedCommittee.value = null;
		}
	}

	watch(selectedCommitteeId, (newId) => {
		selectedGroupIds.value = new Set();
		successMessage.value = "";
		errorMessage.value = "";
		loadCommitteeDetail(newId);
	});

	async function handleAssign() {
		if (!selectedCommitteeId.value || selectedGroupIds.value.size === 0) return;

		successMessage.value = "";
		errorMessage.value = "";
		submitting.value = true;

		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required");

			await assignGroupsToCommittee(
				selectedCommitteeId.value,
				Array.from(selectedGroupIds.value),
				token
			);

			successMessage.value = `${selectedGroupIds.value.size} group(s) assigned successfully.`;
			selectedGroupIds.value = new Set();

			// Refresh data
			const [committeeList, groupList] = await Promise.all([
				fetchCommittees(token),
				fetchUnassignedGroups(token),
			]);
			committees.value = committeeList;
			unassignedGroups.value = groupList;
			await loadCommitteeDetail(selectedCommitteeId.value);
		} catch (err: unknown) {
			const msg =
				err && typeof err === "object" && "message" in err
					? String(err.message)
					: "Failed to assign groups.";
			errorMessage.value = msg;
		} finally {
			submitting.value = false;
		}
	}

	onMounted(loadData);
</script>

<template>
  <main
    class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8"
  >
    <div class="mx-auto w-full max-w-4xl space-y-6">
      <!-- Header -->
      <header
        class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg"
      >
        <div class="flex items-center justify-between">
          <div>
            <h1
              class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl"
            >
              Committee Group Assignment
            </h1>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Select a committee and assign student groups that have completed advisor association.
            </p>
          </div>
          <NuxtLink
            to="/coordinator/dashboard"
            class="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
          >
            <ArrowLeft class="mr-2 inline h-4 w-4" />
            Back
          </NuxtLink>
        </div>
      </header>

      <!-- Loading state -->
      <div
        v-if="loading"
        class="flex items-center justify-center rounded-2xl border border-slate-200 bg-white p-12 dark:border-slate-700 dark:bg-slate-800"
      >
        <Loader2 class="h-6 w-6 animate-spin text-blue-600 dark:text-blue-400" />
        <span class="ml-3 text-sm text-slate-600 dark:text-slate-400">Loading committees and groups…</span>
      </div>

      <!-- Error state -->
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

      <!-- Main content -->
      <template v-else>
        <!-- Committee selector -->
        <section
          class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800"
        >
          <label class="block space-y-2">
            <span class="text-sm font-medium text-slate-700 dark:text-slate-300">
              Select Committee
            </span>
            <div class="relative">
              <select
                v-model="selectedCommitteeId"
                class="w-full appearance-none rounded-lg border border-slate-300 bg-white py-2.5 pl-3 pr-10 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400 dark:focus:ring-blue-400/20"
              >
                <option value="" disabled>Choose a committee…</option>
                <option v-for="c in committees" :key="c.id" :value="c.id">
                  {{ c.name }}
                </option>
              </select>
              <ChevronDown
                class="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                aria-hidden="true"
              />
            </div>
          </label>

          <div
            v-if="committees.length === 0"
            class="mt-4 flex items-start gap-3 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200"
          >
            <AlertCircle class="mt-0.5 h-4 w-4 shrink-0" />
            <div>
              <p class="font-medium">No committee found for assignment</p>
              <p class="mt-1">Create at least one committee first, then come back to assign groups.</p>
              <NuxtLink
                to="/coordinator/committees"
                class="mt-2 inline-flex items-center gap-1 text-sm font-medium text-blue-700 underline-offset-2 hover:underline dark:text-blue-300"
              >
                Go to Committee Management
              </NuxtLink>
            </div>
          </div>

          <!-- Currently assigned groups indicator -->
          <div v-if="selectedCommittee" class="mt-4">
            <h3 class="text-sm font-medium text-slate-700 dark:text-slate-300">
              Currently Assigned Groups
            </h3>
            <div
              v-if="selectedCommittee.groups && selectedCommittee.groups.length > 0"
              class="mt-2 flex flex-wrap gap-2"
            >
              <span
                v-for="group in selectedCommittee.groups"
                :key="group.id"
                class="inline-flex items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-800 dark:border-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300"
              >
                <UserCheck class="h-3 w-3" aria-hidden="true" />
                {{ group.name }}
              </span>
            </div>
            <p
              v-else
              class="mt-2 text-sm text-slate-500 dark:text-slate-400"
            >
              No groups assigned yet.
            </p>
          </div>
        </section>

        <!-- Group selection -->
        <section
          v-if="selectedCommitteeId"
          class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800"
        >
          <div class="flex items-center justify-between">
            <h2 class="text-lg font-semibold text-slate-900 dark:text-white">
              Available Groups
            </h2>
            <span class="text-xs text-slate-500 dark:text-slate-400">
              {{ selectedGroupIds.size }} of {{ selectableGroups.length }} selected
            </span>
          </div>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Only groups with completed advisor association are shown.
          </p>

          <!-- Empty state -->
          <div
            v-if="selectableGroups.length === 0"
            class="mt-6 flex flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 py-10 dark:border-slate-600"
          >
            <Users class="h-10 w-10 text-slate-300 dark:text-slate-600" />
            <p class="mt-3 text-sm text-slate-500 dark:text-slate-400">
              No eligible unassigned groups available.
            </p>
          </div>

          <!-- Group checklist -->
          <div v-else class="mt-4 space-y-2">
            <!-- Select all -->
            <label
              class="flex cursor-pointer items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 px-4 py-2.5 transition hover:bg-slate-100 dark:border-slate-600 dark:bg-slate-700/50 dark:hover:bg-slate-700"
            >
              <input
                type="checkbox"
                :checked="allSelected"
                @change="toggleAll"
                class="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500 dark:border-slate-500 dark:bg-slate-700"
              />
              <span class="text-sm font-medium text-slate-700 dark:text-slate-300">
                Select All
              </span>
            </label>

            <!-- Individual groups -->
            <label
              v-for="group in selectableGroups"
              :key="group.id"
              :class="[
                'flex cursor-pointer items-center gap-3 rounded-lg border px-4 py-3 transition',
                selectedGroupIds.has(group.id)
                  ? 'border-blue-300 bg-blue-50 dark:border-blue-600 dark:bg-blue-950/30'
                  : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-800 dark:hover:border-slate-500 dark:hover:bg-slate-750',
              ]"
            >
              <input
                type="checkbox"
                :checked="selectedGroupIds.has(group.id)"
                @change="toggleGroup(group.id)"
                class="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500 dark:border-slate-500 dark:bg-slate-700"
              />
              <div class="flex-1">
                <span class="text-sm font-medium text-slate-900 dark:text-white">
                  {{ group.name }}
                </span>
              </div>
              <span
                class="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-xs text-emerald-700 dark:border-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-400"
              >
                <UserCheck class="h-3 w-3" aria-hidden="true" />
                Advisor Approved
              </span>
            </label>
          </div>

          <!-- Messages -->
          <div
            v-if="successMessage"
            class="mt-4 flex items-center gap-2 rounded-lg border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-300"
          >
            <CheckCircle2 class="h-4 w-4 shrink-0" />
            {{ successMessage }}
          </div>

          <div
            v-if="errorMessage"
            class="mt-4 flex items-center gap-2 rounded-lg border border-red-300 bg-red-50 p-3 text-sm text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300"
          >
            <AlertCircle class="h-4 w-4 shrink-0" />
            {{ errorMessage }}
          </div>

          <!-- Assign button -->
          <button
            :disabled="!hasSelection || submitting"
            @click="handleAssign"
            class="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
          >
            <Loader2 v-if="submitting" class="h-4 w-4 animate-spin" />
            <Plus v-else class="h-4 w-4" />
            {{
              submitting
                ? "Assigning…"
                : `Assign ${selectedGroupIds.size} Group${selectedGroupIds.size !== 1 ? "s" : ""} to Committee`
            }}
          </button>
        </section>
      </template>
    </div>
  </main>
</template>
