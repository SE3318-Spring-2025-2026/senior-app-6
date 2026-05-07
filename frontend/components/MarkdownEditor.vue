<script setup lang="ts">
import { marked } from "marked";
import {
  Bold,
  Italic,
  Heading,
  Link2,
  List,
  ListOrdered,
  Code,
  Quote,
  Image as ImageIcon,
  Eye,
  Pencil,
} from "lucide-vue-next";
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
const fileInputRef = ref<HTMLInputElement | null>(null);

const selectionText = ref("");
const selectionStart = ref(0);
const selectionEnd = ref(0);
const selectionDisplayLength = ref(0);
const hasSelection = ref(false);

const mode = ref<"edit" | "preview">("edit");

const uploadError = ref<string | null>(null);
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

// ── Image map: token id → real data: URL ───────────────────────────────────────
// Display value (textarea content) uses short tokens; modelValue (out) is fully expanded.
const imageMap = ref<Record<string, string>>({});
const displayValue = ref("");
let imageCounter = 0;

const DATA_URL_RE = /!\[([^\]]*)\]\((data:image\/[A-Za-z0-9.+-]+;base64,[A-Za-z0-9+/=]+)\)/g;
const TOKEN_RE = /!\[([^\]]*)\]\(image:(img-\d+)\)/g;

function compactify(markdown: string): { display: string; map: Record<string, string> } {
  const map: Record<string, string> = {};
  // Carry over existing token bindings if the same token text appears
  let counter = imageCounter;
  const display = markdown.replace(DATA_URL_RE, (_, alt, url) => {
    counter += 1;
    const id = `img-${counter}`;
    map[id] = url;
    return `![${alt}](image:${id})`;
  });
  imageCounter = counter;
  return { display, map };
}

function expand(display: string, map: Record<string, string>): string {
  return display.replace(TOKEN_RE, (orig, alt, id) => {
    const url = map[id];
    return url ? `![${alt}](${url})` : orig;
  });
}

// Map an offset in displayValue to an offset in expanded modelValue
function displayToExpandedOffset(off: number): number {
  let displayPos = 0;
  let expandedPos = 0;
  const re = new RegExp(TOKEN_RE.source, "g");
  let m: RegExpExecArray | null;
  while ((m = re.exec(displayValue.value)) !== null) {
    const tokenStart = m.index;
    const tokenEnd = tokenStart + m[0].length;

    // Before or at the START of this token → still in plain text
    if (off <= tokenStart) {
      return expandedPos + (off - displayPos);
    }

    // Strictly inside the token (between '!' and ')') → snap to expanded START
    if (off < tokenEnd) {
      return expandedPos + (tokenStart - displayPos);
    }

    // off >= tokenEnd: consume this token's plain-text-before + expanded token
    expandedPos += (tokenStart - displayPos);
    const url = imageMap.value[m[2]];
    const expandedTok = url ? `![${m[1]}](${url})` : m[0];
    expandedPos += expandedTok.length;
    displayPos = tokenEnd;
  }
  return expandedPos + (off - displayPos);
}

// Map an offset in expanded modelValue to an offset in displayValue
function expandedToDisplayOffset(off: number): number {
  let displayPos = 0;
  let expandedPos = 0;
  const re = new RegExp(TOKEN_RE.source, "g");
  let m: RegExpExecArray | null;
  while ((m = re.exec(displayValue.value)) !== null) {
    const tokenStart = m.index;
    const tokenEnd = tokenStart + m[0].length;
    const url = imageMap.value[m[2]];
    const expandedTok = url ? `![${m[1]}](${url})` : m[0];
    const beforeText = tokenStart - displayPos;

    // Before or at the start of this token's expanded form
    if (off <= expandedPos + beforeText) {
      return displayPos + (off - expandedPos);
    }
    expandedPos += beforeText;

    // Strictly inside the expanded token → snap to display end of token
    if (off < expandedPos + expandedTok.length) {
      return tokenEnd;
    }

    // off >= end of expanded token: consume it
    expandedPos += expandedTok.length;
    displayPos = tokenEnd;
  }
  return displayPos + (off - expandedPos);
}

