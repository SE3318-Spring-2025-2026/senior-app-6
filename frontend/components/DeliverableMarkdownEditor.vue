<script setup lang="ts">
import { Editor as ToastEditor } from "@toast-ui/editor";
import "@toast-ui/editor/dist/toastui-editor.css";
import "@toast-ui/editor/dist/theme/toastui-editor-dark.css";
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useColorMode } from "#imports";

const props = withDefaults(defineProps<{
	modelValue: string;
	height?: string;
	placeholder?: string;
}>(), {
	height: "560px",
	placeholder: "Write your deliverable in Markdown. Images are embedded as base64 data.",
});

const emit = defineEmits<{
	(e: "update:modelValue", value: string): void;
}>();

const host = ref<HTMLDivElement | null>(null);
let editor: ToastEditor | null = null;
const colorMode = useColorMode();

function fileToDataUrl(blob: Blob): Promise<string> {
	return new Promise((resolve, reject) => {
		const reader = new FileReader();
		reader.onload = () => {
			if (typeof reader.result === "string") {
				resolve(reader.result);
				return;
			}
			reject(new Error("Failed to read image data"));
		};
		reader.onerror = () => reject(new Error("Failed to read image data"));
		reader.readAsDataURL(blob);
	});
}

function applyTheme(dark: boolean): void {
	if (!host.value) {
		return;
	}
	host.value.classList.toggle("toastui-editor-dark", dark);
}

onMounted(() => {
	if (!host.value) {
		return;
	}

	editor = new ToastEditor({
		el: host.value,
		initialEditType: "wysiwyg",
		previewStyle: "vertical",
		height: props.height,
		initialValue: props.modelValue,
		placeholder: props.placeholder,
		usageStatistics: false,
		hideModeSwitch: true,
		hooks: {
			addImageBlobHook: async (blob, callback) => {
				const dataUrl = await fileToDataUrl(blob);
				callback(dataUrl, blob.name || "embedded-image");
			},
		},
	});

	editor.on("change", () => {
		if (editor) {
			emit("update:modelValue", editor.getMarkdown());
		}
	});

	applyTheme(colorMode.value === "dark");
});

watch(
	() => colorMode.value,
	(value) => applyTheme(value === "dark")
);

watch(
	() => props.modelValue,
	(value) => {
		if (editor && editor.getMarkdown() !== value) {
			editor.setMarkdown(value || "", false);
		}
	}
);

onBeforeUnmount(() => {
	if (editor) {
		editor.destroy();
		editor = null;
	}
});
</script>

<template>
	<div
		class="overflow-hidden rounded-2xl shadow-sm"
		:class="colorMode.value === 'dark' ? 'ring-1 ring-slate-700' : 'ring-1 ring-slate-200'">
		<div ref="host"></div>
	</div>
</template>

<style scoped>
:deep(.toastui-editor-defaultUI) {
	border: none;
}
</style>
