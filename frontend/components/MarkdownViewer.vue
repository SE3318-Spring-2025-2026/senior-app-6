<script setup lang="ts">
import { marked } from "marked";

const props = defineProps<{
  content: string;
  highlightRef: string | null;
}>();

const viewerRef = ref<HTMLDivElement | null>(null);
const activeHighlightIds = ref<string[]>([]);

// DOMPurify is browser-only; use a ref so computed re-runs after it loads
type PurifyFn = (html: string, config?: object) => string;
const purify = ref<PurifyFn | null>(null);
if (import.meta.client) {
  import("dompurify").then((mod) => {
    purify.value = mod.default.sanitize.bind(mod.default) as PurifyFn;
  });
}

const sanitizedHtml = computed(() => {
  if (!props.content) return "";
  const raw = marked.parse(props.content) as string;
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
    });
  }
  return raw;
});

function clearHighlights() {
  if (!viewerRef.value) return;
  const marks = viewerRef.value.querySelectorAll("mark.rubric-highlight");
  marks.forEach((mark) => {
    const parent = mark.parentNode;
    if (parent) {
      parent.replaceChild(document.createTextNode(mark.textContent ?? ""), mark);
      parent.normalize();
    }
  });
  activeHighlightIds.value = [];
}

function applyHighlight(searchText: string) {
  if (!viewerRef.value || !searchText.trim()) return;

  clearHighlights();

  const walker = document.createTreeWalker(
    viewerRef.value,
    NodeFilter.SHOW_TEXT,
    null
  );

  const textNodes: Text[] = [];
  let node: Node | null;
  while ((node = walker.nextNode())) {
    textNodes.push(node as Text);
  }

  const lowerSearch = searchText.toLowerCase();
  let foundAny = false;

  textNodes.forEach((textNode) => {
    const text = textNode.textContent ?? "";
    const lowerText = text.toLowerCase();
    const idx = lowerText.indexOf(lowerSearch);
    if (idx === -1) return;

    foundAny = true;
    const id = `highlight-${Math.random().toString(36).slice(2)}`;
    activeHighlightIds.value.push(id);

    const before = text.slice(0, idx);
    const match = text.slice(idx, idx + searchText.length);
    const after = text.slice(idx + searchText.length);

    const mark = document.createElement("mark");
    mark.className = "rubric-highlight";
    mark.id = id;
    mark.textContent = match;

    const frag = document.createDocumentFragment();
    if (before) frag.appendChild(document.createTextNode(before));
    frag.appendChild(mark);
    if (after) frag.appendChild(document.createTextNode(after));

    textNode.parentNode?.replaceChild(frag, textNode);
  });

  if (foundAny && activeHighlightIds.value.length > 0) {
    const first = document.getElementById(activeHighlightIds.value[0]!);
    first?.scrollIntoView({ behavior: "smooth", block: "center" });
  }
}

watch(
  () => props.highlightRef,
  (val) => {
    nextTick(() => {
      if (val) {
        applyHighlight(val);
      } else {
        clearHighlights();
      }
    });
  }
);

watch(sanitizedHtml, () => {
  nextTick(() => {
    if (props.highlightRef) {
      applyHighlight(props.highlightRef);
    }
  });
});
</script>

<template>
  <div
    ref="viewerRef"
    class="markdown-viewer prose prose-slate dark:prose-invert max-w-none w-full select-text"
    aria-readonly="true"
    v-html="sanitizedHtml"
  />
</template>

<style scoped>
.markdown-viewer {
  pointer-events: auto;
  user-select: text;
  -webkit-user-select: text;
}

/* Prevent any kind of editing */
.markdown-viewer :deep(*) {
  pointer-events: auto;
  outline: none;
}

.markdown-viewer :deep([contenteditable]) {
  contenteditable: false;
}
</style>

<style>
/* Global styles for highlight marks */
.rubric-highlight {
  background-color: rgb(251 191 36 / 0.45);
  border-radius: 2px;
  padding: 1px 2px;
  transition: background-color 0.2s ease;
}

.dark .rubric-highlight {
  background-color: rgb(251 191 36 / 0.35);
  color: inherit;
}
</style>
