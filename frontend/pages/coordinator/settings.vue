<script setup lang="ts">
	import {
		AlertCircle,
		CheckCircle2,
		Settings,
		Upload,
		Save,
		Loader as LoaderIcon,
		FileSpreadsheet,
		X,
	} from "lucide-vue-next";

	definePageMeta({
		middleware: "auth",
		roles: ["Coordinator"],
	});

	const { getAuthToken, updateSystemConfig, uploadStudents } = useApiClient();

	// ─── System Config state ──────────────────────────────────────────
	const activeTermId = ref("");
	const maxTeamSize = ref<number | null>(null);
	const configLoading = ref(false);
	const configError = ref<string | null>(null);
	const configSuccess = ref(false);

	// ─── Student Upload state ─────────────────────────────────────────
	const csvFile = ref<File | null>(null);
	const parsedStudentIds = ref<string[]>([]);
	const parseError = ref<string | null>(null);
	const uploadLoading = ref(false);
	const uploadError = ref<string | null>(null);
	const uploadSuccess = ref(false);
	const uploadResultCount = ref(0);

	// ─── System Config: Save ──────────────────────────────────────────
	async function handleSaveConfig() {
		configError.value = null;
		configSuccess.value = false;

		if (!activeTermId.value.trim()) {
			configError.value = "Active Term ID is required.";
			return;
		}
		if (maxTeamSize.value === null || maxTeamSize.value < 1) {
			configError.value = "Max Team Size must be a positive number.";
			return;
		}

		configLoading.value = true;
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required. Please log in.");
			await updateSystemConfig(
				{
					activeTermId: activeTermId.value.trim(),
					maxTeamSize: maxTeamSize.value,
				},
				token,
			);
			configSuccess.value = true;
		} catch (err) {
			const errorMsg =
				err && typeof err === "object" && "message" in err
					? String(err.message)
					: "Failed to save system configuration.";
			configError.value = errorMsg;
		} finally {
			configLoading.value = false;
		}
	}

	// ─── CSV: Parse ───────────────────────────────────────────────────
	function handleFileChange(event: Event) {
		const target = event.target as HTMLInputElement;
		const file = target.files?.[0];
		parseError.value = null;
		parsedStudentIds.value = [];
		uploadSuccess.value = false;
		uploadError.value = null;

		if (!file) {
			csvFile.value = null;
			return;
		}

		if (!file.name.endsWith(".csv") && !file.name.endsWith(".txt")) {
			parseError.value = "Please select a .csv or .txt file.";
			csvFile.value = null;
			return;
		}

		csvFile.value = file;

		const reader = new FileReader();
		reader.onload = (e) => {
			const text = e.target?.result as string;
			if (!text || !text.trim()) {
				parseError.value = "File is empty.";
				return;
			}

			// Parse: each line may contain a single 11-digit student ID
			// Also supports comma-separated values on a single line
			const ids: string[] = [];
			const invalidLines: string[] = [];
			const lines = text.split(/[\r\n]+/).filter((l) => l.trim().length > 0);

			for (const line of lines) {
				// Split by comma in case of CSV with multiple columns
				const parts = line.split(",").map((p) => p.trim().replace(/"/g, ""));
				for (const part of parts) {
					if (/^[0-9]{11}$/.test(part)) {
						if (!ids.includes(part)) {
							ids.push(part);
						}
					} else if (part.length > 0 && !/^(student_?id|no|number|id)$/i.test(part)) {
						invalidLines.push(part);
					}
				}
			}

			if (ids.length === 0) {
				parseError.value = "No valid 11-digit student IDs found in the file.";
				return;
			}

			if (invalidLines.length > 0) {
				const sample = invalidLines.slice(0, 3).map((v) => `'${v}'`).join(", ");
				const more = invalidLines.length > 3 ? ` ve ${invalidLines.length - 3} tane daha` : "";
				parseError.value = `Note: ${invalidLines.length} invalid entry was skipped because it is not an 11-digit ID (e.g. ${sample}${more}).`;
			}

			parsedStudentIds.value = ids;
		};

		reader.onerror = () => {
			parseError.value = "Failed to read the file.";
		};

		reader.readAsText(file);
	}

	function clearFile() {
		csvFile.value = null;
		parsedStudentIds.value = [];
		parseError.value = null;
		uploadSuccess.value = false;
		uploadError.value = null;
	}

	// ─── CSV: Upload ──────────────────────────────────────────────────
	async function handleUpload() {
		uploadError.value = null;
		uploadSuccess.value = false;

		if (parsedStudentIds.value.length === 0) {
			uploadError.value = "No student IDs to upload. Please select a valid CSV file.";
			return;
		}

		uploadLoading.value = true;
		try {
			const token = getAuthToken();
			if (!token) throw new Error("Authentication required. Please log in.");
			await uploadStudents(parsedStudentIds.value, token);
			uploadResultCount.value = parsedStudentIds.value.length;
			uploadSuccess.value = true;
			// Reset form after success
			csvFile.value = null;
			parsedStudentIds.value = [];
		} catch (err) {
			const errorMsg =
				err && typeof err === "object" && "message" in err
					? String(err.message)
					: "Failed to upload student data.";
			uploadError.value = errorMsg;
		} finally {
			uploadLoading.value = false;
		}
	}
</script>

<template>
  <main
    class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8"
  >
    <div class="mx-auto w-full max-w-4xl space-y-6">
      <!-- Page Header -->
      <header
        class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg"
      >
        <div class="flex items-center gap-3">
          <Settings class="h-7 w-7 text-slate-600 dark:text-slate-400" />
          <div>
            <h1
              class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl"
            >
              System Settings
            </h1>
            <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">
              Manage active term configuration and bulk student upload.
            </p>
          </div>
        </div>
      </header>

      <!-- ═══════════════════════════════════════════════════════════════ -->
      <!-- Section 1: System Configuration                               -->
      <!-- ═══════════════════════════════════════════════════════════════ -->
      <article
        class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg"
      >
        <h2
          class="mb-4 flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white"
        >
          <Save class="h-5 w-5 text-blue-600 dark:text-blue-400" />
          System Configuration
        </h2>

        <div class="space-y-4">
          <!-- Active Term ID -->
          <div>
            <label
              for="activeTermId"
              class="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300"
            >
              Active Term ID
            </label>
            <input
              id="activeTermId"
              v-model="activeTermId"
              type="text"
              placeholder="e.g. 2026-SPRING"
              class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 transition placeholder:text-slate-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 dark:border-slate-600 dark:bg-slate-900 dark:text-white dark:placeholder:text-slate-500 dark:focus:border-blue-400"
            />
          </div>

          <!-- Max Team Size -->
          <div>
            <label
              for="maxTeamSize"
              class="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300"
            >
              Max Team Size
            </label>
            <input
              id="maxTeamSize"
              v-model.number="maxTeamSize"
              type="number"
              min="1"
              max="20"
              placeholder="e.g. 4"
              class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 transition placeholder:text-slate-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 dark:border-slate-600 dark:bg-slate-900 dark:text-white dark:placeholder:text-slate-500 dark:focus:border-blue-400"
            />
          </div>

          <!-- Config Error -->
          <div
            v-if="configError"
            class="flex items-start gap-2 rounded-lg border border-red-300 bg-red-50 p-3 dark:border-red-800 dark:bg-red-950/50"
          >
            <AlertCircle class="mt-0.5 h-4 w-4 shrink-0 text-red-600 dark:text-red-400" />
            <p class="text-sm text-red-700 dark:text-red-400">{{ configError }}</p>
          </div>

          <!-- Config Success -->
          <div
            v-if="configSuccess"
            class="flex items-start gap-2 rounded-lg border border-emerald-300 bg-emerald-50 p-3 dark:border-emerald-800 dark:bg-emerald-950/50"
          >
            <CheckCircle2
              class="mt-0.5 h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400"
            />
            <p class="text-sm text-emerald-700 dark:text-emerald-400">
              System configuration saved successfully.
            </p>
          </div>

          <!-- Save Button -->
          <button
            id="saveConfigBtn"
            :disabled="configLoading"
            class="w-full rounded-lg bg-blue-600 px-4 py-2.5 font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
            @click="handleSaveConfig"
          >
            <LoaderIcon v-if="configLoading" class="mr-2 inline h-4 w-4 animate-spin" />
            {{ configLoading ? "Saving..." : "Save Configuration" }}
          </button>
        </div>
      </article>

      <!-- ═══════════════════════════════════════════════════════════════ -->
      <!-- Section 2: Upload Students                                    -->
      <!-- ═══════════════════════════════════════════════════════════════ -->
      <article
        class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg"
      >
        <h2
          class="mb-4 flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white"
        >
          <Upload class="h-5 w-5 text-purple-600 dark:text-purple-400" />
          Upload Students (CSV)
        </h2>

        <p class="mb-4 text-sm text-slate-600 dark:text-slate-400">
          Upload a CSV file containing 11-digit student IDs (one per line or comma-separated). The
          system will register new students from this file.
        </p>

        <div class="space-y-4">
          <!-- File Picker -->
          <div
            v-if="!csvFile"
            class="flex items-center justify-center rounded-lg border-2 border-dashed border-slate-300 bg-slate-50 p-8 transition dark:border-slate-600 dark:bg-slate-900/50"
          >
            <label
              for="csvFileInput"
              class="flex cursor-pointer flex-col items-center gap-2 text-center"
            >
              <FileSpreadsheet class="h-10 w-10 text-slate-400 dark:text-slate-500" />
              <span class="text-sm font-medium text-slate-700 dark:text-slate-300">
                Click to select a CSV file
              </span>
              <span class="text-xs text-slate-500 dark:text-slate-400">
                Supports .csv and .txt files
              </span>
              <input
                id="csvFileInput"
                type="file"
                accept=".csv,.txt"
                class="hidden"
                @change="handleFileChange"
              />
            </label>
          </div>

          <!-- Selected File Info -->
          <div
            v-if="csvFile"
            class="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-700 dark:bg-slate-900/50"
          >
            <div class="flex items-center gap-2">
              <FileSpreadsheet class="h-5 w-5 text-purple-600 dark:text-purple-400" />
              <div>
                <p class="text-sm font-medium text-slate-900 dark:text-white">
                  {{ csvFile.name }}
                </p>
                <p class="text-xs text-slate-500 dark:text-slate-400">
                  {{ parsedStudentIds.length }} valid student ID{{
                    parsedStudentIds.length !== 1 ? "s" : ""
                  }}
                  found
                </p>
              </div>
            </div>
            <button
              class="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-200 hover:text-slate-600 dark:hover:bg-slate-700 dark:hover:text-slate-300"
              @click="clearFile"
            >
              <X class="h-4 w-4" />
            </button>
          </div>

          <!-- Preview parsed IDs (first 10) -->
          <div
            v-if="parsedStudentIds.length > 0"
            class="rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-700 dark:bg-slate-900/50"
          >
            <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
              Preview (first {{ Math.min(parsedStudentIds.length, 10) }} of {{ parsedStudentIds.length }})
            </p>
            <div class="flex flex-wrap gap-1.5">
              <span
                v-for="id in parsedStudentIds.slice(0, 10)"
                :key="id"
                class="rounded-md bg-purple-100 px-2 py-0.5 text-xs font-mono text-purple-800 dark:bg-purple-900/40 dark:text-purple-300"
              >
                {{ id }}
              </span>
              <span
                v-if="parsedStudentIds.length > 10"
                class="rounded-md bg-slate-200 px-2 py-0.5 text-xs text-slate-600 dark:bg-slate-700 dark:text-slate-400"
              >
                +{{ parsedStudentIds.length - 10 }} more
              </span>
            </div>
          </div>

          <!-- Parse Error -->
          <div
            v-if="parseError"
            class="flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 p-3 dark:border-amber-800 dark:bg-amber-950/50"
          >
            <AlertCircle class="mt-0.5 h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400" />
            <p class="text-sm text-amber-700 dark:text-amber-400">{{ parseError }}</p>
          </div>

          <!-- Upload Error -->
          <div
            v-if="uploadError"
            class="flex items-start gap-2 rounded-lg border border-red-300 bg-red-50 p-3 dark:border-red-800 dark:bg-red-950/50"
          >
            <AlertCircle class="mt-0.5 h-4 w-4 shrink-0 text-red-600 dark:text-red-400" />
            <p class="text-sm text-red-700 dark:text-red-400">{{ uploadError }}</p>
          </div>

          <!-- Upload Success -->
          <div
            v-if="uploadSuccess"
            class="flex items-start gap-2 rounded-lg border border-emerald-300 bg-emerald-50 p-3 dark:border-emerald-800 dark:bg-emerald-950/50"
          >
            <CheckCircle2
              class="mt-0.5 h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400"
            />
            <p class="text-sm text-emerald-700 dark:text-emerald-400">
              Successfully uploaded {{ uploadResultCount }} student ID{{
                uploadResultCount !== 1 ? "s" : ""
              }}.
            </p>
          </div>

          <!-- Upload Button -->
          <button
            id="uploadStudentsBtn"
            :disabled="uploadLoading || parsedStudentIds.length === 0"
            class="w-full rounded-lg bg-purple-600 px-4 py-2.5 font-medium text-white transition hover:bg-purple-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-purple-700 dark:hover:bg-purple-600"
            @click="handleUpload"
          >
            <LoaderIcon v-if="uploadLoading" class="mr-2 inline h-4 w-4 animate-spin" />
            {{
              uploadLoading
                ? "Uploading..."
                : `Upload ${parsedStudentIds.length} Student${parsedStudentIds.length !== 1 ? "s" : ""}`
            }}
          </button>
        </div>
      </article>
    </div>
  </main>
</template>
