<script setup lang="ts">
import { computed } from "vue";

export type AiResultBadgeValue = "PENDING" | "PASS" | "WARN" | "FAIL" | "SKIPPED";

const props = defineProps<{
	result: AiResultBadgeValue;
}>();

const badge = computed(() => {
	switch (props.result) {
		case "PASS":
			return {
				label: "PASS",
				classes:
					"bg-green-100 text-green-800 border-green-200 dark:bg-green-900/30 dark:text-green-300 dark:border-green-800",
			};
		case "WARN":
			return {
				label: "WARN",
				classes:
					"bg-yellow-100 text-yellow-800 border-yellow-200 dark:bg-yellow-900/30 dark:text-yellow-300 dark:border-yellow-800",
			};
		case "FAIL":
			return {
				label: "FAIL",
				classes:
					"bg-red-100 text-red-800 border-red-200 dark:bg-red-900/30 dark:text-red-300 dark:border-red-800",
			};
		case "PENDING":
			return {
				label: "PENDING",
				classes:
					"bg-gray-100 text-gray-800 border-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:border-gray-700",
			};
		case "SKIPPED":
			return {
				label: "SKIPPED",
				classes:
					"bg-slate-100 text-slate-600 border-slate-200 dark:bg-slate-800/80 dark:text-slate-400 dark:border-slate-600",
			};
		default:
			return {
				label: String(props.result),
				classes:
					"bg-gray-100 text-gray-800 border-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:border-gray-700",
			};
	}
});
</script>

<template>
	<span
		class="inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium whitespace-nowrap"
		:class="badge.classes"
	>
		{{ badge.label }}
	</span>
</template>
