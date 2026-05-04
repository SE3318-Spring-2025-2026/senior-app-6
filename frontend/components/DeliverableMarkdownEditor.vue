<script setup lang="ts">
import { Editor as ToastEditor } from "@toast-ui/editor";
import "@toast-ui/editor/dist/toastui-editor.css";
import { onBeforeUnmount, onMounted, ref, watch } from "vue";

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
});

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
	<div class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
		<div ref="host" />
	</div>
</template>