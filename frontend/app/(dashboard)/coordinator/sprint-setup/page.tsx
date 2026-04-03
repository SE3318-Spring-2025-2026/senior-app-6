"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  AlertCircle,
  CheckCircle2,
  Calendar,
  Loader,
  Play,
  TrendingUp,
} from "lucide-react";
import {
  createSprint,
  updateSprintTarget,
  getAuthToken,
  type Sprint,
} from "@/lib/api-client";

// ==================== Form Schema ====================

/**
 * Schema for creating a new sprint
 * Validates: start date exists, end date exists, end date is after start date
 */
const sprintCreateSchema = z
  .object({
    startDate: z.string().min(1, "Start date is required."),
    endDate: z.string().min(1, "End date is required."),
  })
  .refine(
    (values) => new Date(values.endDate) > new Date(values.startDate),
    {
      path: ["endDate"],
      message: "End date must be after start date.",
    }
  );

/**
 * Schema for updating sprint story point target
 * Validates: target is a positive number
 */
const sprintTargetSchema = z.object({
  storyPointTarget: z
    .number({ error: "Story point target is required." })
    .min(1, "Story point target must be at least 1."),
});

type SprintCreateFormValues = z.infer<typeof sprintCreateSchema>;
type SprintTargetFormValues = z.infer<typeof sprintTargetSchema>;

/**
 * Format ISO datetime string for display
 * @param value - ISO datetime string
 * @returns Human-readable date format
 */
