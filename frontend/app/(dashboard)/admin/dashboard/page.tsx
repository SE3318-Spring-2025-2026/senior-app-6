"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  BarChart3,
  Shield,
  Users,
  Settings,
  LogOut,
  Menu,
  X,
} from "lucide-react";
import { getAuthToken } from "@/lib/api-client";

/**
 * Admin Dashboard
 * Displays admin-only features: user management, system settings, analytics
 */
export default function AdminDashboard() {
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
            <Shield className="h-6 w-6 text-blue-600 dark:text-blue-400" />
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
              Admin Dashboard
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
              <Users className="h-5 w-5" />
              User Management
            </a>
            <a
              href="#"
              className="flex items-center gap-3 rounded-lg px-4 py-2 text-slate-900 hover:bg-slate-100 dark:text-white dark:hover:bg-slate-800"
            >
              <BarChart3 className="h-5 w-5" />
              Analytics
            </a>
            <a
              href="#"
              className="flex items-center gap-3 rounded-lg px-4 py-2 text-slate-900 hover:bg-slate-100 dark:text-white dark:hover:bg-slate-800"
            >
              <Settings className="h-5 w-5" />
              System Settings
            </a>
          </nav>
        </aside>

        {/* Main Content */}
        <main className="flex-1 p-6">
          <div className="mx-auto max-w-6xl">
            <h2 className="mb-6 text-3xl font-bold text-slate-900 dark:text-white">
              Welcome to Admin Portal
            </h2>

            {/* Dashboard Grid */}
            <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
              {/* Card 1: User Management */}
              <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
                <div className="mb-4 inline-block rounded-lg bg-blue-100 p-3 dark:bg-blue-950/50">
                  <Users className="h-6 w-6 text-blue-600 dark:text-blue-400" />
                </div>
                <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
                  User Management
                </h3>
                <p className="mb-4 text-slate-600 dark:text-slate-400">
                  Manage faculty, coordinators, and student accounts
                </p>
                <button className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600">
                  Manage Users
                </button>
              </div>

              {/* Card 2: Analytics */}
              <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
                <div className="mb-4 inline-block rounded-lg bg-green-100 p-3 dark:bg-green-950/50">
                  <BarChart3 className="h-6 w-6 text-green-600 dark:text-green-400" />
                </div>
                <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
                  Analytics
                </h3>
                <p className="mb-4 text-slate-600 dark:text-slate-400">
                  View system metrics and usage statistics
                </p>
                <button className="rounded-lg bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700 dark:bg-green-700 dark:hover:bg-green-600">
                  View Analytics
                </button>
              </div>

              {/* Card 3: Settings */}
              <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
                <div className="mb-4 inline-block rounded-lg bg-purple-100 p-3 dark:bg-purple-950/50">
                  <Settings className="h-6 w-6 text-purple-600 dark:text-purple-400" />
                </div>
                <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
                  Settings
                </h3>
                <p className="mb-4 text-slate-600 dark:text-slate-400">
                  Configure system settings and policies
                </p>
                <button className="rounded-lg bg-purple-600 px-4 py-2 text-sm font-medium text-white hover:bg-purple-700 dark:bg-purple-700 dark:hover:bg-purple-600">
                  Configure
                </button>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