// Sync from external modelValue (e.g., load existing submission) → displayValue + map
watch(
  () => props.modelValue,
  (val) => {
    const expanded = expand(displayValue.value, imageMap.value);
    if (val === expanded) return; // already in sync (we emitted it)
    const result = compactify(val ?? "");
    displayValue.value = result.display;
    imageMap.value = result.map;
  },
  { immediate: true }
);

function emitExpanded() {
  emit("update:modelValue", expand(displayValue.value, imageMap.value));
}

function onInput(e: Event) {
  displayValue.value = (e.target as HTMLTextAreaElement).value;
  emitExpanded();
}

function onSelectionChange() {
  const el = textareaRef.value;
  if (!el) return;
  const dStart = el.selectionStart ?? 0;
  const dEnd = el.selectionEnd ?? 0;
  const text = displayValue.value.slice(dStart, dEnd);

  if (text.trim().length > 0) {
    const xStart = displayToExpandedOffset(dStart);
    const xEnd = displayToExpandedOffset(dEnd);
    const expanded = expand(displayValue.value, imageMap.value);
    selectionStart.value = xStart;
    selectionEnd.value = xEnd;
    selectionText.value = expanded.slice(xStart, xEnd);
    selectionDisplayLength.value = dEnd - dStart;
    hasSelection.value = true;
    emit("selection", selectionText.value, xStart, xEnd);
  } else {
    hasSelection.value = false;
    selectionText.value = "";
    selectionDisplayLength.value = 0;
    emit("clearSelection");
  }
}

function onScroll() {
  if (mirrorRef.value && textareaRef.value) {
    mirrorRef.value.scrollTop = textareaRef.value.scrollTop;
  }
}

let blurTimer: ReturnType<typeof setTimeout> | null = null;

function onBlur() {
  // Defer clearing so a click handler firing right after blur (e.g. the Map
  // button in the rubric panel) can still read the current selection.
  if (blurTimer) clearTimeout(blurTimer);
  blurTimer = setTimeout(() => {
    hasSelection.value = false;
    selectionText.value = "";
    selectionDisplayLength.value = 0;
    emit("clearSelection");
    blurTimer = null;
  }, 200);
}

function onFocus() {
  // If the user re-focuses the textarea before the blur clear fires, cancel it.
  if (blurTimer) {
    clearTimeout(blurTimer);
    blurTimer = null;
  }
}

// ── Toolbar helpers (operate on displayValue) ──────────────────────────────────

async function applyEdit(transform: (value: string, start: number, end: number) => {
  next: string;
  selectionStart: number;
  selectionEnd: number;
}) {
  const ta = textareaRef.value;
  if (!ta || props.disabled) return;
  const start = ta.selectionStart ?? 0;
  const end = ta.selectionEnd ?? 0;
  const result = transform(displayValue.value, start, end);
  displayValue.value = result.next;
  emitExpanded();
  await nextTick();
  ta.focus();
  ta.selectionStart = result.selectionStart;
  ta.selectionEnd = result.selectionEnd;
  onSelectionChange();
}

function wrap(prefix: string, suffix: string, placeholder = "") {
  applyEdit((value, start, end) => {
    const selected = value.slice(start, end) || placeholder;
    const next = value.slice(0, start) + prefix + selected + suffix + value.slice(end);
    if (start === end) {
      const cursor = start + prefix.length;
      return { next, selectionStart: cursor, selectionEnd: cursor };
    }
    return {
      next,
      selectionStart: start + prefix.length,
      selectionEnd: start + prefix.length + selected.length,
    };
  });
}

function prefixLine(prefix: string) {
  applyEdit((value, start, end) => {
    const lineStart = value.lastIndexOf("\n", start - 1) + 1;
    const before = value.slice(0, lineStart);
    const rest = value.slice(lineStart);
    const next = before + prefix + rest;
    const offset = prefix.length;
    return { next, selectionStart: start + offset, selectionEnd: end + offset };
  });
}

function insertAtCursor(text: string) {
  applyEdit((value, start, end) => {
    const next = value.slice(0, start) + text + value.slice(end);
    const cursor = start + text.length;
    return { next, selectionStart: cursor, selectionEnd: cursor };
  });
}

