<script setup lang="ts">
import { computed } from "vue"
import type { GroupStatus } from "@/types/group"

const props = defineProps<{
  status: GroupStatus | string
}>()

const statusConfig = computed(() => {
  switch (props.status) {
    case "FORMING":
      return {
        label: "Forming",
        classes:
          "bg-yellow-100 text-yellow-800 border-yellow-200 dark:bg-yellow-900/30 dark:text-yellow-300 dark:border-yellow-800",
      }
    case "UNADVISED":
      return {
        label: "Unadvised",
        classes:
          "bg-red-100 text-red-800 border-red-200 dark:bg-red-900/30 dark:text-red-300 dark:border-red-800",
      }
    case "ADVISOR_PENDING":
      return {
        label: "Advisor Pending",
        classes:
          "bg-orange-100 text-orange-800 border-orange-200 dark:bg-orange-900/30 dark:text-orange-300 dark:border-orange-800",
      }
    case "ADVISED":
      return {
        label: "Advised",
        classes:
          "bg-blue-100 text-blue-800 border-blue-200 dark:bg-blue-900/30 dark:text-blue-300 dark:border-blue-800",
      }
    case "TOOLS_BOUND":
      return {
        label: "Tools Bound",
        classes:
          "bg-green-100 text-green-800 border-green-200 dark:bg-green-900/30 dark:text-green-300 dark:border-green-800",
      }
    case "COMMITTEE_ASSIGNED":
      return {
        label: "Committee Assigned",
        classes:
          "bg-purple-100 text-purple-800 border-purple-200 dark:bg-purple-900/30 dark:text-purple-300 dark:border-purple-800",
      }
    case "DISBANDED":
      return {
        label: "Disbanded",
        classes:
          "bg-gray-100 text-gray-800 border-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:border-gray-700",
      }
    default:
      return {
        label: props.status || "Unknown",
        classes:
          "bg-gray-100 text-gray-800 border-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:border-gray-700",
      }
  }
})
</script>

<template>
  <span
    class="inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium whitespace-nowrap"
    :class="statusConfig.classes"
  >
    {{ statusConfig.label }}
  </span>
</template>