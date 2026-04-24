<script setup lang="ts">
import { z } from "zod";
import { AlertCircle, ArrowLeft, CheckCircle, FolderGit2, Loader2, ShieldCheck, X } from "lucide-vue-next";
import type { BindGithubRequest, BindJiraRequest } from "~/composables/useApiClient";
import type { GroupDetailResponse } from "~/types/group";
import { useAuthStore } from "~/stores/auth";

defineOptions({
  name: "StudentGroupToolsPage",
});

definePageMeta({
  middleware: "auth",
  roles: ["Student"],
});

const { getAuthToken, fetchMyGroup, bindJiraTool, bindGithubTool } = useApiClient();
const authStore = useAuthStore();
const router = useRouter();

const EXPIRY_THRESHOLD_MS = 7 * 24 * 60 * 60 * 1000;

const group = ref<GroupDetailResponse | null>(null);
const isLoading = ref(true);
const loadError = ref<string | null>(null);

const jiraSubmitting = ref(false);
const jiraError = ref<string | null>(null);
const jiraFieldErrors = ref<Record<string, string>>({});

const githubSubmitting = ref(false);
const githubError = ref<string | null>(null);
const githubFieldErrors = ref<Record<string, string>>({});

const toast = ref({
  show: false,
  message: "",
  type: "success" as "success" | "error"
});

function showToast(message: string, type: "success" | "error" = "success") {
  toast.value = { show: true, message, type };
  setTimeout(() => {
    toast.value.show = false;
  }, 5000);
}

const jiraSchema = z.object({
  jiraSpaceUrl: z.string().trim().url("Enter a valid JIRA space URL."),
  jiraEmail: z.string().trim().email("Enter a valid Atlassian account email."),
  jiraProjectKey: z.string().trim().min(1, "JIRA project key is required."),
  jiraApiToken: z.string().trim().min(1, "JIRA API token is required."),
  jiraTokenExpiresAt: z.string().optional(),
});

const githubSchema = z.object({
  githubOrgName: z.string().trim().min(1, "GitHub organization name is required."),
  githubPat: z.string().trim().min(1, "GitHub personal access token is required."),
  githubRepoName: z.string().trim().min(1, "GitHub repository name is required."),
});

const currentStudentId = computed(() =>
  authStore.userInfo?.userType === "Student" ? authStore.userInfo.studentId : null
);

const isTeamLeader = computed(() =>
  Boolean(
    group.value?.members.some(
      (member) =>
        member.studentId === currentStudentId.value && member.role === "TEAM_LEADER"
    )
  )
);

const jiraFormValues = computed(() => ({
  jiraSpaceUrl: group.value?.jiraSpaceUrl ?? "",
  jiraEmail: group.value?.jiraEmail ?? "",
  jiraProjectKey: group.value?.jiraProjectKey ?? "",
  jiraApiToken: "",
  jiraTokenExpiresAt: group.value?.jiraTokenExpiresAt ?? "",
}));

const githubFormValues = computed(() => ({
  githubOrgName: group.value?.githubOrgName ?? "",
  githubPat: "",
  githubRepoName: group.value?.githubRepoName ?? "",
}));

const githubTokenInvalid = computed(() => group.value?.githubTokenValid === false);
const jiraTokenInvalid = computed(() => group.value?.jiraTokenValid === false);

const githubPatExpiringSoon = computed(() => {
  if (githubTokenInvalid.value) return false;
  const exp = group.value?.githubPatExpiresAt;
  if (!exp) return false;
  return new Date(exp).getTime() - Date.now() < EXPIRY_THRESHOLD_MS;
});

const jiraTokenExpiringSoon = computed(() => {
  if (jiraTokenInvalid.value) return false;
  const exp = group.value?.jiraTokenExpiresAt;
  if (!exp) return false;
  return new Date(exp).getTime() - Date.now() < EXPIRY_THRESHOLD_MS;
});

function formatDate(dateStr?: string | null): string {
  if (!dateStr) return "";
  return new Date(dateStr).toLocaleDateString();
}

const jiraToolStatus = computed(() => {
  if (!group.value?.jiraBound) return "unbound";
  if (group.value.jiraTokenValid === false) return "invalid";
  if (jiraTokenExpiringSoon.value) return "expiring";
  return "healthy";
});

const githubToolStatus = computed(() => {
  if (!group.value?.githubBound) return "unbound";
  if (group.value.githubTokenValid === false) return "invalid";
  if (githubPatExpiringSoon.value) return "expiring";
  return "healthy";
});

