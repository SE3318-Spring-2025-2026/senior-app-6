<script setup lang="ts">
import { MessageSquare, Send, Loader2, AlertCircle } from "lucide-vue-next";
import type { SubmissionComment } from "~/types/submission";

const props = defineProps<{
  comments: SubmissionComment[];
  loading: boolean;
  posting: boolean;
  error: string | null;
  readOnly?: boolean;
}>();

const emit = defineEmits<{
  (e: "submit", commentText: string): void;
}>();

const draft = ref("");

function handleSubmit() {
  const text = draft.value.trim();
  if (!text || props.posting) return;
  emit("submit", text);
  draft.value = "";
}

function formatDate(dateStr: string): string {
  try {
    return new Intl.DateTimeFormat("tr-TR", {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new Date(dateStr));
  } catch {
    return dateStr;
  }
}

</script>

<template>
  <div class="flex flex-col h-full">
    <!-- Header -->
    <div class="flex items-center gap-2 px-4 py-3 border-b border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50 flex-shrink-0">
      <MessageSquare class="w-4 h-4 text-indigo-500" />
      <h2 class="font-semibold text-sm text-slate-700 dark:text-slate-200">
        Comments
      </h2>
      <span class="ml-auto text-xs text-slate-500 dark:text-slate-400">
        {{ comments.length }}
      </span>
    </div>

    <!-- List -->
    <div class="flex-1 overflow-y-auto">
      <div
        v-if="loading"
        class="flex items-center justify-center h-32"
      >
        <Loader2 class="w-5 h-5 text-slate-400 animate-spin" />
      </div>

      <div
        v-else-if="comments.length === 0"
        class="flex items-center justify-center h-32 px-6"
      >
        <p class="text-sm text-slate-400 dark:text-slate-500 text-center">
          {{ readOnly ? "No comments from the committee yet." : "No comments yet. Be the first to leave feedback." }}
        </p>
      </div>

      <ul v-else class="divide-y divide-slate-100 dark:divide-slate-700/60">
        <li
          v-for="comment in comments"
          :key="comment.id"
          class="px-4 py-3"
        >
          <div class="flex items-center justify-between gap-2 mb-1">
            <p class="text-xs font-medium text-slate-700 dark:text-slate-300 truncate">
              {{ comment.reviewerEmail }}
            </p>
            <span class="text-[10px] text-slate-400 dark:text-slate-500 flex-shrink-0">
              {{ formatDate(comment.createdAt) }}
            </span>
          </div>
          <p class="text-sm text-slate-800 dark:text-slate-200 whitespace-pre-wrap break-words">
            {{ comment.commentText }}
          </p>
        </li>
      </ul>
    </div>

    <!-- Composer (hidden in read-only mode) -->
    <div v-if="!readOnly" class="border-t border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 flex-shrink-0">
      <div
        v-if="error"
        class="flex items-start gap-2 px-4 py-2 bg-red-50 dark:bg-red-900/20 border-b border-red-200 dark:border-red-800 text-xs text-red-700 dark:text-red-300"
      >
        <AlertCircle class="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
        {{ error }}
      </div>

      <div class="p-3 space-y-2">
        <textarea
          v-model="draft"
          rows="3"
          placeholder="Write a comment…"
          class="w-full resize-none rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-sm text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          :disabled="posting"
          @keydown.ctrl.enter.prevent="handleSubmit"
          @keydown.meta.enter.prevent="handleSubmit"
        />

        <div class="flex items-center justify-end gap-2">
          <button
            type="button"
            class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            :disabled="!draft.trim() || posting"
            @click="handleSubmit"
          >
            <Loader2 v-if="posting" class="w-3.5 h-3.5 animate-spin" />
            <Send v-else class="w-3.5 h-3.5" />
            {{ posting ? "Sending…" : "Send" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
