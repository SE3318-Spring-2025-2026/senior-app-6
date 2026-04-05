<template>
  <button
    type="button"
    @click="toggleColorMode"
    class="inline-flex h-10 w-10 items-center justify-center rounded-full border border-[var(--border)] bg-[var(--card)] text-[var(--foreground)] shadow-sm transition-colors hover:opacity-90"
    :aria-label="isDark ? 'Switch to light theme' : 'Switch to dark theme'"
    :title="isDark ? 'Switch to light theme' : 'Switch to dark theme'"
  >
    <Sun v-if="isDark" :size="18" />
    <Moon v-else :size="18" />
  </button>
</template>

<script setup lang="ts">
import { Moon, Sun } from "lucide-vue-next";

const colorMode = useColorMode();

const isDark = computed(() => colorMode.value === "dark");

function toggleColorMode() {
  colorMode.preference = isDark.value ? "light" : "dark";
}
</script>
