<template>
  <div class="min-h-full flex flex-col bg-[var(--background)] text-[var(--foreground)]">
    <div class="fixed top-4 right-4 z-50">
      <UiThemeToggle />
    </div>
    <slot />
  </div>
</template>
