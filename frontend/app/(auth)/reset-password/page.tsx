"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo, useState } from "react";
import { z } from "zod";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { AlertCircle, KeyRound, Loader2, Lock, ShieldCheck } from "lucide-react";
import {
  validateResetPasswordToken,
  setPasswordWithToken,
} from "@/lib/api-client";

const resetPasswordSchema = z
  .object({
    newPassword: z
      .string()
      .min(8, "Password must be at least 8 characters long."),
    confirmPassword: z
      .string()
      .min(8, "Password must be at least 8 characters long."),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    message: "Passwords do not match.",
    path: ["confirmPassword"],
  });

type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>;

type TokenValidationState = "loading" | "invalid" | "valid";

/**
 * Reset Password Page Content
 * Validates token and allows user to set new password
 */
function ResetPasswordPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = useMemo(() => searchParams.get("token"), [searchParams]);

  const [tokenValidationState, setTokenValidationState] =
    useState<TokenValidationState>("loading");
  const [successMessage, setSuccessMessage] = useState("");

  const form = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: {
      newPassword: "",
      confirmPassword: "",
    },
    mode: "onSubmit",
  });

  /**
   * Validate token on component mount
   * Calls GET /api/auth/reset-password?token=...
   */
  useEffect(() => {
    const validateToken = async () => {
      if (!token) {
        setTokenValidationState("invalid");
        return;
      }

      try {
        await validateResetPasswordToken(token);
        setTokenValidationState("valid");
      } catch (err) {
        setTokenValidationState("invalid");
      }
    };

    validateToken();
  }, [token]);

  /**
   * Handle password reset form submission
   * Calls POST /api/auth/reset-password with token and newPassword
   */
  const handleSubmit = form.handleSubmit(async (values) => {
    if (!token) {
      return;
    }

    try {
      await setPasswordWithToken(token, values.newPassword);
      setSuccessMessage("Password set successfully. Redirecting...");

      window.setTimeout(() => {
        router.push("/login");
      }, 1400);
    } catch (err) {
      const errorMsg =
        err && typeof err === "object" && "message" in err
          ? String(err.message)
          : "Failed to set password";
      form.setError("root", { message: errorMsg });
    }
  });

  return (
    <main className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-50 via-white to-slate-100 px-4 py-10">
      <section className="w-full max-w-md rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-xl backdrop-blur md:p-8">
        {tokenValidationState === "loading" && (
          <div className="flex flex-col items-center justify-center gap-3 py-8 text-center">
            <Loader2 className="h-8 w-8 animate-spin text-slate-700" aria-hidden="true" />
            <p className="text-sm text-slate-600">Validating your setup link...</p>
          </div>
        )}

        {tokenValidationState === "invalid" && (
          <div className="space-y-4 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-red-600">
              <AlertCircle className="h-6 w-6" aria-hidden="true" />
            </div>
            <h1 className="text-xl font-semibold tracking-tight text-slate-900">
              Password Setup Unavailable
            </h1>
            <p className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              This setup link is invalid or has expired. Please contact the Administrator for a new invitation.
            </p>
            <Link
              href="/login"
              className="inline-flex w-full items-center justify-center rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-800"
            >
              Return to Login
            </Link>
          </div>
        )}

        {tokenValidationState === "valid" && (
          <div>
            <header className="mb-6 space-y-2 text-center">
              <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-700">
                <ShieldCheck className="h-6 w-6" aria-hidden="true" />
              </div>
              <h1 className="text-2xl font-semibold tracking-tight text-slate-900">
                Welcome! Please set your permanent password
              </h1>
              <p className="text-sm text-slate-600">
                Use a strong password you have not used before.
              </p>
            </header>

            <form onSubmit={handleSubmit} className="space-y-4" noValidate>
              <label className="block space-y-1.5">
                <span className="text-sm font-medium text-slate-700">New Password</span>
                <div className="relative">
                  <Lock
                    className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                    aria-hidden="true"
                  />
                  <input
                    type="password"
                    autoComplete="new-password"
                    placeholder="Enter your new password"
                    className="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-3 text-sm outline-none ring-0 transition focus:border-slate-500"
                    {...form.register("newPassword")}
                  />
                </div>
                {form.formState.errors.newPassword && (
                  <p className="text-xs text-red-600">
                    {form.formState.errors.newPassword.message}
                  </p>
                )}
              </label>

              <label className="block space-y-1.5">
                <span className="text-sm font-medium text-slate-700">Confirm Password</span>
                <div className="relative">
                  <KeyRound
                    className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                    aria-hidden="true"
                  />
                  <input
                    type="password"
                    autoComplete="new-password"
                    placeholder="Confirm your new password"
                    className="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-3 text-sm outline-none ring-0 transition focus:border-slate-500"
                    {...form.register("confirmPassword")}
                  />
                </div>
                {form.formState.errors.confirmPassword && (
                  <p className="text-xs text-red-600">
                    {form.formState.errors.confirmPassword.message}
                  </p>
                )}
              </label>

              {successMessage && (
                <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                  {successMessage}
                </p>
              )}

              <button
                type="submit"
                disabled={form.formState.isSubmitting}
                className="inline-flex w-full items-center justify-center rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {form.formState.isSubmitting ? "Saving password..." : "Set Password"}
              </button>
            </form>
          </div>
        )}
      </section>
    </main>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense
      fallback={
        <main className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-50 via-white to-slate-100 px-4 py-10">
          <p className="text-sm text-slate-600">Loading...</p>
        </main>
      }
    >
      <ResetPasswordPageContent />
    </Suspense>
  );
}