function insertLink() {
  const ta = textareaRef.value;
  if (!ta) return;
  const url = window.prompt("Enter URL", "https://");
  if (!url) return;
  const selected = displayValue.value.slice(ta.selectionStart ?? 0, ta.selectionEnd ?? 0);
  const text = selected || "link text";
  applyEdit((value, start, end) => {
    const replacement = `[${text}](${url})`;
    const next = value.slice(0, start) + replacement + value.slice(end);
    return {
      next,
      selectionStart: start + 1,
      selectionEnd: start + 1 + text.length,
    };
  });
}

// ── Image insert via base64 token ──────────────────────────────────────────────

function readAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

async function insertImageFile(file: File) {
  uploadError.value = null;
  if (!file.type.startsWith("image/")) {
    uploadError.value = "Only image files can be inserted.";
    return;
  }
  if (file.size > MAX_IMAGE_BYTES) {
    uploadError.value = `Image too large (${(file.size / 1024 / 1024).toFixed(1)} MB). Max 5 MB.`;
    return;
  }
  try {
    const dataUrl = await readAsDataUrl(file);
    imageCounter += 1;
    const id = `img-${imageCounter}`;
    imageMap.value = { ...imageMap.value, [id]: dataUrl };
    const alt = file.name.replace(/\.[^.]+$/, "") || "image";
    insertAtCursor(`![${alt}](image:${id})`);
  } catch {
    uploadError.value = "Could not read the image.";
  }
}

function onPickImage() {
  fileInputRef.value?.click();
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (file) insertImageFile(file);
  input.value = "";
}

function onPaste(e: ClipboardEvent) {
  if (props.disabled) return;
  const items = e.clipboardData?.items;
  if (!items) return;
  for (const item of items) {
    if (item.type.startsWith("image/")) {
      const file = item.getAsFile();
      if (file) {
        e.preventDefault();
        insertImageFile(file);
        return;
      }
    }
  }
}

// Snap selection to image-token boundaries:
//  - Click (no drag) inside a token → select the whole token
//  - Drag selection whose edges land inside a token → expand edges to the token's outer bounds
function snapSelectionToImageTokens() {
	const ta = textareaRef.value;
  if (!ta) return false;
  let start = ta.selectionStart ?? 0;
  let end = ta.selectionEnd ?? 0;
  const isClick = start === end;

  let changed = false;
  const re = new RegExp(TOKEN_RE.source, "g");
  let m: RegExpExecArray | null;
  while ((m = re.exec(displayValue.value)) !== null) {
    const tokStart = m.index;
    const tokEnd = tokStart + m[0].length;

    if (isClick) {
      // Inclusive at the start so a click landing on the leading '!' still snaps
      if (start >= tokStart && start < tokEnd) {
        start = tokStart;
        end = tokEnd;
        changed = true;
        break;
      }
    } else {
      // Drag: expand boundaries that land inside this token
      if (start > tokStart && start < tokEnd) {
        start = tokStart;
        changed = true;
      }
      if (end > tokStart && end < tokEnd) {
        end = tokEnd;
        changed = true;
      }
    }
  }

  if (changed) {
    ta.selectionStart = start;
    ta.selectionEnd = end;
  }
  return changed;
}

let dragging = false;

function onMouseDown() {
  dragging = true;
}

function onMouseUp() {
  // The textarea's selectionStart/End for the click is only finalized after
  // mouseup returns; defer to next frame so we read the post-click position.
  dragging = false;
  requestAnimationFrame(() => {
    snapSelectionToImageTokens();
    onSelectionChange();
  });
}

// Catch mouse releases that happen OUTSIDE the textarea after a drag started inside it.
function onWindowMouseUp() {
  if (!dragging) return;
  dragging = false;
  requestAnimationFrame(() => {
    snapSelectionToImageTokens();
    onSelectionChange();
  });
}

onMounted(() => {
  if (import.meta.client) {
    window.addEventListener("mouseup", onWindowMouseUp);
  }
});
onBeforeUnmount(() => {
  if (import.meta.client) {
    window.removeEventListener("mouseup", onWindowMouseUp);
  }
  if (blurTimer) {
    clearTimeout(blurTimer);
    blurTimer = null;
  }
});

// ── Mirror (highlight overlay) ─────────────────────────────────────────────────

