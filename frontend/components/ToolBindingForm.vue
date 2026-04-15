<script setup lang="ts">
import { CheckCircle2, Lock, Loader2 } from "lucide-vue-next";

defineOptions({
  name: "ToolBindingFormCard",
});

type ToolBindingField = {
  name: string;
  label: string;
  type?: "text" | "url" | "password";
  placeholder?: string;
  autocomplete?: string;
  helpText?: string;
  lockedValue?: string;
  lockedPlaceholder?: string;
};

const props = withDefaults(defineProps<{
  title: string;
  description: string;
  toolName: string;
  fields: ToolBindingField[];
  modelValue: Record<string, string | undefined>;
  fieldErrors?: Record<string, string>;
  errorMessage?: string | null;
  loading?: boolean;
  locked?: boolean;
  submitLabel?: string;
}>(), {
  fieldErrors: () => ({}),
  errorMessage: null,
  loading: false,
  locked: false,
  submitLabel: "Bind credentials",
});

const emit = defineEmits<{
  (event: "submit", payload: Record<string, string>): void;
}>();

const formValues = reactive<Record<string, string>>({});

watch(
  () => props.modelValue,
  (nextValues) => {
    for (const field of props.fields) {
      formValues[field.name] = nextValues[field.name] ?? "";
    }
  },
  { immediate: true, deep: true }
);

function updateFieldValue(name: string, value: string) {
  formValues[name] = value;
}

function getDisplayedValue(field: ToolBindingField): string {
  if (!props.locked) {
    return formValues[field.name] ?? "";
  }

  if (field.type === "password") {
    return field.lockedValue ?? "";
  }

  return props.modelValue[field.name] ?? formValues[field.name] ?? "";
}

function getPlaceholder(field: ToolBindingField): string {
  if (props.locked && field.type === "password") {
    return field.lockedPlaceholder ?? "Stored securely";
  }

  return field.placeholder ?? "";
}

function handleSubmit() {
  const payload = props.fields.reduce<Record<string, string>>((acc, field) => {
    acc[field.name] = (formValues[field.name] ?? "").trim();
    return acc;
  }, {});

  emit("submit", payload);
}
</script>

<template>
  <article class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
    <div class="flex items-start justify-between gap-4">
      <div>
        <div class="flex items-center gap-2">
          <h2 class="text-lg font-semibold text-slate-900 dark:text-white">
            {{ title }}
          </h2>
          <span
            v-if="locked"
            class="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300"
          >
            <CheckCircle2 class="h-3.5 w-3.5" />
            Bound
          </span>
        </div>
        <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
          {{ description }}
        </p>
      </div>

      <div
        class="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border"
        :class="locked
          ? 'border-emerald-200 bg-emerald-50 text-emerald-600 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300'
          : 'border-slate-200 bg-slate-50 text-slate-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300'"
      >
        <CheckCircle2 v-if="locked" class="h-5 w-5" />
        <Lock v-else class="h-5 w-5" />
      </div>
    </div>

    <div
      v-if="locked"
      class="mt-4 rounded-xl border border-emerald-200 bg-emerald-50/80 px-4 py-3 text-sm text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-300"
    >
      {{ toolName }} credentials are already bound for this group and are now read-only.
    </div>

    <div
      v-if="errorMessage"
      class="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-950/40 dark:text-red-300"
    >
      {{ errorMessage }}
    </div>

    <form class="mt-5 space-y-4" @submit.prevent="handleSubmit" novalidate>
      <label
        v-for="field in fields"
        :key="field.name"
        class="block space-y-1.5"
      >
        <span class="text-sm font-medium text-slate-700 dark:text-slate-300">
          {{ field.label }}
        </span>

        <input
          :type="locked && field.type === 'password' ? 'text' : (field.type ?? 'text')"
          :value="getDisplayedValue(field)"
          :placeholder="getPlaceholder(field)"
          :autocomplete="field.autocomplete"
          :readonly="locked"
          :disabled="loading"
          class="w-full rounded-xl border px-3 py-2.5 text-sm outline-none transition placeholder:text-slate-400 disabled:cursor-not-allowed disabled:opacity-70"
          :class="locked
            ? 'border-emerald-200 bg-emerald-50/60 text-slate-700 dark:border-emerald-800 dark:bg-emerald-950/20 dark:text-slate-200'
            : 'border-slate-300 bg-white text-slate-900 focus:border-slate-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-slate-400'"
          @input="updateFieldValue(field.name, ($event.target as HTMLInputElement).value)"
        />

        <p v-if="field.helpText" class="text-xs text-slate-500 dark:text-slate-400">
          {{ field.helpText }}
        </p>
        <p v-if="fieldErrors[field.name]" class="text-xs text-red-600 dark:text-red-400">
          {{ fieldErrors[field.name] }}
        </p>
      </label>

      <button
        v-if="!locked"
        type="submit"
        :disabled="loading"
        class="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-slate-900 px-4 py-3 text-sm font-medium text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-70 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-slate-200"
      >
        <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
        <span>{{ loading ? `Binding ${toolName}...` : submitLabel }}</span>
      </button>
    </form>
  </article>
</template>
