<script setup lang="ts">
import { Scale, CheckSquare2, MousePointerClick } from "lucide-vue-next";
import type { RubricCriterionResponse } from "~/types/rubric";
import type { RubricMappingItem } from "~/types/submission";

const props = defineProps<{
  criteria: RubricCriterionResponse[];
  mappings: RubricMappingItem[];
  selectedCriterionName: string | null;
}>();

const emit = defineEmits<{
  (e: "select", criterionName: string, sectionRef: string | null): void;
  (e: "deselect"): void;
}>();

function getMappingForCriterion(criterionName: string): RubricMappingItem | undefined {
  return props.mappings.find(
    (m) => m.sectionReference?.toLowerCase().includes(criterionName.toLowerCase()) ||
           criterionName.toLowerCase().includes(m.sectionReference?.toLowerCase() ?? "")
  );
}

function handleCriterionClick(criterion: RubricCriterionResponse) {
  if (props.selectedCriterionName === criterion.criterionName) {
    emit("deselect");
    return;
  }
  const mapping = getMappingForCriterion(criterion.criterionName);
  const sectionRef = mapping?.sectionReference ?? criterion.criterionName;
  emit("select", criterion.criterionName, sectionRef);
}

function isSelected(name: string): boolean {
  return props.selectedCriterionName === name;
}

function hasMappedSection(name: string): boolean {
  return !!getMappingForCriterion(name);
}

const totalWeight = computed(() =>
  props.criteria.reduce((sum, c) => sum + (c.weight ?? 0), 0)
);
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- Header -->
    <div class="flex items-center gap-2 px-4 py-3 border-b border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50 flex-shrink-0">
      <Scale class="w-4 h-4 text-indigo-500" />
      <h2 class="font-semibold text-sm text-slate-700 dark:text-slate-200">
        Değerlendirme Rubriği
      </h2>
      <span class="ml-auto text-xs text-slate-500 dark:text-slate-400">
        Toplam Ağırlık: {{ totalWeight }}%
      </span>
    </div>

    <!-- Hint -->
    <div class="px-4 py-2 border-b border-slate-200 dark:border-slate-700 flex-shrink-0">
      <p class="text-xs text-slate-500 dark:text-slate-400 flex items-center gap-1">
        <MousePointerClick class="w-3 h-3" />
        Bir kritere tıklayarak belgede ilgili bölümü vurgulayabilirsiniz.
      </p>
    </div>

    <!-- Empty state -->
    <div
      v-if="criteria.length === 0"
      class="flex-1 flex items-center justify-center p-6"
    >
      <p class="text-sm text-slate-400 dark:text-slate-500 text-center">
        Bu teslim için rubrik tanımlanmamış.
      </p>
    </div>

    <!-- Criteria list -->
    <ul v-else class="flex-1 overflow-y-auto divide-y divide-slate-100 dark:divide-slate-700/60">
      <li
        v-for="criterion in criteria"
        :key="criterion.criterionName"
        class="group cursor-pointer select-none"
        :class="[
          isSelected(criterion.criterionName)
            ? 'bg-indigo-50 dark:bg-indigo-900/25 border-l-4 border-indigo-500'
            : 'border-l-4 border-transparent hover:bg-slate-50 dark:hover:bg-slate-800/40',
        ]"
        role="button"
        tabindex="0"
        :aria-pressed="isSelected(criterion.criterionName)"
        :aria-label="`Kriter: ${criterion.criterionName}`"
        @click="handleCriterionClick(criterion)"
        @keydown.enter.prevent="handleCriterionClick(criterion)"
        @keydown.space.prevent="handleCriterionClick(criterion)"
      >
        <div class="px-4 py-3 flex items-start gap-3">
          <!-- Icon -->
          <CheckSquare2
            class="w-4 h-4 mt-0.5 flex-shrink-0 transition-colors"
            :class="isSelected(criterion.criterionName)
              ? 'text-indigo-500'
              : 'text-slate-300 dark:text-slate-600 group-hover:text-slate-400'"
          />

          <!-- Content -->
          <div class="flex-1 min-w-0">
            <p
              class="text-sm font-medium leading-tight break-words"
              :class="isSelected(criterion.criterionName)
                ? 'text-indigo-700 dark:text-indigo-300'
                : 'text-slate-700 dark:text-slate-200'"
            >
              {{ criterion.criterionName }}
            </p>
            <div class="flex items-center gap-2 mt-1 flex-wrap">
              <!-- Grading type badge -->
              <span
                class="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium"
                :class="criterion.gradingType === 'Binary'
                  ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300'
                  : 'bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300'"
              >
                {{ criterion.gradingType }}
              </span>
              <!-- Weight -->
              <span class="text-[11px] text-slate-500 dark:text-slate-400">
                Ağırlık: {{ criterion.weight }}%
              </span>
              <!-- Mapping indicator -->
              <span
                v-if="hasMappedSection(criterion.criterionName)"
                class="text-[10px] text-emerald-600 dark:text-emerald-400 font-medium"
              >
                ✓ Bölüm eşlenmiş
              </span>
            </div>
          </div>
        </div>
      </li>
    </ul>

    <!-- Footer: selected criterion label -->
    <div
      v-if="selectedCriterionName"
      class="px-4 py-2 border-t border-slate-200 dark:border-slate-700 bg-indigo-50 dark:bg-indigo-900/20 flex-shrink-0"
    >
      <p class="text-xs text-indigo-600 dark:text-indigo-400 truncate">
        <span class="font-medium">Seçili:</span> {{ selectedCriterionName }}
      </p>
    </div>
  </div>
</template>