const mirrorHtml = computed(() => {
  const text = displayValue.value;
  if (!text) return "";

  const palette = [
    "bg-yellow-200/60 dark:bg-yellow-500/30",
    "bg-blue-200/60 dark:bg-blue-500/30",
    "bg-green-200/60 dark:bg-green-500/30",
    "bg-pink-200/60 dark:bg-pink-500/30",
    "bg-purple-200/60 dark:bg-purple-500/30",
    "bg-orange-200/60 dark:bg-orange-500/30",
  ];

  const ranges: Array<{ start: number; end: number; criterionName: string; color: string }> =
    props.mappings.flatMap((mapping, idx) => {
      // mapping offsets are in EXPANDED space; translate to display space
      const start = expandedToDisplayOffset(mapping.sectionStart ?? 0);
      const end = expandedToDisplayOffset(mapping.sectionEnd ?? 0);
      if (end <= start) return [];
      return [{
        start,
        end,
        criterionName: mapping.criterionName,
        color: palette[idx % palette.length]!,
      }];
    });

  ranges.sort((a, b) => a.start - b.start);

  let result = "";
  let cursor = 0;
  for (const range of ranges) {
    if (range.start < cursor) continue;
    result += escapeHtml(text.slice(cursor, range.start));
    result += `<mark class="rubric-mapped-section" title="${escapeHtml(range.criterionName)}">${escapeHtml(text.slice(range.start, range.end))}</mark>`;
    cursor = range.end;
  }
  result += escapeHtml(text.slice(cursor));
  return result.replace(/\n/g, "<br>");
});

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

// ── Preview (renders expanded markdown with real images) ──────────────────────

type PurifyFn = (html: string, config?: object) => string;
const purify = ref<PurifyFn | null>(null);
if (import.meta.client) {
  import("dompurify").then((mod) => {
    purify.value = mod.default.sanitize.bind(mod.default) as PurifyFn;
  });
}

const previewHtml = computed(() => {
  const expanded = expand(displayValue.value, imageMap.value);
  if (!expanded) return "";
  const raw = marked.parse(expanded) as string;
  if (purify.value) {
    return purify.value(raw, {
      ALLOWED_TAGS: [
        "h1","h2","h3","h4","h5","h6",
        "p","ul","ol","li","blockquote","pre","code","strong","em",
        "a","img","table","thead","tbody","tr","th","td",
        "hr","br","span","del","sup","sub",
      ],
      ALLOWED_ATTR: ["href","src","alt","title","class","id"],
      ALLOW_DATA_ATTR: false,
      ADD_DATA_URI_TAGS: ["img"],
    });
  }
  return raw;
});

