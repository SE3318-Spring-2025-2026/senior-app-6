<script setup lang="ts">
import { computed } from "vue";
import { marked } from "marked";
import DOMPurify from "dompurify";

const props = defineProps<{
  content: string;
}>();

const renderedHtml = computed(() => {
  if (!props.content) return "";
  const rawHtml = marked.parse(props.content);
  return DOMPurify.sanitize(rawHtml as string);
});
</script>

<template>
  <div
    class="prose prose-slate dark:prose-invert max-w-none prose-img:rounded-xl prose-img:shadow-md"
    v-html="renderedHtml"
  ></div>
</template>

<style>
/* Toast UI Markdown rendering alignment */
.prose img {
  display: block;
  margin-left: auto;
  margin-right: auto;
  max-width: 100%;
}
</style>
