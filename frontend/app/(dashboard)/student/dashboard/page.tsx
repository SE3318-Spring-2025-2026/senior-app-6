"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  BookOpenCheck,
  CheckCircle2,
  Clock,
  LogOut,
  Menu,
  X,
  Zap,
} from "lucide-react";
import { getAuthToken } from "@/lib/api-client";

/**
 * Student Dashboard
 * Displays student-specific features: submissions, grades, feedback
 */
export default function StudentDashboard() {
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
            <BookOpenCheck className="h-6 w-6 text-teal-600 dark:text-teal-400" />
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
              Student Dashboard
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
            <a
              href="#"
              className="flex items-center gap-3 rounded-lg px-4 py-2 text-slate-900 hover:bg-slate-100 dark:text-white dark:hover:bg-slate-800"
            >
              <BookOpenCheck className="h-5 w-5" />
              My Submissions
            </a>
            <a
              href="#"
              className="flex items-center gap-3 rounded-lg px-4 py-2 text-slate-900 hover:bg-slate-100 dark:text-white dark:hover:bg-slate-800"
            >
              <CheckCircle2 className="h-5 w-5" />
              Grades
            </a>
            <a
              href="#"
              className="flex items-center gap-3 rounded-lg px-4 py-2 text-slate-900 hover:bg-slate-100 dark:text-white dark:hover:bg-slate-800"
            >
              <Clock className="h-5 w-5" />
              Deadlines
            </a>
          </nav>
        </aside>

        {/* Main Content */}
        <main className="flex-1 p-6">
          <div className="mx-auto max-w-6xl">
            <h2 className="mb-2 text-3xl font-bold text-slate-900 dark:text-white">
              Welcome to Your Project Portal
            </h2>
            <p className="mb-8 text-slate-600 dark:text-slate-400">
              Track your submissions, view grades, and manage deadlines
            </p>

            {/* Dashboard Grid */}
            <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
              {/* Card 1: Submissions */}
              <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
                <div className="mb-4 inline-block rounded-lg bg-teal-100 p-3 dark:bg-teal-950/50">
                  <BookOpenCheck className="h-6 w-6 text-teal-600 dark:text-teal-400" />
                </div>
                <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
                  My Submissions
                </h3>
                <p className="mb-4 text-slate-600 dark:text-slate-400">
                  View all your submitted deliverables and documents
                </p>
                <button className="rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 dark:bg-teal-700 dark:hover:bg-teal-600">
                  View Submissions
                </button>
              </div>

              {/* Card 2: Grades */}
              <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
                <div className="mb-4 inline-block rounded-lg bg-green-100 p-3 dark:bg-green-950/50">
                  <CheckCircle2 className="h-6 w-6 text-green-600 dark:text-green-400" />
                </div>
                <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
                  Grades & Feedback
                </h3>
                <p className="mb-4 text-slate-600 dark:text-slate-400">
                  Check your grades and instructor feedback
                </p>
                <button className="rounded-lg bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700 dark:bg-green-700 dark:hover:bg-green-600">
                  View Grades
                </button>
              </div>

              {/* Card 3: Deadlines */}
              <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
                <div className="mb-4 inline-block rounded-lg bg-orange-100 p-3 dark:bg-orange-950/50">
                  <Clock className="h-6 w-6 text-orange-600 dark:text-orange-400" />
                </div>
                <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
                  Important Deadlines
                </h3>
                <p className="mb-4 text-slate-600 dark:text-slate-400">
                  Keep track of upcoming submission deadlines
                </p>
                <button className="rounded-lg bg-orange-600 px-4 py-2 text-sm font-medium text-white hover:bg-orange-700 dark:bg-orange-700 dark:hover:bg-orange-600">
                  View Deadlines
                </button>
              </div>
            </div>

            {/* Quick Stats */}
            <div className="mt-8 grid gap-4 md:grid-cols-3">
              <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-900">
                <p className="text-sm text-slate-600 dark:text-slate-400">
                  Active Deliverables
                </p>
                <p className="mt-1 text-2xl font-bold text-slate-900 dark:text-white">
                  3
                </p>
              </div>
              <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-900">
                <p className="text-sm text-slate-600 dark:text-slate-400">
                  Submitted
                </p>
                <p className="mt-1 text-2xl font-bold text-slate-900 dark:text-white">
                  2
                </p>
              </div>
              <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-900">
                <p className="text-sm text-slate-600 dark:text-slate-400">
                  Current GPA
                </p>
                <p className="mt-1 text-2xl font-bold text-slate-900 dark:text-white">
                  3.8
                </p>
              </div>
            </div>

            {/* Alert */}
            <div className="mt-8 flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 dark:border-amber-900/50 dark:bg-amber-950/30">
              <Zap className="mt-0.5 h-5 w-5 text-amber-600 dark:text-amber-400" />
              <p className="text-sm text-amber-900 dark:text-amber-200">
                <strong>Reminder:</strong> Proposal submission deadline is in 5 days. Make sure to submit before the deadline.
              </p>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
