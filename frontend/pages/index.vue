<script setup lang="ts">
	import { ArrowRight, LayoutDashboard, GraduationCap, LogIn } from "lucide-vue-next";

	definePageMeta({ middleware: 'guest' })
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50 dark:from-slate-950 dark:via-slate-900 dark:to-blue-950">
    <!-- Navigation -->
    <nav class="border-b border-slate-200 bg-white/80 backdrop-blur dark:border-slate-800 dark:bg-slate-900/80">
      <div class="mx-auto flex max-w-7xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
        <div class="flex items-center gap-2">
          <LayoutDashboard class="h-6 w-6 text-blue-600 dark:text-blue-400" />
          <span class="text-xl font-bold text-slate-900 dark:text-white">
            SPM Dashboard
          </span>
        </div>
        <NuxtLink
          to="/auth/login"
          class="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
        >
          <LogIn class="h-4 w-4" aria-hidden="true" />
          Sign In
        </NuxtLink>
      </div>
    </nav>

    <!-- Hero Section -->
    <section class="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
      <div class="mx-auto max-w-2xl space-y-8 text-center">
        <h1 class="text-4xl font-bold tracking-tight text-slate-900 dark:text-white sm:text-5xl">
          Senior Project Management System
        </h1>
        <p class="text-lg text-slate-600 dark:text-slate-300">
          A comprehensive platform for managing university senior projects,
          coordinating teams, and tracking deliverables with ease.
        </p>

        <!-- CTA Buttons -->
        <div class="flex flex-col gap-3 sm:flex-row sm:justify-center">
          <NuxtLink
            to="/auth/login"
            class="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-6 py-3 text-sm font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
          >
            <LogIn class="h-4 w-4" aria-hidden="true" />
            Get Started
          </NuxtLink>
          <a
            href="#features"
            class="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-6 py-3 text-sm font-medium text-slate-900 transition hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-white dark:hover:bg-slate-700"
          >
            Learn More
            <ArrowRight class="h-4 w-4" aria-hidden="true" />
          </a>
        </div>
      </div>
    </section>

    <!-- Features Section -->
    <section id="features" class="border-t border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900">
      <div class="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
        <h2 class="mb-12 text-center text-3xl font-bold text-slate-900 dark:text-white">
          Key Features
        </h2>

        <div class="grid gap-8 md:grid-cols-2 lg:grid-cols-3">
          <!-- Feature 1 -->
          <div class="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-800">
            <div class="mb-4 inline-flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900">
              <LayoutDashboard class="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>
            <h3 class="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
              Dashboard
            </h3>
            <p class="text-sm text-slate-600 dark:text-slate-400">
              Centralized project management with real-time updates and notifications.
            </p>
          </div>

          <!-- Feature 2 -->
          <div class="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-800">
            <div class="mb-4 inline-flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-100 dark:bg-emerald-900">
              <GraduationCap class="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
            </div>
            <h3 class="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
              Team Management
            </h3>
            <p class="text-sm text-slate-600 dark:text-slate-400">
              Organize students and faculty, assign roles, and manage permissions seamlessly.
            </p>
          </div>

          <!-- Feature 3 -->
          <div class="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-800">
            <div class="mb-4 inline-flex h-10 w-10 items-center justify-center rounded-lg bg-purple-100 dark:bg-purple-900">
              <ArrowRight class="h-5 w-5 text-purple-600 dark:text-purple-400" />
            </div>
            <h3 class="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
              Workflow Management
            </h3>
            <p class="text-sm text-slate-600 dark:text-slate-400">
              Define and track project phases, milestones, and deliverables.
            </p>
          </div>
        </div>
      </div>
    </section>

    <!-- Call to Action Section -->
    <section class="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
      <div class="rounded-lg border border-slate-200 bg-blue-50 p-8 text-center dark:border-slate-800 dark:bg-blue-900/20">
        <h2 class="mb-4 text-2xl font-bold text-slate-900 dark:text-white">
          Ready to get started?
        </h2>
        <p class="mb-6 text-slate-600 dark:text-slate-300">
          Sign in with your university credentials or GitHub account.
        </p>
        <NuxtLink
          to="/auth/login"
          class="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-6 py-3 text-sm font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
        >
          <LogIn class="h-4 w-4" aria-hidden="true" />
          Sign In Now
        </NuxtLink>
      </div>
    </section>

    <!-- Footer -->
    <footer class="border-t border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
      <div class="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
        <p class="text-center text-sm text-slate-600 dark:text-slate-400">
          &copy; 2026 Senior Project Management System. All rights reserved.
        </p>
      </div>
    </footer>
  </main>
</template>
