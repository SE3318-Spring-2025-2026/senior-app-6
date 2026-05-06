<script setup lang="ts">
import { Scale, Link2, Trash2, AlertCircle, CheckCircle2 } from "lucide-vue-next";
import type { RubricCriterionResponse } from "~/types/rubric";
import type { LocalMappingEntry } from "~/types/submission";

const props = defineProps<{
  criteria: RubricCriterionResponse[];
  mappings: LocalMappingEntry[];
  hasSelection: boolean;
  selectionText: string;
  loading?: boolean;
  noCriteriaIds?: boolean;
}>();

const emit = defineEmits<{
  (e: "link", criterion: RubricCriterionResponse): void;
  (e: "removeMapping", localId: string): void;
}>();

// Group mappings by criterionName for display
const mappingsByCriterion = computed(() => {
  const map = new Map<string, LocalMappingEntry[]>();
  for (const m of props.mappings) {
    const existing = map.get(m.criterionName) ?? [];
    existing.push(m);
    map.set(m.criterionName, existing);
  }
  return map;
});

const palette = [
  "bg-yellow-100 text-yellow-800 border-yellow-300 dark:bg-yellow-900/30 dark:text-yellow-300 dark:border-yellow-700",
  "bg-blue-100 text-blue-800 border-blue-300 dark:bg-blue-900/30 dark:text-blue-300 dark:border-blue-700",
  "bg-green-100 text-green-800 border-green-300 dark:bg-green-900/30 dark:text-green-300 dark:border-green-700",
  "bg-pink-100 text-pink-800 border-pink-300 dark:bg-pink-900/30 dark:text-pink-300 dark:border-pink-700",
  "bg-purple-100 text-purple-800 border-purple-300 dark:bg-purple-900/30 dark:text-purple-300 dark:border-purple-700",
  "bg-orange-100 text-orange-800 border-orange-300 dark:bg-orange-900/30 dark:text-orange-300 dark:border-orange-700",
];

function criterionColor(idx: number) {
  return palette[idx % palette.length]!;
}

function mappingCount(criterionName: string) {
  return mappingsByCriterion.value.get(criterionName)?.length ?? 0;
}

function truncate(text: string, max = 45): string {
  return text.length > max ? text.slice(0, max) + "…" : text;
}

