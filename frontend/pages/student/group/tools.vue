<script setup lang="ts">
import { z } from "zod";
import { AlertCircle, FolderGit2, Loader2, ShieldCheck } from "lucide-vue-next";
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

const group = ref<GroupDetailResponse | null>(null);
const isLoading = ref(true);
const loadError = ref<string | null>(null);

const jiraSubmitting = ref(false);
const jiraError = ref<string | null>(null);
const jiraFieldErrors = ref<Record<string, string>>({});

const githubSubmitting = ref(false);
const githubError = ref<string | null>(null);
const githubFieldErrors = ref<Record<string, string>>({});

const jiraSchema = z.object({
  jiraSpaceUrl: z.string().trim().url("Enter a valid JIRA space URL."),
  jiraProjectKey: z.string().trim().min(1, "JIRA project key is required."),
  jiraApiToken: z.string().trim().min(1, "JIRA API token is required."),
});

const githubSchema = z.object({
  githubOrgName: z.string().trim().min(1, "GitHub organization name is required."),
  githubPat: z.string().trim().min(1, "GitHub personal access token is required."),
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
  jiraProjectKey: group.value?.jiraProjectKey ?? "",
  jiraApiToken: "",
}));

const githubFormValues = computed(() => ({
  githubOrgName: group.value?.githubOrgName ?? "",
  githubPat: "",
}));

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
      jiraProjectKey: fields.jiraProjectKey?.[0] ?? "",
      jiraApiToken: fields.jiraApiToken?.[0] ?? "",
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
  } catch (error: unknown) {
    jiraError.value =
      error && typeof error === "object" && "message" in error
        ? String(error.message)
        : "Failed to bind JIRA credentials.";
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
  } catch (error: unknown) {
    githubError.value =
      error && typeof error === "object" && "message" in error
        ? String(error.message)
        : "Failed to bind GitHub credentials.";
  } finally {
    githubSubmitting.value = false;
  }
}
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-6xl space-y-6">
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
        <div class="grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
          <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800">
            <h2 class="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
              <ShieldCheck class="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
              Secure binding flow
            </h2>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Credentials are validated by the backend before they are stored. Successful bindings are
              shown as locked to prevent accidental edits from the UI.
            </p>
          </div>

          <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800">
            <h2 class="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
              <FolderGit2 class="h-5 w-5 text-slate-700 dark:text-slate-300" />
              Binding checklist
            </h2>
            <ul class="mt-3 space-y-2 text-sm text-slate-600 dark:text-slate-400">
              <li>Enter the JIRA workspace URL, project key, and API token.</li>
              <li>Enter the GitHub organization name and a PAT with repository access.</li>
              <li>Watch each form switch into a locked state after a successful bind.</li>
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
              },
              {
                name: 'jiraProjectKey',
                label: 'Project Key',
                type: 'text',
                placeholder: 'SPM',
                autocomplete: 'off',
              },
              {
                name: 'jiraApiToken',
                label: 'API Token',
                type: 'password',
                placeholder: 'Enter a JIRA API token',
                autocomplete: 'new-password',
                lockedPlaceholder: 'Stored securely after validation',
              },
            ]"
            :model-value="jiraFormValues"
            :field-errors="jiraFieldErrors"
            :error-message="jiraError"
            :loading="jiraSubmitting"
            :locked="Boolean(group.jiraBound)"
            submit-label="Bind JIRA"
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
              },
              {
                name: 'githubPat',
                label: 'Personal Access Token',
                type: 'password',
                placeholder: 'Enter a GitHub PAT',
                autocomplete: 'new-password',
                helpText: 'Use a token with the required repository scope.',
                lockedPlaceholder: 'Stored securely after validation',
              },
            ]"
            :model-value="githubFormValues"
            :field-errors="githubFieldErrors"
            :error-message="githubError"
            :loading="githubSubmitting"
            :locked="Boolean(group.githubBound)"
            submit-label="Bind GitHub"
            @submit="handleGithubSubmit"
          />
        </div>
      </section>
    </div>
  </main>
</template>
