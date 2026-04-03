import Link from "next/link";
import { ArrowRight, LayoutDashboard, GraduationCap, LogIn } from "lucide-react";

export default function Home() {
  return (
    <main className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50 dark:from-slate-950 dark:via-slate-900 dark:to-blue-950">
      {/* Navigation */}
      <nav className="border-b border-slate-200 bg-white/80 backdrop-blur dark:border-slate-800 dark:bg-slate-900/80">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
          <div className="flex items-center gap-2">
            <LayoutDashboard className="h-6 w-6 text-blue-600 dark:text-blue-400" />
            <span className="text-xl font-bold text-slate-900 dark:text-white">
              SPM Dashboard
            </span>
          </div>
          <Link
            href="/auth/login"
            className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
          >
            <LogIn className="h-4 w-4" aria-hidden="true" />
            Sign In
          </Link>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-2xl space-y-8 text-center">
          <h1 className="text-4xl font-bold tracking-tight text-slate-900 dark:text-white sm:text-5xl">
            Senior Project Management System
          </h1>
          <p className="text-lg text-slate-600 dark:text-slate-300">
            A comprehensive platform for managing university senior projects,
            coordinating teams, and tracking deliverables with ease.
          </p>

          {/* CTA Buttons */}
          <div className="flex flex-col gap-3 sm:flex-row sm:justify-center">
            <Link
              href="/auth/login"
              className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-6 py-3 text-sm font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
            >
              <LogIn className="h-4 w-4" aria-hidden="true" />
              Get Started
            </Link>
            <a
              href="#features"
              className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-6 py-3 text-sm font-medium text-slate-900 transition hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-white dark:hover:bg-slate-700"
            >
              Learn More
              <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </a>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section id="features" className="border-t border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900">
        <div className="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
          <h2 className="mb-12 text-center text-3xl font-bold text-slate-900 dark:text-white">
            Key Features
          </h2>

          <div className="grid gap-8 md:grid-cols-2 lg:grid-cols-3">
            {/* Feature 1 */}
            <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-800">
              <div className="mb-4 inline-flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900">
                <LayoutDashboard className="h-5 w-5 text-blue-600 dark:text-blue-400" />
              </div>
              <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
                Dashboard
              </h3>
              <p className="text-sm text-slate-600 dark:text-slate-400">
                Centralized project management with real-time updates and
                notifications.
              </p>
            </div>

            {/* Feature 2 */}
            <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-800">
              <div className="mb-4 inline-flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-100 dark:bg-emerald-900">
                <GraduationCap className="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
              </div>
              <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
                Team Management
              </h3>
              <p className="text-sm text-slate-600 dark:text-slate-400">
                Organize students and faculty, assign roles, and manage
                permissions seamlessly.
              </p>
            </div>

            {/* Feature 3 */}
            <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-800">
              <div className="mb-4 inline-flex h-10 w-10 items-center justify-center rounded-lg bg-purple-100 dark:bg-purple-900">
                <ArrowRight className="h-5 w-5 text-purple-600 dark:text-purple-400" />
              </div>
              <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
                Workflow Management
              </h3>
              <p className="text-sm text-slate-600 dark:text-slate-400">
                Define and track project phases, milestones, and deliverables.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Call to Action Section */}
      <section className="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
        <div className="rounded-lg border border-slate-200 bg-blue-50 p-8 text-center dark:border-slate-800 dark:bg-blue-900/20">
          <h2 className="mb-4 text-2xl font-bold text-slate-900 dark:text-white">
            Ready to get started?
          </h2>
          <p className="mb-6 text-slate-600 dark:text-slate-300">
            Sign in with your university credentials or GitHub account.
          </p>
          <Link
            href="/auth/login"
            className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-6 py-3 text-sm font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
          >
            <LogIn className="h-4 w-4" aria-hidden="true" />
            Sign In Now
          </Link>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
        <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
          <p className="text-center text-sm text-slate-600 dark:text-slate-400">
            © 2026 Senior Project Management System. All rights reserved.
          </p>
        </div>
      </footer>
    </main>
  );
}