const totalWeight = computed(() =>
  props.criteria.reduce((s, c) => s + (c.weight ?? 0), 0)
);
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- Header -->
    <div class="flex items-center gap-2 px-4 py-3 border-b border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50 flex-shrink-0">
      <Scale class="w-4 h-4 text-indigo-500" />
      <h2 class="font-semibold text-sm text-slate-700 dark:text-slate-200">
        Rubric Criteria
      </h2>
      <span class="ml-auto text-xs text-slate-500 dark:text-slate-400">
        {{ totalWeight }}% total
      </span>
    </div>

    <!-- Instruction -->
    <div class="px-4 py-2.5 border-b border-slate-200 dark:border-slate-700 flex-shrink-0">
      <p
        class="text-xs flex items-start gap-1.5 transition-colors"
        :class="hasSelection
          ? 'text-indigo-600 dark:text-indigo-400 font-medium'
          : 'text-slate-500 dark:text-slate-400'"
      >
        <Link2 class="w-3.5 h-3.5 mt-0.5 flex-shrink-0" />
        <span v-if="hasSelection">
          Selected text: <em class="not-italic font-semibold">"{{ truncate(selectionText) }}"</em>
          — Pick a criterion below to map it.
        </span>
        <span v-else>
          Select text in the editor first, then click a criterion.
        </span>
      </p>
    </div>

    <!-- No criteria IDs warning -->
    <div
      v-if="noCriteriaIds && criteria.length > 0"
      class="mx-4 mt-3 flex gap-2 rounded-lg bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-700 p-3 flex-shrink-0"
    >
      <AlertCircle class="w-4 h-4 text-amber-500 flex-shrink-0 mt-0.5" />
      <p class="text-xs text-amber-700 dark:text-amber-300">
        Criterion IDs could not be loaded. Mappings will be saved by criterion name.
      </p>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex-1 flex items-center justify-center">
      <p class="text-sm text-slate-400 dark:text-slate-500">Loading rubric…</p>
    </div>

    <!-- Empty -->
    <div
      v-else-if="criteria.length === 0"
      class="flex-1 flex items-center justify-center p-6"
    >
      <p class="text-sm text-slate-400 dark:text-slate-500 text-center">
        No rubric has been defined for this deliverable.
      </p>
    </div>

    <!-- Criteria list -->
    <ul v-else class="flex-1 overflow-y-auto divide-y divide-slate-100 dark:divide-slate-700/60">
      <li
        v-for="(criterion, idx) in criteria"
        :key="criterion.criterionName"
        class="px-4 py-3"
      >
        <!-- Criterion header + link button -->
        <div class="flex items-start gap-2">
          <!-- Color dot -->
          <span
            class="mt-1 inline-block w-2.5 h-2.5 rounded-full flex-shrink-0 border"
            :class="criterionColor(idx)"
          />

          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium text-slate-800 dark:text-slate-100 break-words leading-snug">
              {{ criterion.criterionName }}
            </p>
            <div class="flex items-center gap-2 mt-0.5 flex-wrap">
              <span
                class="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium"
                :class="criterion.gradingType === 'Binary'
                  ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300'
                  : 'bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300'"
              >
                {{ criterion.gradingType }}
              </span>
              <span class="text-[11px] text-slate-500 dark:text-slate-400">{{ criterion.weight }}%</span>
              <span
                v-if="mappingCount(criterion.criterionName) > 0"
                class="text-[10px] font-medium text-emerald-600 dark:text-emerald-400 flex items-center gap-0.5"
              >
                <CheckCircle2 class="w-3 h-3" />
                {{ mappingCount(criterion.criterionName) }} section(s)
              </span>
            </div>
          </div>

          <!-- Link button -->
          <button
            type="button"
            :disabled="!hasSelection"
            class="flex-shrink-0 inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs font-medium transition-all border"
            :class="hasSelection
              ? 'bg-indigo-600 text-white border-indigo-600 hover:bg-indigo-700 shadow-sm'
              : 'bg-slate-100 text-slate-400 border-slate-200 dark:bg-slate-800 dark:text-slate-500 dark:border-slate-700 cursor-not-allowed'"
            :title="hasSelection ? 'Map selected text to this criterion' : 'Select text in the editor first'"
            @click="emit('link', criterion)"
          >
            <Link2 class="w-3 h-3" />
            Map
          </button>
        </div>

        <!-- Mapped sections for this criterion -->
        <div
          v-if="mappingsByCriterion.get(criterion.criterionName)?.length"
          class="mt-2 ml-5 space-y-1"
        >
          <div
            v-for="mapping in mappingsByCriterion.get(criterion.criterionName)"
            :key="mapping.localId"
            class="flex items-center gap-1.5 group"
          >
            <span
              class="flex-1 inline-block text-[11px] px-2 py-0.5 rounded border truncate"
              :class="criterionColor(idx)"
              :title="mapping.sectionKey"
            >
              "{{ truncate(mapping.sectionKey, 40) }}"
            </span>
            <button
              type="button"
              class="opacity-0 group-hover:opacity-100 transition-opacity p-0.5 rounded text-red-400 hover:text-red-600 dark:hover:text-red-400"
              title="Remove mapping"
              @click="emit('removeMapping', mapping.localId)"
            >
              <Trash2 class="w-3 h-3" />
            </button>
          </div>
        </div>
      </li>
    </ul>

    <!-- Footer: total mappings -->
    <div
      v-if="mappings.length > 0"
      class="px-4 py-2.5 border-t border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50 flex-shrink-0"
    >
      <p class="text-xs text-slate-600 dark:text-slate-400">
        <span class="font-semibold text-indigo-600 dark:text-indigo-400">{{ mappings.length }}</span>
        section(s) mapped — Will be saved on submission.
      </p>
    </div>
  </div>
</template>
