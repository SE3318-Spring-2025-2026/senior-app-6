"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Loader2 } from "lucide-react";

/**
 * GitHub OAuth Callback Handler
 * This page is called by GitHub OAuth redirect after user authorizes
 * Handles token reception and session setup
 *
 * URL Pattern: http://localhost:3000/auth/github/callback?token=JWT&state=STATE
 * Backend should redirect here after successful GitHub authentication
 */
function GitHubCallbackContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [status, setStatus] = useState<"loading" | "error" | "success">(
    "loading"
  );
  const [message, setMessage] = useState("");

  useEffect(() => {
    const handleCallback = async () => {
      try {
        // Extract JWT token from URL query parameters
        const token = searchParams.get("token");
        const state = searchParams.get("state");
        const error = searchParams.get("error");

        // Check for OAuth error
        if (error) {
          setStatus("error");
          setMessage(`GitHub authorization failed: ${error}`);
          return;
        }

        // Check if token is present
        if (!token) {
          setStatus("error");
          setMessage("No authentication token received. Please try again.");
          return;
        }

        // Store JWT token in localStorage
        localStorage.setItem("authToken", token);

        // Parse and store user info if available
        // You may want to decode the JWT and extract user role
        try {
          // Simple JWT decode (not secure, should validate on backend)
          const parts = token.split(".");
          if (parts.length === 3) {
            const payload = JSON.parse(atob(parts[1]));
            localStorage.setItem("userInfo", JSON.stringify(payload));
          }
        } catch (e) {
          console.warn("Could not decode JWT payload");
        }

        // Success - redirect to student dashboard
        setStatus("success");
        setMessage("Authentication successful. Redirecting...");

        // Redirect after brief delay
        setTimeout(() => {
          router.push("/student/dashboard");
        }, 1500);
      } catch (err) {
        setStatus("error");
        setMessage("An unexpected error occurred. Please try again.");
      }
    };

    handleCallback();
  }, [searchParams, router]);

  return (
    <main className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-50 via-white to-slate-100 px-4 py-10 dark:from-slate-950 dark:via-slate-900 dark:to-slate-950">
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
      <section className="relative z-10 w-full max-w-md space-y-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-lg dark:border-slate-700 dark:bg-slate-900 dark:shadow-xl md:p-8">
        {/* Loading State */}
        {status === "loading" && (
          <div className="flex flex-col items-center justify-center gap-4 text-center">
            <div className="inline-flex h-12 w-12 items-center justify-center rounded-full bg-blue-100 dark:bg-blue-950/50">
              <Loader2 className="h-6 w-6 animate-spin text-blue-600 dark:text-blue-400" />
            </div>
            <h1 className="text-2xl font-semibold text-slate-900 dark:text-white">
              Authenticating...
            </h1>
            <p className="text-slate-600 dark:text-slate-400">
              Please wait while we complete your GitHub login
            </p>
          </div>
        )}

        {/* Success State */}
        {status === "success" && (
          <div className="flex flex-col items-center justify-center gap-4 text-center">
            <div className="inline-flex h-12 w-12 items-center justify-center rounded-full bg-green-100 dark:bg-green-950/50">
              <svg
                className="h-6 w-6 text-green-600 dark:text-green-400"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M5 13l4 4L19 7"
                />
              </svg>
            </div>
            <h1 className="text-2xl font-semibold text-slate-900 dark:text-white">
              {message}
            </h1>
            <p className="text-slate-600 dark:text-slate-400">
              You will be redirected to your dashboard shortly.
            </p>
          </div>
        )}

        {/* Error State */}
        {status === "error" && (
          <div className="flex flex-col items-center justify-center gap-4 text-center">
            <div className="inline-flex h-12 w-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950/50">
              <svg
                className="h-6 w-6 text-red-600 dark:text-red-400"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M6 18L18 6M6 6l12 12"
                />
              </svg>
            </div>
            <h1 className="text-2xl font-semibold text-red-900 dark:text-red-100">
              Authentication Failed
            </h1>
            <p className="text-red-700 dark:text-red-300">{message}</p>

            <div className="pt-2">
              <a
                href="/login?tab=students"
                className="inline-flex items-center justify-center rounded-lg bg-red-600 px-6 py-2 text-sm font-medium text-white transition hover:bg-red-700 dark:bg-red-700 dark:hover:bg-red-600"
              >
                Back to Login
              </a>
            </div>
          </div>
        )}
      </section>
    </main>
  );
}

export default function GitHubCallbackPage() {
  return (
    <Suspense
      fallback={
        <main className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-50 via-white to-slate-100 px-4 py-10 dark:from-slate-950 dark:via-slate-900 dark:to-slate-950">
          <section className="relative z-10 w-full max-w-md space-y-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-lg dark:border-slate-700 dark:bg-slate-900 dark:shadow-xl md:p-8">
            <div className="flex flex-col items-center justify-center gap-4 text-center">
              <div className="inline-flex h-12 w-12 items-center justify-center rounded-full bg-blue-100 dark:bg-blue-950/50">
                <Loader2 className="h-6 w-6 animate-spin text-blue-600 dark:text-blue-400" />
              </div>
              <h1 className="text-2xl font-semibold text-slate-900 dark:text-white">
                Loading...
              </h1>
            </div>
          </section>
        </main>
      }
    >
      <GitHubCallbackContent />
    </Suspense>
  );
}
