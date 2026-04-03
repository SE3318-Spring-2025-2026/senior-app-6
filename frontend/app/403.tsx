import Link from "next/link";
import { AlertTriangle, ArrowLeft } from "lucide-react";

/**
 * 403 Forbidden Page
 * Displayed when a user tries to access a page they don't have permission for
 */
export default function ForbiddenPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-50 via-white to-slate-100 px-4 py-10 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950">
      {/* Background gradient for dark mode */}
      <div
        className="pointer-events-none absolute inset-0 hidden dark:block"
        aria-hidden="true"
        style={{
          background:
            "radial-gradient(48rem 28rem at 50% 34%, rgba(59,130,246,0.14), rgba(15,23,42,0) 70%)",
          filter: "blur(14px)",
        }}
      />

      {/* Content */}
      <section className="relative z-10 w-full max-w-md space-y-6 text-center">
        {/* Icon */}
        <div className="flex justify-center">
          <div className="rounded-full bg-red-100 p-4 dark:bg-red-950/50">
            <AlertTriangle className="h-12 w-12 text-red-600 dark:text-red-400" />
          </div>
        </div>

        {/* Error Code */}
        <div>
          <h1 className="text-6xl font-bold text-slate-900 dark:text-white">
            403
          </h1>
          <p className="text-xl font-semibold text-slate-700 dark:text-slate-300">
            Access Forbidden
          </p>
        </div>

        {/* Message */}
        <p className="text-slate-600 dark:text-slate-400">
          You don't have permission to access this page. This might be because
          your role doesn't grant you access to this resource, or your session
          has expired.
        </p>

        {/* Action Buttons */}
        <div className="flex flex-col gap-3 pt-4 sm:flex-row sm:justify-center">
          <Link
            href="/login"
            className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-6 py-3 font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to Login
          </Link>
          <Link
            href="/"
            className="inline-flex items-center justify-center rounded-lg border border-slate-300 px-6 py-3 font-medium text-slate-700 transition hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
          >
            Back to Home
          </Link>
        </div>

        {/* Info Box */}
        <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-left dark:border-amber-900/50 dark:bg-amber-950/30">
          <p className="text-sm text-amber-900 dark:text-amber-200">
            <strong>Need help?</strong> If you believe this is an error, please
            contact the system administrator.
          </p>
        </div>
      </section>
    </div>
  );
}
