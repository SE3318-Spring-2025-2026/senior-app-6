<script setup lang="ts">
import {
  LogIn,
  Users,
  GitBranch,
  ClipboardList,
  BarChart3,
  Shield,
  Sparkles,
  LayoutDashboard,
} from "lucide-vue-next";

definePageMeta({ middleware: "guest" });
</script>

<template>
  <main class="min-h-screen bg-gradient-to-b from-blue-50 via-white to-slate-100 dark:from-blue-950 dark:via-slate-900 dark:to-slate-950">

    <!-- Navigation -->
    <nav class="border-b border-blue-100/80 bg-blue-50/70 backdrop-blur dark:border-blue-900/40 dark:bg-blue-950/70">
      <div class="mx-auto flex max-w-6xl items-center justify-between px-4 py-4 sm:px-6">
        <div class="flex items-center gap-2">
          <LayoutDashboard class="h-5 w-5 text-blue-600 dark:text-blue-400" />
          <span class="text-lg font-bold text-slate-900 dark:text-white">SPM</span>
        </div>
        <NuxtLink
          to="/auth/login"
          class="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
        >
          <LogIn class="h-4 w-4" />
          Sign In
        </NuxtLink>
      </div>
    </nav>

    <!-- Hero -->
    <section class="mx-auto max-w-6xl px-4 py-24 sm:px-6 text-center">
      <div class="mx-auto max-w-2xl space-y-6">
        <h1 class="text-4xl font-bold tracking-tight text-slate-900 dark:text-white sm:text-5xl">
          Senior Project Management
        </h1>
        <p class="text-lg text-slate-600 dark:text-slate-400">
          End-to-end platform for university capstone projects — group formation, sprint tracking,
          deliverable review, and AI-assisted grading in one place.
        </p>
        <div class="flex flex-col gap-3 sm:flex-row sm:justify-center pt-2">
          <NuxtLink
            to="/auth/login"
            class="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-6 py-3 text-sm font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
          >
            <LogIn class="h-4 w-4" />
            Get Started
          </NuxtLink>
          <a
            href="#features"
            class="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white/70 px-6 py-3 text-sm font-medium text-slate-700 transition hover:bg-white dark:border-slate-700 dark:bg-slate-800/70 dark:text-slate-200 dark:hover:bg-slate-800"
          >
            See Features
          </a>
        </div>
      </div>
    </section>

    <!-- Features -->
    <section id="features" class="mx-auto max-w-6xl px-4 pb-20 sm:px-6">
      <h2 class="mb-10 text-center text-2xl font-bold text-slate-900 dark:text-white">
        What's inside
      </h2>

      <div class="grid gap-6 md:grid-cols-2 lg:grid-cols-3">

        <div class="rounded-2xl border border-white/80 bg-white/70 p-6 shadow-sm backdrop-blur dark:border-slate-700/60 dark:bg-slate-800/60">
          <div class="mb-4 inline-flex h-10 w-10 items-center justify-center rounded-xl bg-cyan-100 dark:bg-cyan-900/40">
            <Users class="h-5 w-5 text-cyan-600 dark:text-cyan-400" />
          </div>
          <h3 class="mb-1.5 font-semibold text-slate-900 dark:text-white">Group Formation</h3>
          <p class="text-sm text-slate-600 dark:text-slate-400">
            Students form teams, send invitations, and get matched with an advisor. Coordinators can override assignments at any time.
          </p>
        </div>

        <div class="rounded-2xl border border-white/80 bg-white/70 p-6 shadow-sm backdrop-blur dark:border-slate-700/60 dark:bg-slate-800/60">
          <div class="mb-4 inline-flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-100 dark:bg-emerald-900/40">
            <GitBranch class="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
          </div>
          <h3 class="mb-1.5 font-semibold text-slate-900 dark:text-white">Sprint Tracking</h3>
          <p class="text-sm text-slate-600 dark:text-slate-400">
            JIRA and GitHub integration to track story points, PR merges, and sprint velocity across all project groups.
          </p>
        </div>

        <div class="rounded-2xl border border-white/80 bg-white/70 p-6 shadow-sm backdrop-blur dark:border-slate-700/60 dark:bg-slate-800/60">
          <div class="mb-4 inline-flex h-10 w-10 items-center justify-center rounded-xl bg-amber-100 dark:bg-amber-900/40">
            <ClipboardList class="h-5 w-5 text-amber-600 dark:text-amber-400" />
          </div>
          <h3 class="mb-1.5 font-semibold text-slate-900 dark:text-white">Deliverable Submission</h3>
          <p class="text-sm text-slate-600 dark:text-slate-400">
            Proposal, Statement of Work, and Demonstration submissions with deadlines, review cycles, and submission history.
          </p>
        </div>

        <div class="rounded-2xl border border-white/80 bg-white/70 p-6 shadow-sm backdrop-blur dark:border-slate-700/60 dark:bg-slate-800/60">
          <div class="mb-4 inline-flex h-10 w-10 items-center justify-center rounded-xl bg-violet-100 dark:bg-violet-900/40">
            <Shield class="h-5 w-5 text-violet-600 dark:text-violet-400" />
          </div>
          <h3 class="mb-1.5 font-semibold text-slate-900 dark:text-white">Committee Review</h3>
          <p class="text-sm text-slate-600 dark:text-slate-400">
            Jury committees are assigned per deliverable. Each professor grades independently using customizable rubrics.
          </p>
        </div>

        <div class="rounded-2xl border border-white/80 bg-white/70 p-6 shadow-sm backdrop-blur dark:border-slate-700/60 dark:bg-slate-800/60">
          <div class="mb-4 inline-flex h-10 w-10 items-center justify-center rounded-xl bg-fuchsia-100 dark:bg-fuchsia-900/40">
            <BarChart3 class="h-5 w-5 text-fuchsia-600 dark:text-fuchsia-400" />
          </div>
          <h3 class="mb-1.5 font-semibold text-slate-900 dark:text-white">Rubric Grading</h3>
          <p class="text-sm text-slate-600 dark:text-slate-400">
            Weighted rubric criteria with scrum and code-review scalars. Final grades computed from individual completion ratios.
          </p>
        </div>

        <div class="rounded-2xl border border-white/80 bg-white/70 p-6 shadow-sm backdrop-blur dark:border-slate-700/60 dark:bg-slate-800/60">
          <div class="mb-4 inline-flex h-10 w-10 items-center justify-center rounded-xl bg-rose-100 dark:bg-rose-900/40">
            <Sparkles class="h-5 w-5 text-rose-600 dark:text-rose-400" />
          </div>
          <h3 class="mb-1.5 font-semibold text-slate-900 dark:text-white">AI Validation</h3>
          <p class="text-sm text-slate-600 dark:text-slate-400">
            LLM-powered PR review and diff-match analysis flags quality issues before human graders see submissions.
          </p>
        </div>

      </div>
    </section>

    <!-- CTA -->
    <section class="mx-auto max-w-6xl px-4 pb-20 sm:px-6">
      <div class="rounded-2xl border border-white/80 bg-white/60 p-10 text-center shadow-sm backdrop-blur dark:border-slate-700/60 dark:bg-slate-800/50">
        <h2 class="mb-3 text-2xl font-bold text-slate-900 dark:text-white">
          Ready to begin?
        </h2>
        <p class="mb-6 text-sm text-slate-600 dark:text-slate-400">
          Staff log in with university credentials. Students authenticate via GitHub.
        </p>
        <NuxtLink
          to="/auth/login"
          class="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-6 py-3 text-sm font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
        >
          <LogIn class="h-4 w-4" />
          Sign In
        </NuxtLink>
      </div>
    </section>

    <!-- Footer -->
    <footer class="border-t border-slate-200/60 dark:border-slate-800/60">
      <div class="mx-auto max-w-6xl px-4 py-8 sm:px-6">
        <p class="text-center text-xs text-slate-400 dark:text-slate-500">
          &copy; 2026 Senior Project Management System
        </p>
      </div>
    </footer>

  </main>
</template>