async function loadGroup() {
  isLoading.value = true;
  loadError.value = null;

  try {
    const token = getAuthToken();
    if (!token) {
      throw new Error("Authentication required. Please log in again.");
    }

    const fetchedGroup = await fetchMyGroup(token);
    group.value = fetchedGroup;

    if (!fetchedGroup.members.some((member) => member.studentId === currentStudentId.value)) {
      await router.replace("/forbidden");
      return;
    }

    if (!fetchedGroup.members.some((member) => member.studentId === currentStudentId.value && member.role === "TEAM_LEADER")) {
      await router.replace("/forbidden");
      return;
    }
  } catch (error: unknown) {
    const apiError = error as { status?: number; message?: string };
    if (apiError.status === 404 || apiError.status === 403) {
      await router.replace("/forbidden");
      return;
    }

    const errorMessage =
      error && typeof error === "object" && "message" in error
        ? String(error.message)
        : "Failed to load group details.";
    loadError.value = errorMessage;
  } finally {
    isLoading.value = false;
  }
}

onMounted(loadGroup);

async function handleJiraSubmit(payload: Record<string, string>) {
  jiraError.value = null;
  jiraFieldErrors.value = {};

  const result = jiraSchema.safeParse(payload);
  if (!result.success) {
    const fields = result.error.flatten().fieldErrors;
    jiraFieldErrors.value = {
      jiraSpaceUrl: fields.jiraSpaceUrl?.[0] ?? "",
      jiraEmail: fields.jiraEmail?.[0] ?? "",
      jiraProjectKey: fields.jiraProjectKey?.[0] ?? "",
      jiraApiToken: fields.jiraApiToken?.[0] ?? "",
      jiraTokenExpiresAt: fields.jiraTokenExpiresAt?.[0] ?? "",
    };
    return;
  }

  if (!group.value) return;

  jiraSubmitting.value = true;
  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required.");

    await bindJiraTool(group.value.id, result.data as BindJiraRequest, token);
    await loadGroup();
    showToast("JIRA successfully bound to your group!", "success");
  } catch (error: unknown) {
    const msg = error && typeof error === "object" && "message" in error
        ? String(error.message)
        : "Failed to bind JIRA credentials.";
    jiraError.value = msg;
    showToast(msg, "error");
  } finally {
    jiraSubmitting.value = false;
  }
}

