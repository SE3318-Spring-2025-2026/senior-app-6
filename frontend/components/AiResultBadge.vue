<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  result: "PASS" | "WARN" | "FAIL" | "PENDING" | "SKIPPED" | null | undefined;
}>();

const config = computed(() => {
  switch (props.result) {
    case "PASS":
      return {
        label: "PASS",
        classes:
          "bg-emerald-100 text-emerald-800 border-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-300 dark:border-emerald-800",
      };
    case "WARN":
      return {
        label: "WARN",
        classes:
          "bg-amber-100 text-amber-800 border-amber-200 dark:bg-amber-900/30 dark:text-amber-300 dark:border-amber-800",
      };
    case "FAIL":
      return {
        label: "FAIL",
        classes:
          "bg-red-100 text-red-800 border-red-200 dark:bg-red-900/30 dark:text-red-300 dark:border-red-800",
      };
    case "PENDING":
      return {
        label: "Pending",
        classes:
          "bg-blue-100 text-blue-700 border-blue-200 dark:bg-blue-900/30 dark:text-blue-300 dark:border-blue-800",
      };
    case "SKIPPED":
      return {
        label: "Skipped",
        classes:
          "bg-slate-100 text-slate-500 border-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:border-slate-700",
      };
    default:
      return {
        label: "—",
        classes:
          "bg-slate-100 text-slate-500 border-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:border-slate-700",
      };
  }
});
</script>

<template>
  <span
    class="inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium whitespace-nowrap"
    :class="config.classes"
  >
    {{ config.label }}
  </span>
</template>
