<script setup lang="ts">
	const model = defineModel<string | number>();
	const modelType = typeof model.value;
	const numberRegex = /^[+-]?(\d+\.?\d*|\.\d+)$/;
	const props = defineProps<{
		min? : number;
		max? : number;
	}>();

	async function handleInput(event: Event) {
		const target = event.target as HTMLInputElement;
		const value = target.value;

		if (value === "" || value === "+" || value === "-" || value === "." || value === "+." || value === "-.") {
			if (modelType === "string") {
				model.value = String(value);
				return;
			}
			model.value = undefined;
			return;
		}

		if (numberRegex.test(value)) {
			if ((value.length > 1 && (value.endsWith(".") || value.startsWith("."))) ||
					(value === "+" || value === "-" || value === "+." || value === "-.")) {
				return;
			}
			if (modelType === "string") {
				model.value = value as string;
				return;
			}
			const numericValue = Number(value);
			if (!isNaN(numericValue)) {
				if (props.min !== undefined && numericValue < props.min) {
					model.value = props.min as string | number;
				} else if (props.max !== undefined && numericValue > props.max) {
					model.value = props.max as string | number;
				} else {
					model.value = numericValue as string | number;
				}
			} else {
				model.value = undefined;
			}
		}
	}

	async function handleBlur(event: Event) {
		const target = event.target as HTMLInputElement;
		const value = target.value;

		if (value === "" || value === "+" || value === "-" || value === "." || value === "+." || value === "-.") {
			model.value = undefined;
			return;
		}

		if (numberRegex.test(value)) {
			if (modelType === "string") {
				model.value = value as string;
				return;
			}
			const numericValue = Number(value);
			if (!isNaN(numericValue)) {
				if (props.min !== undefined && numericValue < props.min) {
					model.value = props.min as string | number;
				} else if (props.max !== undefined && numericValue > props.max) {
					model.value = props.max as string | number;
				} else {
					model.value = numericValue as string | number;
				}
			} else {
				model.value = undefined;
			}
		} else {
			model.value = undefined;
		}
	}

</script>

<template>
	<input
		type="text"
		pattern="[0-9\.\-]*"
		:value="model"
		@input="handleInput"
		@blur="handleBlur"
		class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder-slate-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-600 dark:bg-slate-800 dark:text-white dark:placeholder-slate-500 dark:focus:border-blue-400 dark:focus:ring-blue-400/20"
	/>
</template>
