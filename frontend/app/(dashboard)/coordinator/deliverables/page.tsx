"use client";

import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  CalendarDays,
  CheckCircle2,
  Edit,
  Plus,
  AlertCircle,
  Loader,
} from "lucide-react";
import {
  createDeliverable,
  updateDeliverable,
  getAuthToken,
  type Deliverable,
} from "@/lib/api-client";

// Deliverable types
const deliverableTypes = ["Proposal", "SoW", "Demonstration"] as const;
type DeliverableType = (typeof deliverableTypes)[number];

// ==================== Form Schemas ====================

/**
 * Schema for creating a new deliverable
 * Validates: name (min 2 chars), type (enum), and deadline ordering
 */
const deliverableCreateSchema = z
  .object({
    name: z
      .string()
      .trim()
      .min(2, "Deliverable name must be at least 2 characters."),
    type: z.enum(deliverableTypes, {
      error: "Please select a valid deliverable type.",
    }),
    submissionDeadline: z
      .string()
      .min(1, "Submission deadline is required."),
    reviewDeadline: z
      .string()
      .min(1, "Review deadline is required."),
  })
  .refine(
    (values) =>
      new Date(values.reviewDeadline).getTime() >
      new Date(values.submissionDeadline).getTime(),
    {
      path: ["reviewDeadline"],
      message: "Review deadline must be after submission deadline.",
    }
  );

/**
 * Schema for updating deliverable deadlines
 * Validates deadline ordering only (name and type cannot be modified)
 */
const deliverableEditSchema = z
  .object({
    submissionDeadline: z
      .string()
      .min(1, "Submission deadline is required."),
    reviewDeadline: z
      .string()
      .min(1, "Review deadline is required."),
  })
  .refine(
    (values) =>
      new Date(values.reviewDeadline).getTime() >
      new Date(values.submissionDeadline).getTime(),
    {
      path: ["reviewDeadline"],
      message: "Review deadline must be after submission deadline.",
    }
  );

type DeliverableCreateFormValues = z.infer<typeof deliverableCreateSchema>;
type DeliverableEditFormValues = z.infer<typeof deliverableEditSchema>;

/**
 * Format a date string for human-readable display
 * @param value - ISO date string
 * @returns Formatted date in "MMM D, YYYY, h:mm A" format
 */
