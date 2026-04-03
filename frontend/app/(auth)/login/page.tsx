"use client";

import { useState } from "react";
import { Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { z } from "zod";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  AlertCircle,
  GraduationCap,
  LayoutDashboard,
  LogIn,
  Mail,
  Shield,
  Lock,
} from "lucide-react";
import { loginFaculty, loginStudent } from "@/lib/api-client";

// Form validation schemas
const facultySchema = z.object({
  email: z.string().email("Please enter a valid email address."),
  password: z.string().min(1, "Password is required."),
});

const studentSchema = z.object({
  studentId: z
    .string()
    .trim()
    .min(1, "Student ID is required.")
    .regex(
      /^[A-Za-z0-9_-]+$/,
      "Student ID can only include letters, numbers, '-' and '_'."
    ),
});

type FacultyFormValues = z.infer<typeof facultySchema>;
type StudentFormValues = z.infer<typeof studentSchema>;
type AuthTab = "students" | "faculty";

/**
 * Helper function to map role to dashboard URL
 */
function getRoleDashboardUrl(role: string): string {
  const roleMap: Record<string, string> = {
    ADMIN: "/admin/dashboard",
    COORDINATOR: "/coordinator/dashboard",
    PROFESSOR: "/professor/dashboard",
    STUDENT: "/student/dashboard",
  };
  return roleMap[role] || "/403";
}

/**
 * Main login page content component
 * Handles both faculty/admin and student authentication flows
 */
function LoginPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [activeTab, setActiveTab] = useState<AuthTab>("students");
  const [facultyErrorMessage, setFacultyErrorMessage] = useState("");
  const [studentErrorMessage, setStudentErrorMessage] = useState("");

  const urlError = searchParams.get("error");
  const showStudentNotRegisteredAlert = urlError === "StudentNotRegistered";

  // Faculty form setup
  const facultyForm = useForm<FacultyFormValues>({
    resolver: zodResolver(facultySchema),
    defaultValues: { email: "", password: "" },
    mode: "onSubmit",
  });

  // Student form setup
  const studentForm = useForm<StudentFormValues>({
    resolver: zodResolver(studentSchema),
    defaultValues: { studentId: "" },
    mode: "onChange",
  });

  const studentId = useWatch({
    control: studentForm.control,
    name: "studentId",
  });
  const isStudentButtonDisabled = !studentId?.trim();

  /**
   * Handle faculty/admin login via email and password
   * Communicates with backend API for authentication
   * Stores JWT token and redirects to role-specific dashboard
   */
  const handleFacultySubmit = facultyForm.handleSubmit(async (values) => {
    setFacultyErrorMessage("");

    try {
      const response = await loginFaculty(values.email, values.password);
      // Store token in localStorage
      localStorage.setItem("authToken", response.token);
      // Store user info including role for role-based routing
      localStorage.setItem("userInfo", JSON.stringify(response.userInfo));
      
      // Redirect to role-specific dashboard
      const dashboardUrl = getRoleDashboardUrl(response.userInfo.role);
      router.push(dashboardUrl);
    } catch (error: unknown) {
      const errorObj = error as any;
      let errorMsg = "Authentication failed. Please try again.";
      
      // Handle specific error cases
      if (errorObj.status === 403) {
        errorMsg = "Your student ID is not registered in the system.";
        router.push("?error=StudentNotRegistered");
      } else if (errorObj.status === 401) {
        errorMsg = "Invalid email or password.";
      } else if (errorObj.message) {
        errorMsg = String(errorObj.message);
      }
      
      setFacultyErrorMessage(errorMsg);
    }
  });

  /**
   * Handle student GitHub login
   * Initiates the OAuth flow with GitHub
   * For student login, role is determined post-GitHub authentication
   */
  const handleStudentSubmit = studentForm.handleSubmit(async (values) => {
    setStudentErrorMessage("");

    try {
      const response = await loginStudent(values.studentId);
      if (response.redirectUrl) {
        // GitHub OAuth redirect
        window.location.href = response.redirectUrl;
      } else {
        // If no redirect URL, assume student login returns userInfo
        // Store token and redirect to student dashboard
        router.push("/student/dashboard");
      }
    } catch (error: unknown) {
      const errorObj = error as any;
      let errorMsg = "GitHub login failed. Please try again.";
      
      // Handle 403 Forbidden - student not registered
      if (errorObj.status === 403) {
        errorMsg = "Your student ID is not registered in the system.";
        setStudentErrorMessage(errorMsg);
      } else if (errorObj.message) {
        errorMsg = String(errorObj.message);
        setStudentErrorMessage(errorMsg);
      }
    }
  });

  return (
    <main className="relative flex min-h-screen items-center justify-center overflow-hidden bg-slate-50 px-4 py-10 transition-colors dark:bg-slate-950">
      {/* Background gradient */}
      <div
        className="pointer-events-none absolute inset-0 hidden dark:block"
        aria-hidden="true"
        style={{
          background:
            "radial-gradient(48rem 28rem at 50% 34%, rgba(59,130,246,0.14), rgba(15,23,42,0) 70%)",
          filter: "blur(14px)",
        }}
      />

      {/* Login card */}
      <section className="relative z-10 w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-lg transition-colors dark:border-slate-700 dark:bg-slate-900 dark:shadow-xl md:p-8">
        {/* Header */}
        <header className="mb-6 space-y-3 text-center">
          <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-xl border border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-400/30 dark:bg-blue-950/50 dark:text-blue-300">
            <LayoutDashboard className="h-5 w-5" aria-hidden="true" />
          </div>
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white">
            University Project Dashboard
          </h1>
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Sign in to access your project workspace.
          </p>
        </header>

        {/* Student not registered alert */}
        {showStudentNotRegisteredAlert && (
          <div className="mb-4 flex items-start gap-2 rounded-lg border border-red-300 bg-red-50 p-3 text-sm text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <p>Your student ID is not registered in the system.</p>
          </div>
        )}

        {/* Auth tabs */}
        <div className="mb-6 grid grid-cols-2 gap-2 rounded-lg border border-slate-200 bg-slate-100 p-1 dark:border-slate-700 dark:bg-slate-800">
          <button
            type="button"
            onClick={() => {
              setActiveTab("students");
              setFacultyErrorMessage("");
            }}
            className={`rounded-md px-3 py-2 text-sm font-medium transition ${
              activeTab === "students"
                ? "bg-white text-slate-900 shadow-sm dark:bg-slate-700 dark:text-white"
                : "text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-200"
            }`}
            aria-pressed={activeTab === "students"}
          >
            <GraduationCap className="mr-2 inline h-4 w-4" aria-hidden="true" />
            Students
          </button>
          <button
            type="button"
            onClick={() => {
              setActiveTab("faculty");
              setStudentErrorMessage("");
            }}
            className={`rounded-md px-3 py-2 text-sm font-medium transition ${
              activeTab === "faculty"
                ? "bg-white text-slate-900 shadow-sm dark:bg-slate-700 dark:text-white"
                : "text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-200"
            }`}
            aria-pressed={activeTab === "faculty"}
          >
            <Shield className="mr-2 inline h-4 w-4" aria-hidden="true" />
            Faculty/Admin
          </button>
        </div>

        {/* Faculty login form */}
        {activeTab === "faculty" ? (
          <form onSubmit={handleFacultySubmit} className="space-y-4" noValidate>
            <label className="block space-y-1.5">
              <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                Email Address
              </span>
              <div className="relative">
                <Mail
                  className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400 dark:text-slate-500"
                  aria-hidden="true"
                />
                <input
                  type="email"
                  placeholder="you@university.edu"
                  autoComplete="email"
                  className="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 placeholder-slate-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-600 dark:bg-slate-800 dark:text-white dark:placeholder-slate-500 dark:focus:border-blue-400 dark:focus:ring-blue-400/20"
                  {...facultyForm.register("email")}
                />
              </div>
              {facultyForm.formState.errors.email && (
                <p className="text-xs text-red-600 dark:text-red-400">
                  {facultyForm.formState.errors.email.message}
                </p>
              )}
            </label>

            <label className="block space-y-1.5">
              <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                Password
              </span>
              <div className="relative">
                <Lock
                  className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400 dark:text-slate-500"
                  aria-hidden="true"
                />
                <input
                  type="password"
                  placeholder="Enter your password"
                  autoComplete="current-password"
                  className="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 placeholder-slate-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-600 dark:bg-slate-800 dark:text-white dark:placeholder-slate-500 dark:focus:border-blue-400 dark:focus:ring-blue-400/20"
                  {...facultyForm.register("password")}
                />
              </div>
              {facultyForm.formState.errors.password && (
                <p className="text-xs text-red-600 dark:text-red-400">
                  {facultyForm.formState.errors.password.message}
                </p>
              )}
            </label>

            {facultyErrorMessage && (
              <div className="rounded-lg border border-red-300 bg-red-50 p-3 text-sm text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300">
                {facultyErrorMessage}
              </div>
            )}

            <button
              type="submit"
              disabled={facultyForm.formState.isSubmitting}
              className="inline-flex w-full items-center justify-center rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600 dark:focus-visible:ring-blue-500/40"
            >
              {facultyForm.formState.isSubmitting ? "Signing in..." : "Sign In"}
            </button>
          </form>
        ) : (
          // Student GitHub login form
          <form onSubmit={handleStudentSubmit} className="space-y-4" noValidate>
            <label className="block space-y-1.5">
              <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                Student ID
              </span>
              <input
                type="text"
                placeholder="e.g., 202400123"
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder-slate-400 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-600 dark:bg-slate-800 dark:text-white dark:placeholder-slate-500 dark:focus:border-blue-400 dark:focus:ring-blue-400/20"
                {...studentForm.register("studentId")}
              />
              {studentForm.formState.errors.studentId && (
                <p className="text-xs text-red-600 dark:text-red-400">
                  {studentForm.formState.errors.studentId.message}
                </p>
              )}
            </label>

            {studentErrorMessage && (
              <div className="rounded-lg border border-red-300 bg-red-50 p-3 text-sm text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300">
                {studentErrorMessage}
              </div>
            )}

            <button
              type="submit"
              disabled={isStudentButtonDisabled || studentForm.formState.isSubmitting}
              className="inline-flex w-full items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-900 transition hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/35 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-800 dark:text-white dark:hover:bg-slate-700 dark:focus-visible:ring-blue-500/35"
            >
              <LogIn className="h-4 w-4" aria-hidden="true" />
              Sign in with GitHub
            </button>
          </form>
        )}
      </section>
    </main>
  );
}