function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "Invalid date";
  }
  return date.toLocaleDateString("en-US", {
    weekday: "short",
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

/**
 * Coordinator Sprint Management Page
 *
 * Features:
 * - Create new sprints with start and end dates
 * - Set story point targets for sprints
 * - View all active and completed sprints
 * - Real-time error handling and validation
 * - Full dark mode support
 */
export default function CoordinatorSprintSetupPage() {
  // ==================== State Management ====================

  // Sprints list
  const [sprints, setSprints] = useState<Sprint[]>([]);

  // Loading and error states
  const [isLoading, setIsLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [createError, setCreateError] = useState<string | null>(null);
  const [targetError, setTargetError] = useState<string | null>(null);

  // Success messages
  const [createMessage, setCreateMessage] = useState("");
  const [targetMessage, setTargetMessage] = useState("");

  // Edit mode for sprint target
  const [editingTargetId, setEditingTargetId] = useState<string | null>(null);

  // ==================== Form Setup ====================

  const createForm = useForm<SprintCreateFormValues>({
    resolver: zodResolver(sprintCreateSchema),
    defaultValues: {
      startDate: "",
      endDate: "",
    },
    mode: "onSubmit",
  });

  const targetForm = useForm<SprintTargetFormValues>({
    resolver: zodResolver(sprintTargetSchema),
    defaultValues: {
      storyPointTarget: 0,
    },
    mode: "onSubmit",
  });

  // ==================== Effects ====================

  /**
   * Fetch sprints from backend on component mount
   * Calls GET /coordinator/sprints endpoint
   */
  useEffect(() => {
    const fetchSprints = async () => {
      setIsLoading(true);
      setFetchError(null);

      try {
        const token = getAuthToken();
        if (!token) {
          router.push("/login");
          return;
        }
        const { getSprints } = await import("@/lib/api-client");
        const data = await getSprints(token);
        setSprints(data);
      } catch (err) {
        const errorMsg =
          err && typeof err === "object" && "message" in err
            ? String(err.message)
            : "Failed to load sprints";
        setFetchError(errorMsg);
      } finally {
        setIsLoading(false);
      }
    };

    fetchSprints();
  }, []);

  // ==================== Event Handlers ====================

  /**
   * Handle sprint creation form submission
   * Validates form, calls backend API, updates list
   */
  const handleCreateSubmit = createForm.handleSubmit(async (values) => {
    setCreateMessage("");
    setCreateError(null);

    try {
      const token = getAuthToken();
      if (!token) {
        throw new Error("Authentication required");
      }

      // Call backend API to create sprint
      const created = await createSprint(values, token);

      // Add new sprint to the beginning of the list
      setSprints((previous) => [created, ...previous]);

      // Reset form and show success message
      createForm.reset({
        startDate: "",
        endDate: "",
      });
      setCreateMessage("✓ Sprint created successfully");

      // Clear success message after 3 seconds
      setTimeout(() => setCreateMessage(""), 3000);
    } catch (err) {
      const errorMsg =
        err && typeof err === "object" && "message" in err
          ? String(err.message)
          : "Failed to create sprint";
      setCreateError(errorMsg);
    }
  });

  /**
   * Handle sprint target update submission
   * Validates form, calls backend API, updates list
   */
  const handleTargetSubmit = targetForm.handleSubmit(async (values) => {
    if (!editingTargetId) {
      return;
    }

    setTargetMessage("");
    setTargetError(null);

    try {
      const token = getAuthToken();
      if (!token) {
        throw new Error("Authentication required");
      }

      // Call backend API to update sprint target
      await updateSprintTarget(
        editingTargetId,
        values.storyPointTarget,
        token
      );

      // Update the sprint in the list
      setSprints((previous) =>
        previous.map((sprint) =>
          sprint.id === editingTargetId
            ? { ...sprint, storyPointTarget: values.storyPointTarget }
            : sprint
        )
      );

      // Reset form and show success message
      setTargetMessage("✓ Sprint target updated successfully");
      setEditingTargetId(null);

      // Clear success message after 3 seconds
      setTimeout(() => setTargetMessage(""), 3000);
    } catch (err) {
      const errorMsg =
        err && typeof err === "object" && "message" in err
          ? String(err.message)
          : "Failed to update sprint target";
      setTargetError(errorMsg);
    }
  });

  /**
   * Open edit mode for sprint target
   */
  const handleOpenTargetEdit = (sprint: Sprint) => {
    setEditingTargetId(sprint.id);
    setTargetError(null);
    targetForm.reset({
      storyPointTarget: sprint.storyPointTarget || 0,
    });
  };

  /**
   * Cancel edit mode
   */
  const handleCancelEdit = () => {
    setEditingTargetId(null);
    setTargetError(null);
  };

  // ==================== Render ====================

  return (
    <main className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
      <div className="mx-auto w-full max-w-4xl space-y-6">
        {/* Page Header */}
        <header className="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
            Sprint Setup
          </h1>
          <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
            Create and manage project sprints with story point targets.
          </p>
        </header>

        {/* Fetch Error Alert */}
        {fetchError && (
          <div className="flex items-start gap-3 rounded-lg border border-red-300 bg-red-50 p-4 dark:border-red-800 dark:bg-red-950/50">
            <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
            <div>
              <p className="font-medium text-red-900 dark:text-red-300">
                Error Loading Sprints
              </p>
              <p className="text-sm text-red-700 dark:text-red-400">
                {fetchError}
              </p>
            </div>
          </div>
        )}

        {/* Loading State */}
        {isLoading && !fetchError && (
          <div className="flex items-center justify-center rounded-lg border border-slate-200 bg-white p-12 dark:border-slate-700 dark:bg-slate-800">
            <div className="text-center">
              <Loader className="mx-auto h-8 w-8 animate-spin text-blue-600 dark:text-blue-400" />
              <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
                Loading sprints...
              </p>
            </div>
          </div>
        )}

        {/* Main Content */}
        {!isLoading && !fetchError && (
          <div className="space-y-6">
            {/* Create Sprint Form */}
            <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
              <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
                <Calendar className="h-5 w-5" />
                Create New Sprint
              </h2>

              <form
                onSubmit={handleCreateSubmit}
                className="space-y-4"
                noValidate
              >
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="block space-y-1.5">
                    <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                      Start Date
                    </span>
                    <input
                      type="date"
                      className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400"
                      {...createForm.register("startDate")}
                    />
                    {createForm.formState.errors.startDate && (
                      <p className="text-xs text-red-600 dark:text-red-400">
                        {createForm.formState.errors.startDate.message}
                      </p>
                    )}
                  </label>

                  <label className="block space-y-1.5">
                    <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                      End Date
                    </span>
                    <input
                      type="date"
                      className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400"
                      {...createForm.register("endDate")}
                    />
                    {createForm.formState.errors.endDate && (
                      <p className="text-xs text-red-600 dark:text-red-400">
                        {createForm.formState.errors.endDate.message}
                      </p>
                    )}
                  </label>
                </div>

                {createError && (
                  <div className="flex items-start gap-2 rounded-lg border border-red-300 bg-red-50 p-3 dark:border-red-800 dark:bg-red-950/50">
                    <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-600 dark:text-red-400" />
                    <p className="text-xs text-red-700 dark:text-red-400">
                      {createError}
                    </p>
                  </div>
                )}

                {createMessage && (
                  <div className="flex items-start gap-2 rounded-lg border border-emerald-300 bg-emerald-50 p-3 dark:border-emerald-800 dark:bg-emerald-950/50">
                    <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
                    <p className="text-xs text-emerald-700 dark:text-emerald-400">
                      {createMessage}
                    </p>
                  </div>
                )}

                <button
                  type="submit"
                  disabled={
                    createForm.formState.isSubmitting ||
                    createForm.formState.isLoading
                  }
                  className="w-full rounded-lg bg-blue-600 px-4 py-2 font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
                >
                  {createForm.formState.isSubmitting
                    ? "Creating..."
                    : "Create Sprint"}
                </button>
              </form>
            </article>

            {/* Sprints List */}
            <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
              <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
                <Play className="h-5 w-5" />
                Active Sprints ({sprints.length})
              </h2>

              {sprints.length === 0 ? (
                <p className="text-sm text-slate-600 dark:text-slate-400">
                  No sprints yet. Create one using the form above.
                </p>
              ) : (
                <div className="space-y-4">
                  {sprints.map((sprint) => (
                    <div
                      key={sprint.id}
                      className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/50"
                    >
                      <div className="mb-4 flex items-start justify-between gap-4">
                        <div className="flex-1">
                          <h3 className="font-medium text-slate-900 dark:text-white">
                            Sprint: {formatDate(sprint.startDate)} →{" "}
                            {formatDate(sprint.endDate)}
                          </h3>
                          <p className="mt-1 text-xs text-slate-600 dark:text-slate-400">
                            ID: <span className="font-mono">{sprint.id}</span>
                          </p>
                        </div>
                      </div>

                      {/* Story Point Target Section */}
                      {editingTargetId === sprint.id ? (
                        // Edit Mode
                        <form
                          onSubmit={handleTargetSubmit}
                          className="space-y-3"
                          noValidate
                        >
                          <label className="block space-y-1">
                            <span className="text-xs font-medium text-slate-700 dark:text-slate-300">
                              Story Point Target
                            </span>
                            <input
                              type="number"
                              min="1"
                              className="w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-900 outline-none transition focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400"
                              {...targetForm.register("storyPointTarget", {
                                valueAsNumber: true,
                              })}
                            />
                            {targetForm.formState.errors.storyPointTarget && (
                              <p className="text-xs text-red-600 dark:text-red-400">
                                {
                                  targetForm.formState.errors.storyPointTarget
                                    .message
                                }
                              </p>
                            )}
                          </label>

                          {targetError && (
                            <div className="flex items-start gap-2 rounded-md border border-red-300 bg-red-100 p-2 dark:border-red-800 dark:bg-red-950/50">
                              <AlertCircle className="mt-0.5 h-3 w-3 shrink-0 text-red-600 dark:text-red-400" />
                              <p className="text-xs text-red-700 dark:text-red-400">
                                {targetError}
                              </p>
                            </div>
                          )}

                          <div className="flex gap-2">
                            <button
                              type="submit"
                              disabled={targetForm.formState.isSubmitting}
                              className="flex-1 rounded-md bg-blue-600 px-2 py-1 text-xs font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
                            >
                              {targetForm.formState.isSubmitting
                                ? "Saving..."
                                : "Save Target"}
                            </button>
                            <button
                              type="button"
                              onClick={handleCancelEdit}
                              disabled={targetForm.formState.isSubmitting}
                              className="flex-1 rounded-md border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
                            >
                              Cancel
                            </button>
                          </div>

                          {targetMessage && (
                            <div className="flex items-start gap-2 rounded-md border border-emerald-300 bg-emerald-100 p-2 dark:border-emerald-800 dark:bg-emerald-950/50">
                              <CheckCircle2 className="mt-0.5 h-3 w-3 shrink-0 text-emerald-600 dark:text-emerald-400" />
                              <p className="text-xs text-emerald-700 dark:text-emerald-400">
                                {targetMessage}
                              </p>
                            </div>
                          )}
                        </form>
                      ) : (
                        // Display Mode
                        <div className="flex items-center justify-between gap-4">
                          <div className="flex items-center gap-2">
                            <TrendingUp className="h-4 w-4 text-blue-600 dark:text-blue-400" />
                            <div>
                              <p className="text-xs font-medium text-slate-700 dark:text-slate-300">
                                Story Point Target
                              </p>
                              <p className="text-sm font-semibold text-slate-900 dark:text-white">
                                {sprint.storyPointTarget || "Not set"}
                              </p>
                            </div>
                          </div>
                          <button
                            onClick={() => handleOpenTargetEdit(sprint)}
                            className="rounded-md bg-blue-100 px-3 py-1 text-xs font-medium text-blue-600 transition hover:bg-blue-200 dark:bg-blue-900/30 dark:text-blue-400 dark:hover:bg-blue-900/50"
                          >
                            Edit
                          </button>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </article>
          </div>
        )}
      </div>
    </main>
  );
}