defineExpose({ selectionText, selectionStart, selectionEnd, hasSelection });
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- Toolbar -->
    <div class="flex items-center gap-1 px-2 py-1.5 border-b border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50 flex-shrink-0">
      <button type="button" :disabled="disabled || mode === 'preview'" title="Bold"
        class="p-1.5 rounded text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed"
        @click="wrap('**', '**', 'bold text')"><Bold class="w-3.5 h-3.5" /></button>
      <button type="button" :disabled="disabled || mode === 'preview'" title="Italic"
        class="p-1.5 rounded text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed"
        @click="wrap('*', '*', 'italic text')"><Italic class="w-3.5 h-3.5" /></button>
      <button type="button" :disabled="disabled || mode === 'preview'" title="Heading"
        class="p-1.5 rounded text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed"
        @click="prefixLine('## ')"><Heading class="w-3.5 h-3.5" /></button>

      <span class="w-px h-5 bg-slate-200 dark:bg-slate-700 mx-1" />

      <button type="button" :disabled="disabled || mode === 'preview'" title="Link"
        class="p-1.5 rounded text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed"
        @click="insertLink"><Link2 class="w-3.5 h-3.5" /></button>
      <button type="button" :disabled="disabled || mode === 'preview'"
        title="Insert image (max 5 MB; embedded as base64)"
        class="p-1.5 rounded text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed"
        @click="onPickImage"><ImageIcon class="w-3.5 h-3.5" /></button>
      <input ref="fileInputRef" type="file" accept="image/*" class="hidden" @change="onFileChange" />

      <span class="w-px h-5 bg-slate-200 dark:bg-slate-700 mx-1" />

      <button type="button" :disabled="disabled || mode === 'preview'" title="Bullet list"
        class="p-1.5 rounded text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed"
        @click="prefixLine('- ')"><List class="w-3.5 h-3.5" /></button>
      <button type="button" :disabled="disabled || mode === 'preview'" title="Numbered list"
        class="p-1.5 rounded text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed"
        @click="prefixLine('1. ')"><ListOrdered class="w-3.5 h-3.5" /></button>
      <button type="button" :disabled="disabled || mode === 'preview'" title="Quote"
        class="p-1.5 rounded text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed"
        @click="prefixLine('> ')"><Quote class="w-3.5 h-3.5" /></button>
      <button type="button" :disabled="disabled || mode === 'preview'" title="Inline code"
        class="p-1.5 rounded text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed"
        @click="wrap('`', '`', 'code')"><Code class="w-3.5 h-3.5" /></button>

      <div class="ml-auto inline-flex rounded-md border border-slate-200 dark:border-slate-700 overflow-hidden">
        <button type="button" class="inline-flex items-center gap-1 px-2 py-1 text-xs"
          :class="mode === 'edit' ? 'bg-indigo-500 text-white' : 'bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'"
          @click="mode = 'edit'"><Pencil class="w-3 h-3" />Edit</button>
        <button type="button" class="inline-flex items-center gap-1 px-2 py-1 text-xs border-l border-slate-200 dark:border-slate-700"
          :class="mode === 'preview' ? 'bg-indigo-500 text-white' : 'bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'"
          @click="mode = 'preview'"><Eye class="w-3 h-3" />Preview</button>
      </div>
    </div>

    <!-- Selection hint bar (edit only) -->
    <div v-if="mode === 'edit'"
      class="flex items-center gap-2 px-3 py-2 text-xs border-b border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50 flex-shrink-0 transition-all"
      :class="hasSelection ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-400 dark:text-slate-500'">
      <span v-if="hasSelection" class="flex items-center gap-1.5 font-medium">
        <span class="inline-block w-2 h-2 rounded-full bg-indigo-500 animate-pulse" />
        Text selected ({{ selectionDisplayLength }} characters) — Pick a criterion from the right panel to map it
      </span>
      <span v-else>Highlight text to select a section, then click a criterion on the right. Click an image token to select the whole image.</span>
    </div>

    <div v-if="uploadError"
      class="px-3 py-1.5 text-xs text-red-700 dark:text-red-300 bg-red-50 dark:bg-red-900/20 border-b border-red-200 dark:border-red-800 flex-shrink-0">
      {{ uploadError }}
    </div>

    <!-- Edit mode -->
    <div v-if="mode === 'edit'" class="relative flex-1 min-h-0">
      <div ref="mirrorRef" class="absolute inset-0 overflow-hidden pointer-events-none" aria-hidden="true">
        <div class="mirror-content w-full h-full overflow-hidden whitespace-pre-wrap break-words" v-html="mirrorHtml" />
      </div>
      <textarea ref="textareaRef"
        :value="displayValue"
        :disabled="disabled"
        placeholder="Write your markdown here. Paste an image or use the toolbar to insert one."
        class="relative w-full h-full resize-none bg-transparent outline-none font-mono text-sm text-slate-900 dark:text-slate-100 placeholder-slate-400 dark:placeholder-slate-500 p-4 leading-relaxed caret-indigo-500"
        :class="disabled ? 'cursor-not-allowed opacity-60' : ''"
        spellcheck="false"
        autocomplete="off"
        autocorrect="off"
        @input="onInput"
        @mousedown="onMouseDown"
        @mouseup="onMouseUp"
        @keyup="onSelectionChange"
        @scroll="onScroll"
        @blur="onBlur"
        @focus="onFocus"
        @paste="onPaste" />
    </div>

    <!-- Preview mode -->
    <div v-else class="flex-1 min-h-0 overflow-y-auto px-6 py-6 bg-white dark:bg-slate-900">
      <div v-if="!displayValue" class="h-full flex items-center justify-center text-sm text-slate-400 dark:text-slate-500">
        Nothing to preview yet.
      </div>
      <div v-else class="markdown-preview prose prose-slate dark:prose-invert max-w-none" v-html="previewHtml" />
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
.markdown-preview :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 0.375rem;
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