/**
 * Login page with Suspense boundary for search params
 */
export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <main className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-slate-950">
          <div className="text-center">
            <div className="inline-flex h-10 w-10 items-center justify-center rounded-full bg-blue-100 dark:bg-blue-900">
              <div className="h-6 w-6 animate-spin rounded-full border-2 border-blue-300 border-t-blue-600 dark:border-t-blue-400" />
            </div>
            <p className="mt-4 text-sm text-slate-600 dark:text-slate-400">
              Loading...
            </p>
          </div>
        </main>
      }
    >
      <LoginPageContent />
    </Suspense>
  );
}
        <header className="mb-6 space-y-3 text-center">
          <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-xl border border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-300/30 dark:bg-blue-400/10 dark:text-blue-300">
            <LayoutDashboard className="h-5 w-5" aria-hidden="true" />
          </div>
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900 dark:text-[aliceblue]">
            University Project Dashboard
          </h1>
          <p className="text-sm text-slate-600 dark:text-[rgba(240,248,255,0.65)]">
            Sign in to access your project workspace.
          </p>
        </header>

        {showStudentNotRegisteredAlert && (
          <div className="mb-4 flex items-start gap-2 rounded-lg border border-red-400 bg-red-100 p-3 text-sm text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <p>Your student ID is not registered in the system.</p>
          </div>
        )}

        <div className="mb-6 grid grid-cols-2 rounded-xl border border-slate-200 bg-slate-100 p-1 text-sm dark:border-white/10 dark:bg-white/[0.04]">
          <button
            type="button"
            onClick={() => {
              setActiveTab("students");
              setFacultyErrorMessage("");
            }}
            className={`inline-flex items-center justify-center gap-2 rounded-lg px-3 py-2 font-medium transition ${
              activeTab === "students"
                ? "bg-white text-slate-900 shadow-sm dark:bg-white/[0.12] dark:text-[aliceblue] dark:shadow-[inset_0_1px_0_rgba(255,255,255,0.15)]"
                : "text-slate-600 hover:text-slate-900 dark:text-[rgba(240,248,255,0.7)] dark:hover:text-[aliceblue]"
            }`}
            aria-pressed={activeTab === "students"}
          >
            <GraduationCap className="h-4 w-4" aria-hidden="true" />
            Students
          </button>
          <button
            type="button"
            onClick={() => {
              setActiveTab("faculty");
              setStudentActionMessage("");
            }}
            className={`inline-flex items-center justify-center gap-2 rounded-lg px-3 py-2 font-medium transition ${
              activeTab === "faculty"
                ? "bg-white text-slate-900 shadow-sm dark:bg-white/[0.12] dark:text-[aliceblue] dark:shadow-[inset_0_1px_0_rgba(255,255,255,0.15)]"
                : "text-slate-600 hover:text-slate-900 dark:text-[rgba(240,248,255,0.7)] dark:hover:text-[aliceblue]"
            }`}
            aria-pressed={activeTab === "faculty"}
          >
            <Shield className="h-4 w-4" aria-hidden="true" />
            Faculty/Admin
          </button>
        </div>

        {activeTab === "faculty" ? (
          <form onSubmit={handleFacultySubmit} className="space-y-4" noValidate>
            <label className="block space-y-1.5">
              <span className="text-sm font-medium text-slate-700 dark:text-[rgba(240,248,255,0.85)]">Email</span>
              <div className="relative">
                <Mail
                  className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400 dark:text-[rgba(240,248,255,0.45)]"
                  aria-hidden="true"
                />
                <input
                  type="email"
                  placeholder="you@university.edu"
                  autoComplete="email"
                  className="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-600 focus:ring-2 focus:ring-blue-600/25 dark:border-white/10 dark:bg-black/50 dark:text-[aliceblue] dark:placeholder:text-[rgba(240,248,255,0.35)] dark:focus:border-blue-500 dark:focus:ring-blue-500/30"
                  {...facultyForm.register("email")}
                />
              </div>
              {facultyForm.formState.errors.email && (
                <p className="text-xs text-red-700 dark:text-red-300">
                  {facultyForm.formState.errors.email.message}
                </p>
              )}
            </label>

            <label className="block space-y-1.5">
              <span className="text-sm font-medium text-slate-700 dark:text-[rgba(240,248,255,0.85)]">Password</span>
              <div className="relative">
                <UserSquare2
                  className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400 dark:text-[rgba(240,248,255,0.45)]"
                  aria-hidden="true"
                />
                <input
                  type="password"
                  placeholder="Enter your password"
                  autoComplete="current-password"
                  className="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-600 focus:ring-2 focus:ring-blue-600/25 dark:border-white/10 dark:bg-black/50 dark:text-[aliceblue] dark:placeholder:text-[rgba(240,248,255,0.35)] dark:focus:border-blue-500 dark:focus:ring-blue-500/30"
                  {...facultyForm.register("password")}
                />
              </div>
              {facultyForm.formState.errors.password && (
                <p className="text-xs text-red-700 dark:text-red-300">
                  {facultyForm.formState.errors.password.message}
                </p>
              )}
            </label>

            {facultyErrorMessage && (
              <p className="rounded-lg border border-red-400 bg-red-100 px-3 py-2 text-sm text-red-900 dark:border-red-800 dark:bg-red-950/50 dark:text-red-300">
                {facultyErrorMessage}
              </p>
            )}

            <button
              type="submit"
              disabled={facultyForm.formState.isSubmitting}
              className="inline-flex w-full items-center justify-center rounded-lg border border-slate-900 bg-slate-900 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600/40 disabled:cursor-not-allowed disabled:opacity-60 dark:border-white/10 dark:bg-white/[0.06] dark:text-[aliceblue] dark:shadow-none dark:hover:bg-white/[0.1] dark:focus-visible:ring-blue-500/40"
            >
              {facultyForm.formState.isSubmitting ? "Signing in..." : "Sign In"}
            </button>
          </form>
        ) : (
          <form onSubmit={handleStudentSubmit} className="space-y-4" noValidate>
            <label className="block space-y-1.5">
              <span className="text-sm font-medium text-slate-700 dark:text-[rgba(240,248,255,0.85)]">Student ID</span>
              <input
                type="text"
                placeholder="e.g. 202400123"
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-600 focus:ring-2 focus:ring-blue-600/25 dark:border-white/10 dark:bg-black/50 dark:text-[aliceblue] dark:placeholder:text-[rgba(240,248,255,0.35)] dark:focus:border-blue-500 dark:focus:ring-blue-500/30"
                {...studentForm.register("studentId")}
              />
              {studentForm.formState.errors.studentId && (
                <p className="text-xs text-red-700 dark:text-red-300">
                  {studentForm.formState.errors.studentId.message}
                </p>
              )}
            </label>

            <button
              type="submit"
              disabled={isStudentButtonDisabled || studentForm.formState.isSubmitting}
              className="inline-flex w-full items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-900 shadow-sm transition hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600/35 disabled:cursor-not-allowed disabled:opacity-60 dark:border-white/10 dark:bg-white/[0.06] dark:text-[aliceblue] dark:shadow-none dark:hover:bg-white/[0.1] dark:focus-visible:ring-blue-500/35"
            >
              <LogIn className="h-4 w-4" aria-hidden="true" />
              Sign in with GitHub
            </button>

            {studentActionMessage && (
              <p className="rounded-lg border border-emerald-300 bg-emerald-100 px-3 py-2 text-sm text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-300">
                {studentActionMessage}
              </p>
            )}
          </form>
        )}
      </section>
    </main>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <main className="relative flex min-h-screen items-center justify-center overflow-hidden bg-slate-50 px-4 py-10 text-slate-900 transition-colors dark:bg-[#151515] dark:text-[aliceblue]">
          <p className="text-sm text-slate-600 dark:text-[rgba(240,248,255,0.65)]">Loading...</p>
        </main>
      }
    >
      <LoginPageContent />
    </Suspense>
  );
}