function formatDeadline(value: string): string {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "Invalid date";
  }

  return parsed.toLocaleString("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

/**
 * Coordinator Deliverables Management Page
 *
 * Features:
 * - Display all deliverables with automatic fetching from backend
 * - Create new deliverables with validation
 * - Edit existing deliverables (deadline modification only)
 * - Real-time error handling and loading states
 * - Loading indicator and error messages
 */
export default function CoordinatorDeliverablesPage() {
  // ==================== State Management ====================

  // Main deliverables list fetched from backend
  const [deliverables, setDeliverables] = useState<Deliverable[]>([]);

  // Loading and error states
  const [isLoading, setIsLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [createError, setCreateError] = useState<string | null>(null);
  const [editError, setEditError] = useState<string | null>(null);

  // Success messages
  const [createMessage, setCreateMessage] = useState("");
  const [editMessage, setEditMessage] = useState("");

  // Edit mode state
  const [activeEditId, setActiveEditId] = useState<string | null>(null);

  // ==================== Form Setup ====================

  const createForm = useForm<DeliverableCreateFormValues>({
    resolver: zodResolver(deliverableCreateSchema),
    defaultValues: {
      name: "",
      type: "Proposal",
      submissionDeadline: "",
      reviewDeadline: "",
    },
    mode: "onSubmit",
  });

  const editForm = useForm<DeliverableEditFormValues>({
    resolver: zodResolver(deliverableEditSchema),
    defaultValues: {
      submissionDeadline: "",
      reviewDeadline: "",
    },
    mode: "onSubmit",
  });

  // ==================== Computed Properties ====================

  /**
   * Find the currently edited deliverable in the list
   */
  const activeDeliverable = useMemo(
    () => deliverables.find((item) => item.id === activeEditId) ?? null,
    [activeEditId, deliverables]
  );

  // ==================== Effects ====================

  /**
   * Fetch deliverables from backend on component mount
   * Note: Currently no GET endpoint exists, so this fetches seeded data
   * TODO: Implement GET /coordinator/deliverables endpoint
   */
  useEffect(() => {
    const fetchDeliverables = async () => {
      setIsLoading(true);
      setFetchError(null);

      try {
        // TODO: Replace with real API call when endpoint is available
        // const token = getAuthToken();
        // const data = await getDeliverables(token);
        // setDeliverables(data);

        // For now, use mock data
        setDeliverables([
          {
            id: "seed-1",
            name: "Initial Project Proposal",
            type: "Proposal",
            submissionDeadline: "2026-04-08T17:00",
            reviewDeadline: "2026-04-10T17:00",
          },
        ]);
      } catch (err) {
        const errorMsg =
          err && typeof err === "object" && "message" in err
            ? String(err.message)
            : "Failed to load deliverables";
        setFetchError(errorMsg);
      } finally {
        setIsLoading(false);
      }
    };

    fetchDeliverables();
  }, []);

  // ==================== Event Handlers ====================

  /**
   * Handle deliverable creation form submission
   * Validates form, calls backend API, updates list and resets form
   */
  const handleCreateSubmit = createForm.handleSubmit(
    async (values) => {
      setCreateMessage("");
      setCreateError(null);

      try {
        const token = getAuthToken();
        if (!token) {
          throw new Error("Authentication required");
        }

        // Call backend API to create deliverable
        const created = await createDeliverable(values, token);

        // Add new deliverable to the beginning of the list
        setDeliverables((previous) => [created, ...previous]);

        // Reset form and show success message
        createForm.reset({
          name: "",
          type: "Proposal",
          submissionDeadline: "",
          reviewDeadline: "",
        });
        setCreateMessage("✓ Deliverable created successfully");

        // Clear success message after 3 seconds
        setTimeout(() => setCreateMessage(""), 3000);
      } catch (err) {
        const errorMsg =
          err && typeof err === "object" && "message" in err
            ? String(err.message)
            : "Failed to create deliverable";
        setCreateError(errorMsg);
      }
    }
  );

  /**
   * Open edit mode for a specific deliverable
   * Loads its current deadlines into the edit form
   */
  const handleOpenEdit = (item: Deliverable) => {
    setEditMessage("");
    setEditError(null);
    setActiveEditId(item.id);
    editForm.reset({
      submissionDeadline: item.submissionDeadline,
      reviewDeadline: item.reviewDeadline,
    });
  };

  /**
   * Close edit mode without saving
   */
  const handleCancelEdit = () => {
    setActiveEditId(null);
    setEditError(null);
    editForm.reset();
  };

  /**
   * Handle deliverable update form submission
   * Validates form, calls backend API, updates list
   */
  const handleEditSubmit = editForm.handleSubmit(
    async (values) => {
      if (!activeEditId) {
        return;
      }

      setEditMessage("");
      setEditError(null);

      try {
        const token = getAuthToken();
        if (!token) {
          throw new Error("Authentication required");
        }

        // Call backend API to update deliverable
        const updated = await updateDeliverable(activeEditId, values, token);

        // Update the deliverable in the list
        setDeliverables((previous) =>
          previous.map((item) =>
            item.id === updated.id
              ? {
                  ...item,
                  submissionDeadline: updated.submissionDeadline,
                  reviewDeadline: updated.reviewDeadline,
                }
              : item
          )
        );

        // Reset form and show success message
        setEditMessage("✓ Deliverable updated successfully");
        setActiveEditId(null);

        // Clear success message after 3 seconds
        setTimeout(() => setEditMessage(""), 3000);
      } catch (err) {
        const errorMsg =
          err && typeof err === "object" && "message" in err
            ? String(err.message)
            : "Failed to update deliverable";
        setEditError(errorMsg);
      }
    }
  );

  // ==================== Render ====================

  return (
    <main className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
      <div className="mx-auto w-full max-w-7xl space-y-6">
        {/* Page Header */}
        <header className="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
            Deliverables Management
          </h1>
          <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
            Create and manage project deliverables, deadlines, and review
            windows.
          </p>
        </header>

        {/* Fetch Error Alert */}
        {fetchError && (
          <div className="flex items-start gap-3 rounded-lg border border-red-300 bg-red-50 p-4 dark:border-red-800 dark:bg-red-950/50">
            <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
            <div>
              <p className="font-medium text-red-900 dark:text-red-300">
                Error Loading Deliverables
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
                Loading deliverables...
              </p>
            </div>
          </div>
        )}

        {/* Main Content */}
        {!isLoading && !fetchError && (
          <section className="grid gap-6 lg:grid-cols-[minmax(320px,380px)_1fr]">
            {/* Create Deliverable Form */}
            <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
              <h2 className="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
                <Plus className="h-5 w-5 text-slate-700 dark:text-slate-300" />
                New Deliverable
              </h2>

              <form
                onSubmit={handleCreateSubmit}
                className="mt-4 space-y-4"
                noValidate
              >
                <label className="block space-y-1.5">
                  <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                    Name
                  </span>
                  <input
                    type="text"
                    placeholder="Enter deliverable name"
                    className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-slate-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:placeholder:text-slate-500 dark:focus:border-slate-400"
                    {...createForm.register("name")}
                  />
                  {createForm.formState.errors.name && (
                    <p className="text-xs text-red-600 dark:text-red-400">
                      {createForm.formState.errors.name.message}
                    </p>
                  )}
                </label>

                <label className="block space-y-1.5">
                  <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                    Type
                  </span>
                  <select
                    className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-slate-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-slate-400"
                    {...createForm.register("type")}
                  >
                    {deliverableTypes.map((type) => (
                      <option key={type} value={type}>
                        {type}
                      </option>
                    ))}
                  </select>
                  {createForm.formState.errors.type && (
                    <p className="text-xs text-red-600 dark:text-red-400">
                      {createForm.formState.errors.type.message}
                    </p>
                  )}
                </label>

                <label className="block space-y-1.5">
                  <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                    Submission Deadline
                  </span>
                  <input
                    type="datetime-local"
                    className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-slate-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-slate-400"
                    {...createForm.register("submissionDeadline")}
                  />
                  {createForm.formState.errors.submissionDeadline && (
                    <p className="text-xs text-red-600 dark:text-red-400">
                      {createForm.formState.errors.submissionDeadline.message}
                    </p>
                  )}
                </label>

                <label className="block space-y-1.5">
                  <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                    Review Deadline
                  </span>
                  <input
                    type="datetime-local"
                    className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-slate-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-slate-400"
                    {...createForm.register("reviewDeadline")}
                  />
                  {createForm.formState.errors.reviewDeadline && (
                    <p className="text-xs text-red-600 dark:text-red-400">
                      {createForm.formState.errors.reviewDeadline.message}
                    </p>
                  )}
                </label>

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
                  className="w-full rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
                >
                  {createForm.formState.isSubmitting
                    ? "Creating..."
                    : "Create Deliverable"}
                </button>
              </form>
            </article>

            {/* Deliverables List */}
            <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
              <h2 className="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
                <CalendarDays className="h-5 w-5 text-slate-700 dark:text-slate-300" />
                Deliverables ({deliverables.length})
              </h2>

              {deliverables.length === 0 ? (
                <p className="mt-4 text-sm text-slate-600 dark:text-slate-400">
                  No deliverables yet. Create one using the form on the left.
                </p>
              ) : (
                <div className="mt-4 space-y-3">
                  {deliverables.map((deliverable) =>
                    activeEditId === deliverable.id ? (
                      // Edit Mode
                      <div
                        key={deliverable.id}
                        className="rounded-lg border border-blue-300 bg-blue-50 p-4 dark:border-blue-800 dark:bg-blue-950/30"
                      >
                        <h3 className="mb-3 font-medium text-slate-900 dark:text-white">
                          Edit: {deliverable.name}
                        </h3>

                        <form
                          onSubmit={handleEditSubmit}
                          className="space-y-3"
                          noValidate
                        >
                          <label className="block space-y-1">
                            <span className="text-xs font-medium text-slate-700 dark:text-slate-300">
                              Submission Deadline
                            </span>
                            <input
                              type="datetime-local"
                              className="w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-900 outline-none transition focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400"
                              {...editForm.register("submissionDeadline")}
                            />
                            {editForm.formState.errors.submissionDeadline && (
                              <p className="text-xs text-red-600 dark:text-red-400">
                                {
                                  editForm.formState.errors.submissionDeadline
                                    .message
                                }
                              </p>
                            )}
                          </label>

                          <label className="block space-y-1">
                            <span className="text-xs font-medium text-slate-700 dark:text-slate-300">
                              Review Deadline
                            </span>
                            <input
                              type="datetime-local"
                              className="w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-900 outline-none transition focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400"
                              {...editForm.register("reviewDeadline")}
                            />
                            {editForm.formState.errors.reviewDeadline && (
                              <p className="text-xs text-red-600 dark:text-red-400">
                                {editForm.formState.errors.reviewDeadline.message}
                              </p>
                            )}
                          </label>

                          {editError && (
                            <div className="flex items-start gap-2 rounded-md border border-red-300 bg-red-100 p-2 dark:border-red-800 dark:bg-red-950/50">
                              <AlertCircle className="mt-0.5 h-3 w-3 shrink-0 text-red-600 dark:text-red-400" />
                              <p className="text-xs text-red-700 dark:text-red-400">
                                {editError}
                              </p>
                            </div>
                          )}

                          <div className="flex gap-2">
                            <button
                              type="submit"
                              disabled={editForm.formState.isSubmitting}
                              className="flex-1 rounded-md bg-blue-600 px-2 py-1 text-xs font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
                            >
                              {editForm.formState.isSubmitting
                                ? "Saving..."
                                : "Save Changes"}
                            </button>
                            <button
                              type="button"
                              onClick={handleCancelEdit}
                              disabled={editForm.formState.isSubmitting}
                              className="flex-1 rounded-md border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
                            >
                              Cancel
                            </button>
                          </div>
                        </form>

                        {editMessage && (
                          <div className="mt-3 flex items-start gap-2 rounded-md border border-emerald-300 bg-emerald-100 p-2 dark:border-emerald-800 dark:bg-emerald-950/50">
                            <CheckCircle2 className="mt-0.5 h-3 w-3 shrink-0 text-emerald-600 dark:text-emerald-400" />
                            <p className="text-xs text-emerald-700 dark:text-emerald-400">
                              {editMessage}
                            </p>
                          </div>
                        )}
                      </div>
                    ) : (
                      // Display Mode
                      <div
                        key={deliverable.id}
                        className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/50"
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div className="flex-1">
                            <h3 className="font-medium text-slate-900 dark:text-white">
                              {deliverable.name}
                            </h3>
                            <p className="mt-1 text-xs text-slate-600 dark:text-slate-400">
                              Type: <span className="font-medium">{deliverable.type}</span>
                            </p>
                            <div className="mt-3 space-y-1 text-xs text-slate-600 dark:text-slate-400">
                              <p>
                                📤 Submission:{" "}
                                <span className="font-mono">
                                  {formatDeadline(
                                    deliverable.submissionDeadline
                                  )}
                                </span>
                              </p>
                              <p>
                                ✅ Review:{" "}
                                <span className="font-mono">
                                  {formatDeadline(deliverable.reviewDeadline)}
                                </span>
                              </p>
                            </div>
                          </div>
                          <button
                            onClick={() => handleOpenEdit(deliverable)}
                            className="rounded-md bg-blue-100 p-2 text-blue-600 transition hover:bg-blue-200 dark:bg-blue-900/30 dark:text-blue-400 dark:hover:bg-blue-900/50"
                            title="Edit deliverable"
                          >
                            <Edit className="h-4 w-4" />
                          </button>
                        </div>
                      </div>
                    )
                  )}
                </div>
              )}
            </article>
          </section>
        )}
      </div>
    </main>
  );
}
