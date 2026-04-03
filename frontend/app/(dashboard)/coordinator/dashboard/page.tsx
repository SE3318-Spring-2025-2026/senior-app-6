"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  ClipboardList,
  Scale,
  Calendar,
  LogOut,
  Menu,
  X,
  ArrowRight,
} from "lucide-react";
import { getAuthToken } from "@/lib/api-client";

/**
 * Coordinator Dashboard
 * Main hub for coordinator to manage configuration, deliverables, sprints, and rubrics
 */
export default function CoordinatorDashboard() {
  const router = useRouter();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    // Verify user is authenticated
    const token = getAuthToken();
    if (!token) {
      router.push("/login");
      return;
    }
    setIsLoading(false);
  }, [router]);

  const handleLogout = () => {
    localStorage.removeItem("authToken");
    localStorage.removeItem("userInfo");
    router.push("/login");
  };

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-slate-950">
        <div className="text-slate-600 dark:text-slate-400">Loading...</div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen flex-col bg-slate-50 transition-colors dark:bg-slate-950">
      {/* Header */}
      <header className="border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
        <div className="flex items-center justify-between px-4 py-4 md:px-6">
          <div className="flex items-center gap-2">
            <ClipboardList className="h-6 w-6 text-indigo-600 dark:text-indigo-400" />
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
              Coordinator Dashboard
            </h1>
          </div>
          <button
            onClick={() => setIsMenuOpen(!isMenuOpen)}
            className="md:hidden"
          >
            {isMenuOpen ? (
              <X className="h-6 w-6 text-slate-600 dark:text-slate-400" />
            ) : (
              <Menu className="h-6 w-6 text-slate-600 dark:text-slate-400" />
            )}
          </button>
          <button
            onClick={handleLogout}
            className="hidden items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800 md:flex"
          >
            <LogOut className="h-4 w-4" />
            Logout
          </button>
        </div>
      </header>

      <div className="flex flex-1">
        {/* Sidebar */}
        <aside
          className={`border-r border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900 ${
            isMenuOpen ? "w-64" : "hidden w-64 md:block"
          }`}
        >
          <nav className="space-y-2 p-4">
            <button
              onClick={() => router.push("/coordinator/deliverables")}
              className="w-full flex items-center gap-3 rounded-lg px-4 py-2 text-left text-slate-900 hover:bg-slate-100 dark:text-white dark:hover:bg-slate-800"
            >
              <ClipboardList className="h-5 w-5" />
              Deliverables
            </button>
            <button
              onClick={() => router.push("/coordinator/evaluation-setup")}
              className="w-full flex items-center gap-3 rounded-lg px-4 py-2 text-left text-slate-900 hover:bg-slate-100 dark:text-white dark:hover:bg-slate-800"
            >
              <Scale className="h-5 w-5" />
              Evaluation Setup
            </button>
            <button
              onClick={() => router.push("/coordinator/sprint-setup")}
              className="w-full flex items-center gap-3 rounded-lg px-4 py-2 text-left text-slate-900 hover:bg-slate-100 dark:text-white dark:hover:bg-slate-800"
            >
              <Calendar className="h-5 w-5" />
              Sprint Setup
            </button>
          </nav>
        </aside>

        {/* Main Content */}
        <main className="flex-1 p-6">
          <div className="mx-auto max-w-6xl">
            <h2 className="mb-2 text-3xl font-bold text-slate-900 dark:text-white">
              Configuration Hub
            </h2>
            <p className="mb-8 text-slate-600 dark:text-slate-400">
              Manage your project setup, deliverables, grading criteria, and sprints
            </p>

            {/* Dashboard Grid */}
            <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
              {/* Card 1: Deliverables */}
              <button
                onClick={() => router.push("/coordinator/deliverables")}
                className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm transition hover:shadow-md dark:border-slate-800 dark:bg-slate-900 dark:hover:bg-slate-800"
              >
                <div className="mb-4 inline-block rounded-lg bg-indigo-100 p-3 dark:bg-indigo-950/50">
                  <ClipboardList className="h-6 w-6 text-indigo-600 dark:text-indigo-400" />
                </div>
                <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
                  Deliverables
                </h3>
                <p className="mb-4 text-slate-600 dark:text-slate-400">
                  Create and manage project deliverables (Proposal, SoW, Demo)
                </p>
                <div className="flex items-center text-sm font-medium text-indigo-600 dark:text-indigo-400">
                  Manage <ArrowRight className="ml-2 h-4 w-4" />
                </div>
              </button>

              {/* Card 2: Evaluation Setup */}
              <button
                onClick={() => router.push("/coordinator/evaluation-setup")}
                className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm transition hover:shadow-md dark:border-slate-800 dark:bg-slate-900 dark:hover:bg-slate-800"
              >
                <div className="mb-4 inline-block rounded-lg bg-purple-100 p-3 dark:bg-purple-950/50">
                  <Scale className="h-6 w-6 text-purple-600 dark:text-purple-400" />
                </div>
                <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
                  Evaluation Setup
                </h3>
                <p className="mb-4 text-slate-600 dark:text-slate-400">
                  Define rubrics and grading criteria for deliverables
                </p>
                <div className="flex items-center text-sm font-medium text-purple-600 dark:text-purple-400">
                  Configure <ArrowRight className="ml-2 h-4 w-4" />
                </div>
              </button>

              {/* Card 3: Sprint Setup */}
              <button
                onClick={() => router.push("/coordinator/sprint-setup")}
                className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm transition hover:shadow-md dark:border-slate-800 dark:bg-slate-900 dark:hover:bg-slate-800"
              >
                <div className="mb-4 inline-block rounded-lg bg-cyan-100 p-3 dark:bg-cyan-950/50">
                  <Calendar className="h-6 w-6 text-cyan-600 dark:text-cyan-400" />
                </div>
                <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
                  Sprint Setup
                </h3>
                <p className="mb-4 text-slate-600 dark:text-slate-400">
                  Create sprints and set story point targets
                </p>
                <div className="flex items-center text-sm font-medium text-cyan-600 dark:text-cyan-400">
                  Setup <ArrowRight className="ml-2 h-4 w-4" />
                </div>
              </button>
            </div>

            {/* Info Box */}
            <div className="mt-8 rounded-lg border border-blue-200 bg-blue-50 p-4 dark:border-blue-900/50 dark:bg-blue-950/30">
              <p className="text-sm text-blue-900 dark:text-blue-200">
                <strong>Tip:</strong> Start by creating deliverables, then define grading rubrics, and finally set up your project sprints. Once everything is configured, publish your configuration to activate the platform.
              </p>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
