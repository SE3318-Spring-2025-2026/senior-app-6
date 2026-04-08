<script setup lang="ts">
import type { GroupMember } from "@/types/group"

const props = withDefaults(
  defineProps<{
    members: GroupMember[]
    removable?: boolean
  }>(),
  {
    removable: false,
  }
)

const emit = defineEmits<{
  (e: "remove", member: GroupMember): void
}>()

function handleRemove(member: GroupMember) {
  emit("remove", member)
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
    <div class="border-b border-gray-200 px-4 py-3 dark:border-gray-800">
      <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
        Members
      </h3>
    </div>

    <div v-if="!members || members.length === 0" class="px-4 py-6 text-sm text-gray-500 dark:text-gray-400">
      No members have been added yet.
    </div>

    <ul v-else class="divide-y divide-gray-200 dark:divide-gray-800">
      <li
        v-for="member in members"
        :key="member.id"
        class="flex items-center justify-between px-4 py-3"
      >
        <div class="flex items-center gap-3">
          <span
            v-if="member.role === 'TEAM_LEADER'"
            class="inline-flex h-8 w-8 items-center justify-center rounded-full bg-indigo-100 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300"
            title="Team Leader"
            aria-label="Team Leader"
          >
            ★
          </span>

          <span
            v-else
            class="inline-flex h-8 w-8 items-center justify-center rounded-full bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300"
            aria-hidden="true"
          >
            👤
          </span>

          <div>
            <p class="text-sm font-medium text-gray-900 dark:text-gray-100">
              {{ member.fullName }}
            </p>
            <p class="text-xs text-gray-500 dark:text-gray-400">
              {{ member.studentId ?? "No student ID" }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-2">
          <span
            v-if="member.role === 'TEAM_LEADER'"
            class="rounded-full bg-indigo-50 px-2 py-1 text-xs font-medium text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300"
          >
            Team Leader
          </span>

          <button
            v-if="removable && member.role !== 'TEAM_LEADER'"
            type="button"
            class="rounded-lg border border-red-200 px-3 py-1.5 text-xs font-medium text-red-600 transition hover:bg-red-50 dark:border-red-800 dark:text-red-300 dark:hover:bg-red-900/20"
            @click="handleRemove(member)"
          >
            Remove
          </button>
        </div>
      </li>
    </ul>
  </div>
</template>