async function handleGithubSubmit(payload: Record<string, string>) {
  githubError.value = null;
  githubFieldErrors.value = {};

  const result = githubSchema.safeParse(payload);
  if (!result.success) {
    const fields = result.error.flatten().fieldErrors;
    githubFieldErrors.value = {
      githubOrgName: fields.githubOrgName?.[0] ?? "",
      githubPat: fields.githubPat?.[0] ?? "",
      githubRepoName: fields.githubRepoName?.[0] ?? "",
    };
    return;
  }

  if (!group.value) return;

  githubSubmitting.value = true;
  try {
    const token = getAuthToken();
    if (!token) throw new Error("Authentication required.");

    await bindGithubTool(group.value.id, result.data as BindGithubRequest, token);
    await loadGroup();
    showToast("GitHub successfully bound to your group!", "success");
  } catch (error: unknown) {
    const msg = error && typeof error === "object" && "message" in error
        ? String(error.message)
        : "Failed to bind GitHub credentials.";
    githubError.value = msg;
    showToast(msg, "error");
  } finally {
    githubSubmitting.value = false;
  }
}
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-6xl space-y-6">
      <NuxtLink
        to="/student/group"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to group hub
      </NuxtLink>

      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div>
            <p class="text-sm font-medium uppercase tracking-[0.18em] text-slate-500 dark:text-slate-400">
              Group Integrations
            </p>
            <h1 class="mt-2 text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
              Bind external tools for your team
            </h1>
            <p class="mt-2 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
              Team Leaders can securely connect the group JIRA workspace and GitHub organization.
              Once a tool is bound, its form becomes read-only for the rest of the team lifecycle.
            </p>
          </div>

          <div
            v-if="group"
            class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-700 dark:bg-slate-900"
          >
            <p class="text-xs font-medium uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400">
              Current Group
            </p>
            <p class="mt-1 text-base font-semibold text-slate-900 dark:text-white">
              {{ group.groupName }}
            </p>
            <div class="mt-2">
              <UiGroupStatusBadge :status="group.status" />
            </div>
          </div>
        </div>
      </header>

      <!-- Token warning banners -->
      <div v-if="githubTokenInvalid || jiraTokenInvalid || githubPatExpiringSoon || jiraTokenExpiringSoon" class="space-y-3">
        <div
          v-if="githubTokenInvalid"
          class="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 px-5 py-4 dark:border-red-800 dark:bg-red-950/40"
        >
          <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
          <p class="text-sm font-medium text-red-800 dark:text-red-200">
            GitHub PAT is no longer valid. Please re-bind with a new token.
          </p>
        </div>
        <div
          v-if="jiraTokenInvalid"
          class="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 px-5 py-4 dark:border-red-800 dark:bg-red-950/40"
        >
          <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
          <p class="text-sm font-medium text-red-800 dark:text-red-200">
            JIRA API token is no longer valid. Please re-bind with a new token.
          </p>
        </div>
        <div
          v-if="githubPatExpiringSoon"
          class="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-5 py-4 dark:border-amber-800 dark:bg-amber-950/40"
        >
          <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-amber-600 dark:text-amber-400" />
          <p class="text-sm font-medium text-amber-800 dark:text-amber-200">
            Your GitHub PAT expires on {{ formatDate(group?.githubPatExpiresAt) }}. Consider updating it soon.
          </p>
        </div>
        <div
          v-if="jiraTokenExpiringSoon"
          class="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-5 py-4 dark:border-amber-800 dark:bg-amber-950/40"
        >
          <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-amber-600 dark:text-amber-400" />
          <p class="text-sm font-medium text-amber-800 dark:text-amber-200">
            Your JIRA API token expires on {{ formatDate(group?.jiraTokenExpiresAt) }}. Consider updating it soon.
          </p>
        </div>
      </div>

      <div
        v-if="isLoading"
        class="flex items-center justify-center rounded-2xl border border-slate-200 bg-white p-12 shadow-sm dark:border-slate-700 dark:bg-slate-800"
      >
        <div class="text-center">
          <Loader2 class="mx-auto h-8 w-8 animate-spin text-slate-600 dark:text-slate-300" />
          <p class="mt-3 text-sm text-slate-600 dark:text-slate-400">
            Loading group integrations...
          </p>
        </div>
      </div>

      <div
        v-else-if="loadError"
        class="flex items-start gap-3 rounded-2xl border border-red-200 bg-red-50 p-5 shadow-sm dark:border-red-800 dark:bg-red-950/40"
      >
        <AlertCircle class="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
        <div>
          <p class="font-medium text-red-900 dark:text-red-200">Unable to open tool bindings</p>
          <p class="mt-1 text-sm text-red-700 dark:text-red-300">{{ loadError }}</p>
        </div>
      </div>

      <section v-else-if="group && isTeamLeader" class="space-y-6">
        <div class="grid gap-6 xl:grid-cols-2">
          <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800">
            <h2 class="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
              <ShieldCheck class="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
              Secure binding flow
            </h2>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Credentials are validated by the backend before they are stored. You can update credentials at any time — re-binding validates the new token before overwriting the old one.
            </p>
          </div>

          <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800">
            <h2 class="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
              <FolderGit2 class="h-5 w-5 text-slate-700 dark:text-slate-300" />
              Binding checklist
            </h2>
            <ul class="mt-3 space-y-2 text-sm text-slate-600 dark:text-slate-400">
              <li>Enter the JIRA workspace URL, Atlassian account email, project key, and API token.</li>
              <li>Enter the GitHub organization name and a PAT with repository access.</li>
              <li>Each tool shows its current health status (Active / Expiring Soon / Token Invalid) after binding.</li>
            </ul>
          </div>
        </div>

        <div class="grid gap-6 xl:grid-cols-2">
          <ToolBindingForm
            title="JIRA Binding"
            tool-name="JIRA"
            description="Connect the team's JIRA workspace for project tracking."
            :fields="[
              {
                name: 'jiraSpaceUrl',
                label: 'JIRA Space URL',
                type: 'url',
                placeholder: 'https://your-team.atlassian.net',
                autocomplete: 'url',
                helpText: 'Your Atlassian site base URL. Open Jira in your browser — it is the part before /jira in the address bar (e.g. https://yourteam.atlassian.net).',
              },
              {
                name: 'jiraEmail',
                label: 'Atlassian Account Email',
                type: 'email',
                placeholder: 'you@example.com',
                autocomplete: 'email',
                helpText: 'The exact email of your Atlassian account. To verify: click your profile picture in Jira → Manage account → Profile. Use whatever email is shown there.',
              },
              {
                name: 'jiraProjectKey',
                label: 'Project Key',
                type: 'text',
                placeholder: 'SPM',
                autocomplete: 'off',
                helpText: 'The short uppercase code for your project. Find it in the Jira board URL: .../projects/PROJECTKEY/boards — the all-caps part is your key.',
              },
              {
                name: 'jiraApiToken',
                label: 'API Token',
                type: 'password',
                placeholder: 'Enter a JIRA API token',
                autocomplete: 'new-password',
                lockedPlaceholder: 'Stored securely after validation',
                helpText: 'Generate one at id.atlassian.com → Security → API tokens → Create API token. Must be created while logged in as the same email above.',
              },
              {
                name: 'jiraTokenExpiresAt',
                label: 'Token Expiry Date (optional)',
                type: 'date',
                placeholder: '',
                autocomplete: 'off',
                helpText: 'Optional. The expiry date of your API token — visible in your Atlassian account under Security → API tokens.',
              },
            ]"
            :model-value="jiraFormValues"
            :field-errors="jiraFieldErrors"
            :error-message="jiraError"
            :loading="jiraSubmitting"
            :tool-status="jiraToolStatus"
            :locked="group?.status === 'DISBANDED'"
            :submit-label="jiraToolStatus === 'unbound' ? 'Bind JIRA' : 'Update JIRA Credentials'"
            @submit="handleJiraSubmit"
          />

          <ToolBindingForm
            title="GitHub Binding"
            tool-name="GitHub"
            description="Connect the team's GitHub organization for repository access checks."
            :fields="[
              {
                name: 'githubOrgName',
                label: 'Organization Name',
                type: 'text',
                placeholder: 'senior-project-team',
                autocomplete: 'organization',
                helpText: 'Your GitHub organization name — not your personal username. Find it in the org URL: github.com/ORG-NAME. Ask your team lead if you are unsure which org to use.',
              },
              {
                name: 'githubPat',
                label: 'Personal Access Token',
                type: 'password',
                placeholder: 'Enter a GitHub PAT',
                autocomplete: 'new-password',
                lockedPlaceholder: 'Stored securely after validation',
                helpText: 'Generate at github.com → Settings → Developer settings → Personal access tokens → Tokens (classic). Select the repo scope. The token must have access to the organization above.',
              },
              {
                name: 'githubRepoName',
                label: 'Repository Name',
                type: 'text',
                placeholder: 'my-project-repo',
                autocomplete: 'off',
                helpText: 'The exact name of the repository under the organization above.',
              },
            ]"
            :model-value="githubFormValues"
            :field-errors="githubFieldErrors"
            :error-message="githubError"
            :loading="githubSubmitting"
            :tool-status="githubToolStatus"
            :locked="group?.status === 'DISBANDED'"
            :submit-label="githubToolStatus === 'unbound' ? 'Bind GitHub' : 'Update GitHub Credentials'"
            @submit="handleGithubSubmit"
          />
        </div>
      </section>
    </div>

    <!-- Success/Error Toast -->
    <Transition name="toast">
      <div
        v-if="toast.show"
        class="fixed bottom-8 right-8 z-[100] flex items-center gap-3 rounded-xl border px-6 py-4 shadow-2xl transition-all duration-300"
        :class="[
          toast.type === 'success'
            ? 'border-green-200 bg-green-50 text-green-900 dark:border-green-800 dark:bg-green-950/80 dark:text-green-100'
            : 'border-red-200 bg-red-50 text-red-900 dark:border-red-800 dark:bg-red-950/80 dark:text-red-100'
        ]"
      >
        <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full"
             :class="toast.type === 'success' ? 'bg-green-100 dark:bg-green-900' : 'bg-red-100 dark:bg-red-900'">
          <component :is="toast.type === 'success' ? CheckCircle : AlertCircle"
                     class="h-5 w-5"
                     :class="toast.type === 'success' ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'" />
        </div>
        <div class="flex flex-col">
          <p class="text-sm font-semibold leading-tight">{{ toast.type === 'success' ? 'Success' : 'Error' }}</p>
          <p class="text-xs opacity-80">{{ toast.message }}</p>
        </div>
        <button @click="toast.show = false" class="ml-4 p-1 opacity-50 hover:opacity-100">
          <X class="h-4 w-4" />
        </button>
      </div>
    </Transition>
  </main>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.toast-enter-from {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}

.toast-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.95);
}
</style>
