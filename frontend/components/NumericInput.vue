<script setup lang="ts">
	const model = defineModel<string>();

	async function handleInput(event: Event) {
		const target = event.target as HTMLInputElement;
		const value = target.value;
		if (/^\d*$/.test(value)) {
			model.value = value;
		}
		target.value = model.value?.toString() || "";
	}

</script>

<template>
	<input
		type="text"
		pattern="[0-9]*"
		:value="model"
		@input="handleInput"
		class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder-slate-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-600 dark:bg-slate-800 dark:text-white dark:placeholder-slate-500 dark:focus:border-blue-400 dark:focus:ring-blue-400/20"
	/>
</template>
