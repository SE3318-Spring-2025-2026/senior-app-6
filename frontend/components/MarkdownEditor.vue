<script setup lang="ts">
import type { LocalMappingEntry } from "~/types/submission";

const props = defineProps<{
  modelValue: string;
  mappings: LocalMappingEntry[];
  disabled?: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
  (e: "selection", text: string, start: number, end: number): void;
  (e: "clearSelection"): void;
}>();

const textareaRef = ref<HTMLTextAreaElement | null>(null);
const mirrorRef = ref<HTMLDivElement | null>(null);

// Track current selection
const selectionText = ref("");
const selectionStart = ref(0);
const selectionEnd = ref(0);
const hasSelection = ref(false);

function onInput(e: Event) {
  emit("update:modelValue", (e.target as HTMLTextAreaElement).value);
}

function onSelectionChange() {
  const el = textareaRef.value;
  if (!el) return;
  const start = el.selectionStart ?? 0;
  const end = el.selectionEnd ?? 0;
  const text = el.value.slice(start, end);

  if (text.trim().length > 0) {
    selectionStart.value = start;
    selectionEnd.value = end;
    selectionText.value = text;
    hasSelection.value = true;
    emit("selection", text, start, end);
  } else {
    hasSelection.value = false;
    selectionText.value = "";
    emit("clearSelection");
  }
}

// Sync scroll between textarea and mirror
function onScroll() {
  if (mirrorRef.value && textareaRef.value) {
    mirrorRef.value.scrollTop = textareaRef.value.scrollTop;
  }
}

// Build mirror HTML: plain text with <mark> for each mapped sectionReference
const mirrorHtml = computed(() => {
  let text = props.modelValue;
  if (!text) return "";

  // Sort mappings by position of their sectionReference in text (first occurrence)
  const ranges: Array<{ start: number; end: number; criterionName: string; color: string }> = [];
  const palette = [
    "bg-yellow-200/60 dark:bg-yellow-500/30",
    "bg-blue-200/60 dark:bg-blue-500/30",
    "bg-green-200/60 dark:bg-green-500/30",
    "bg-pink-200/60 dark:bg-pink-500/30",
    "bg-purple-200/60 dark:bg-purple-500/30",
    "bg-orange-200/60 dark:bg-orange-500/30",
  ];

  props.mappings.forEach((mapping, idx) => {
    const ref = mapping.sectionReference;
    const pos = text.indexOf(ref);
    if (pos !== -1) {
      ranges.push({
        start: pos,
        end: pos + ref.length,
        criterionName: mapping.criterionName,
        color: palette[idx % palette.length]!,
      });
    }
  });

  // Sort by start position, resolve overlaps (skip overlapping)
  ranges.sort((a, b) => a.start - b.start);

  let result = "";
  let cursor = 0;

  for (const range of ranges) {
    if (range.start < cursor) continue; // skip overlapping
    result += escapeHtml(text.slice(cursor, range.start));
    result += `<mark class="rubric-mapped-section" title="${escapeHtml(range.criterionName)}">${escapeHtml(text.slice(range.start, range.end))}</mark>`;
    cursor = range.end;
  }
  result += escapeHtml(text.slice(cursor));

  // Preserve newlines
  return result.replace(/\n/g, "<br>");
});

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

// Expose selection info for parent
defineExpose({ selectionText, selectionStart, selectionEnd, hasSelection });
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- Selection hint bar -->
    <div
      class="flex items-center gap-2 px-3 py-2 text-xs border-b border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50 flex-shrink-0 transition-all"
      :class="hasSelection ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-400 dark:text-slate-500'"
    >
      <span v-if="hasSelection" class="flex items-center gap-1.5 font-medium">
        <span class="inline-block w-2 h-2 rounded-full bg-indigo-500 animate-pulse" />
        Text selected ({{ selectionText.length }} characters) — Pick a criterion from the right panel to map it
      </span>
      <span v-else>
        Highlight text to select a section, then click a criterion on the right
      </span>
    </div>

    <!-- Editor area: textarea + highlight mirror -->
    <div class="relative flex-1 min-h-0">
      <!-- Mirror: renders mapped sections as colored highlights -->
      <div
        ref="mirrorRef"
        class="absolute inset-0 overflow-hidden pointer-events-none"
        aria-hidden="true"
      >
        <div
          class="mirror-content w-full h-full overflow-hidden whitespace-pre-wrap break-words"
          v-html="mirrorHtml"
        />
      </div>

      <!-- Actual textarea -->
      <textarea
        ref="textareaRef"
        :value="modelValue"
        :disabled="disabled"
        placeholder="Write your markdown content here…

# Project Title

## Introduction
..."
        class="relative w-full h-full resize-none bg-transparent outline-none font-mono text-sm text-slate-900 dark:text-slate-100 placeholder-slate-400 dark:placeholder-slate-500 p-4 leading-relaxed caret-indigo-500"
        :class="disabled ? 'cursor-not-allowed opacity-60' : ''"
        spellcheck="false"
        autocomplete="off"
        autocorrect="off"
        @input="onInput"
        @mouseup="onSelectionChange"
        @keyup="onSelectionChange"
        @scroll="onScroll"
        @blur="onSelectionChange"
      />
    </div>
  </div>
</template>

<style scoped>
.mirror-content {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.875rem;
  line-height: 1.625;
  padding: 1rem;
  color: transparent;
  position: absolute;
  inset: 0;
  overflow: hidden;
  word-break: break-word;
  white-space: pre-wrap;
}
</style>

<style>
.rubric-mapped-section {
  background-color: rgb(251 191 36 / 0.45);
  border-radius: 2px;
  color: transparent;
  position: relative;
}

.dark .rubric-mapped-section {
  background-color: rgb(251 191 36 / 0.3);
}
</style>
