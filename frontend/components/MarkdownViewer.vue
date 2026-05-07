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
      ADD_DATA_URI_TAGS: ["img"],
    });
  }
  return raw;
});

const IMAGE_MD_GLOBAL_RE = /!\[[^\]]*\]\(data:image\/[A-Za-z0-9.+-]+;base64,[A-Za-z0-9+/=]+\)/g;

// Split a mapping's sectionKey into alternating text/image parts.
function splitSectionKey(key: string): Array<{ kind: "text" | "image"; value: string; src?: string }> {
  const parts: Array<{ kind: "text" | "image"; value: string; src?: string }> = [];
  let cursor = 0;
  const re = new RegExp(IMAGE_MD_GLOBAL_RE.source, "g");
  let m: RegExpExecArray | null;
  while ((m = re.exec(key)) !== null) {
    if (m.index > cursor) {
      parts.push({ kind: "text", value: key.slice(cursor, m.index) });
    }
    const inner = m[0].match(/\((data:[^)]+)\)/);
    parts.push({ kind: "image", value: m[0], src: inner ? inner[1] : "" });
    cursor = m.index + m[0].length;
  }
  if (cursor < key.length) {
    parts.push({ kind: "text", value: key.slice(cursor) });
  }
  return parts;
}

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
  viewerRef.value
    .querySelectorAll("img.rubric-image-highlight")
    .forEach((img) => img.classList.remove("rubric-image-highlight"));
  activeHighlightIds.value = [];
}

function highlightImageBySrc(dataUrl: string): HTMLImageElement | null {
  if (!viewerRef.value) return null;
  let first: HTMLImageElement | null = null;
  viewerRef.value.querySelectorAll<HTMLImageElement>("img").forEach((img) => {
    if (img.src === dataUrl || img.getAttribute("src") === dataUrl) {
      img.classList.add("rubric-image-highlight");
      if (!first) first = img;
    }
  });
  return first;
}

function highlightPlainText(searchText: string): HTMLElement | null {
  if (!viewerRef.value) return null;
  const trimmed = searchText.trim();
  if (!trimmed) return null;

  const walker = document.createTreeWalker(viewerRef.value, NodeFilter.SHOW_TEXT, null);
  const textNodes: Text[] = [];
  let node: Node | null;
  while ((node = walker.nextNode())) {
    textNodes.push(node as Text);
  }

  const lowerSearch = trimmed.toLowerCase();
  let firstMark: HTMLElement | null = null;

  textNodes.forEach((textNode) => {
    const text = textNode.textContent ?? "";
    const lowerText = text.toLowerCase();
    const idx = lowerText.indexOf(lowerSearch);
    if (idx === -1) return;

    const id = `highlight-${Math.random().toString(36).slice(2)}`;
    activeHighlightIds.value.push(id);

    const before = text.slice(0, idx);
    const match = text.slice(idx, idx + trimmed.length);
    const after = text.slice(idx + trimmed.length);

    const mark = document.createElement("mark");
    mark.className = "rubric-highlight";
    mark.id = id;
    mark.textContent = match;

    const frag = document.createDocumentFragment();
    if (before) frag.appendChild(document.createTextNode(before));
    frag.appendChild(mark);
    if (after) frag.appendChild(document.createTextNode(after));

    textNode.parentNode?.replaceChild(frag, textNode);
    if (!firstMark) firstMark = mark;
  });

  return firstMark;
}

function applyHighlight(searchText: string) {
  if (!viewerRef.value || !searchText.trim()) return;

  clearHighlights();

  const parts = splitSectionKey(searchText);
  // No images embedded → use plain-text path directly with the whole search string
  if (parts.every((p) => p.kind === "text")) {
    const first = highlightPlainText(searchText);
    if (first) first.scrollIntoView({ behavior: "smooth", block: "center" });
    return;
  }

  // Mixed (or pure image) mapping → highlight each segment independently
  let scrollTarget: HTMLElement | null = null;
  for (const part of parts) {
    if (part.kind === "image" && part.src) {
      const img = highlightImageBySrc(part.src);
      if (img && !scrollTarget) scrollTarget = img;
    } else if (part.kind === "text" && part.value.trim()) {
      const mark = highlightPlainText(part.value);
      if (mark && !scrollTarget) scrollTarget = mark;
    }
  }
  if (scrollTarget) scrollTarget.scrollIntoView({ behavior: "smooth", block: "center" });
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

.rubric-image-highlight {
  outline: 3px solid rgb(251 191 36 / 0.85);
  outline-offset: 2px;
  border-radius: 4px;
  box-shadow: 0 0 0 4px rgb(251 191 36 / 0.25);
  transition: outline-color 0.2s ease, box-shadow 0.2s ease;
}

.dark .rubric-image-highlight {
  outline-color: rgb(251 191 36 / 0.7);
  box-shadow: 0 0 0 4px rgb(251 191 36 / 0.2);
}
</style>
