<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { Loader2, MessageSquare, Send } from "lucide-vue-next";
import type { SubmissionComment } from "~/types/comment";

interface Props {
  submissionId: string;
  canComment?: boolean;
  title?: string;
  description?: string;
}

const props = withDefaults(defineProps<Props>(), {
  canComment: false,
  title: "Comments",
  description: "Committee feedback linked to this submission.",
});

const { getAuthToken, fetchSubmissionComments, createSubmissionComment } = useApiClient();

const comments = ref<SubmissionComment[]>([]);
const loading = ref(false);
const submitting = ref(false);
const loadError = ref("");
const submitError = ref("");
const submitSuccess = ref("");
const commentText = ref("");

const hasComments = computed(() => comments.value.length > 0);

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

async function loadComments() {
  if (!props.submissionId) return;

  loading.value = true;
  loadError.value = "";

  try {
    const token = getAuthToken();
    if (!token) {
      throw new Error("Authentication required. Please log in again.");
    }

    comments.value = await fetchSubmissionComments(props.submissionId, token);
  } catch (error: unknown) {
    const message = error && typeof error === "object" && "message" in error
      ? String((error as { message?: string }).message)
      : "Unable to load comments right now.";
    loadError.value = message;
    comments.value = [];
  } finally {
    loading.value = false;
  }
}

async function submitComment() {
  if (!props.canComment) return;

  const content = commentText.value.trim();
  if (!content) {
    submitError.value = "Comment text cannot be empty.";
    return;
  }

  submitting.value = true;
  submitError.value = "";
  submitSuccess.value = "";

  try {
    const token = getAuthToken();
    if (!token) {
      throw new Error("Authentication required. Please log in again.");
    }

    await createSubmissionComment(props.submissionId, { commentText: content }, token);
    commentText.value = "";
    submitSuccess.value = "Comment posted successfully.";
    await loadComments();
  } catch (error: unknown) {
    submitError.value = error && typeof error === "object" && "message" in error
      ? String((error as { message?: string }).message)
      : "Failed to submit comment.";
  } finally {
    submitting.value = false;
  }
}

watch(() => props.submissionId, loadComments, { immediate: true });
</script>

<template>
  <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
    <div class="flex items-start justify-between gap-4">
      <div>
        <h3 class="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
          <MessageSquare class="h-5 w-5 text-blue-600 dark:text-blue-400" />
          {{ title }}
        </h3>
        <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
          {{ description }}
        </p>
      </div>
      <span class="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-900/50 dark:text-slate-300">
        {{ comments.length }} comment{{ comments.length === 1 ? "" : "s" }}
      </span>
    </div>

    <div v-if="loadError" class="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-100">
      {{ loadError }}
    </div>

    <div v-if="loading" class="mt-4 space-y-3">
      <div class="h-20 animate-pulse rounded-xl bg-slate-100 dark:bg-slate-700"></div>
      <div class="h-20 animate-pulse rounded-xl bg-slate-100 dark:bg-slate-700"></div>
    </div>

    <div v-else class="mt-4 space-y-3">
      <div v-if="!hasComments" class="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-4 text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-900/40 dark:text-slate-400">
        No comments have been posted yet.
      </div>

      <article v-for="comment in comments" :key="comment.id" class="rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/40">
        <div class="flex items-start justify-between gap-3">
          <div>
            <div class="flex flex-wrap items-center gap-2">
              <p class="font-semibold text-slate-900 dark:text-white">{{ comment.authorName }}</p>
              <span v-if="comment.authorRole" class="rounded-full bg-blue-100 px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-blue-700 dark:bg-blue-950/60 dark:text-blue-200">
                {{ comment.authorRole }}
              </span>
            </div>
            <p class="mt-1 text-xs text-slate-500 dark:text-slate-400">{{ formatDateTime(comment.createdAt) }}</p>
          </div>
          <span v-if="comment.sectionReference" class="rounded-full bg-slate-200 px-2 py-0.5 text-[11px] font-semibold text-slate-700 dark:bg-slate-800 dark:text-slate-300">
            {{ comment.sectionReference }}
          </span>
        </div>
        <p class="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-700 dark:text-slate-300">{{ comment.content }}</p>
      </article>
    </div>

    <form v-if="canComment" class="mt-6 border-t border-slate-200 pt-6 dark:border-slate-700" @submit.prevent="submitComment">
      <label class="block text-sm font-medium text-slate-700 dark:text-slate-300" for="submission-comment-text">
        Leave feedback
      </label>
      <textarea
        id="submission-comment-text"
        v-model="commentText"
        rows="4"
        class="mt-2 w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-700 dark:bg-slate-900 dark:text-white dark:focus:border-blue-400 dark:focus:ring-blue-400/20"
        placeholder="Write a review comment for the team..."
      ></textarea>

      <div v-if="submitError" class="mt-3 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
        {{ submitError }}
      </div>
      <div v-if="submitSuccess" class="mt-3 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200">
        {{ submitSuccess }}
      </div>

      <div class="mt-4 flex items-center justify-end gap-3">
        <button
          type="submit"
          :disabled="submitting"
          class="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
        >
          <Loader2 v-if="submitting" class="h-4 w-4 animate-spin" />
          <Send v-else class="h-4 w-4" />
          {{ submitting ? "Posting..." : "Post comment" }}
        </button>
      </div>
    </form>
  </section>
</template>