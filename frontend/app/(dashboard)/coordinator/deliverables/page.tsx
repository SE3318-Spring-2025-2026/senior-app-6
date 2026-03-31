"use client";

import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { CalendarDays, CheckCircle2, Edit, Plus } from "lucide-react";

const deliverableTypes = ["Proposal", "SoW", "Demonstration"] as const;

type DeliverableType = (typeof deliverableTypes)[number];

type Deliverable = {
  id: string;
  name: string;
  type: DeliverableType;
  submissionDeadline: string;
  reviewDeadline: string;
};

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

function mockCreateDeliverable(
  values: DeliverableCreateFormValues
): Promise<Deliverable> {
  return new Promise((resolve) => {
    window.setTimeout(() => {
      resolve({
        id: typeof crypto !== "undefined" && "randomUUID" in crypto
          ? crypto.randomUUID()
          : String(Date.now()),
        name: values.name.trim(),
        type: values.type,
        submissionDeadline: values.submissionDeadline,
        reviewDeadline: values.reviewDeadline,
      });
    }, 500);
  });
}

function mockPatchDeliverable(
  id: string,
  values: DeliverableEditFormValues
): Promise<{ id: string; submissionDeadline: string; reviewDeadline: string }> {
  return new Promise((resolve) => {
    window.setTimeout(() => {
      resolve({
        id,
        submissionDeadline: values.submissionDeadline,
        reviewDeadline: values.reviewDeadline,
      });
    }, 450);
  });
}

export default function CoordinatorDeliverablesPage() {
  const [deliverables, setDeliverables] = useState<Deliverable[]>([
    {
      id: "seed-1",
      name: "Initial Project Proposal",
      type: "Proposal",
      submissionDeadline: "2026-04-08T17:00",
      reviewDeadline: "2026-04-10T17:00",
    },
  ]);
  const [createMessage, setCreateMessage] = useState("");
  const [editMessage, setEditMessage] = useState("");
  const [activeEditId, setActiveEditId] = useState<string | null>(null);

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

  const activeDeliverable = useMemo(
    () => deliverables.find((item) => item.id === activeEditId) ?? null,
    [activeEditId, deliverables]
  );

  const handleCreateSubmit = createForm.handleSubmit(async (values) => {
    setCreateMessage("");

    const created = await mockCreateDeliverable(values);
    setDeliverables((previous) => [created, ...previous]);
    createForm.reset({
      name: "",
      type: "Proposal",
      submissionDeadline: "",
      reviewDeadline: "",
    });
    setCreateMessage("Deliverable saved");
  });

  const handleOpenEdit = (item: Deliverable) => {
    setEditMessage("");
    setActiveEditId(item.id);
    editForm.reset({
      submissionDeadline: item.submissionDeadline,
      reviewDeadline: item.reviewDeadline,
    });
  };

  const handleEditSubmit = editForm.handleSubmit(async (values) => {
    if (!activeEditId) {
      return;
    }

    const updated = await mockPatchDeliverable(activeEditId, values);

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

    setEditMessage("Deliverable updated");
    setActiveEditId(null);
  });

  return (
    <main className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 md:p-8">
      <div className="mx-auto w-full max-w-7xl space-y-6">
        <header className="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur">
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900 md:text-3xl">
            Deliverables Management
          </h1>
          <p className="mt-2 text-sm text-slate-600">
            Create and manage project deliverables, deadlines, and review windows.
          </p>
        </header>

        <section className="grid gap-6 lg:grid-cols-[minmax(320px,380px)_1fr]">
          <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="flex items-center gap-2 text-lg font-semibold text-slate-900">
              <Plus className="h-5 w-5 text-slate-700" aria-hidden="true" />
              New Deliverable
            </h2>

            <form onSubmit={handleCreateSubmit} className="mt-4 space-y-4" noValidate>
              <label className="block space-y-1.5">
                <span className="text-sm font-medium text-slate-700">Name</span>
                <input
                  type="text"
                  placeholder="Enter deliverable name"
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-slate-500"
                  {...createForm.register("name")}
                />
                {createForm.formState.errors.name && (
                  <p className="text-xs text-red-600">
                    {createForm.formState.errors.name.message}
                  </p>
                )}
              </label>

              <label className="block space-y-1.5">
                <span className="text-sm font-medium text-slate-700">Type</span>
                <select
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-slate-500"
                  {...createForm.register("type")}
                >
                  {deliverableTypes.map((type) => (
                    <option key={type} value={type}>
                      {type}
                    </option>
                  ))}
                </select>
                {createForm.formState.errors.type && (
                  <p className="text-xs text-red-600">
                    {createForm.formState.errors.type.message}
                  </p>
                )}
              </label>

              <label className="block space-y-1.5">
                <span className="text-sm font-medium text-slate-700">
                  Submission Deadline
                </span>
                <input
                  type="datetime-local"
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-slate-500"
                  {...createForm.register("submissionDeadline")}
                />
                {createForm.formState.errors.submissionDeadline && (
                  <p className="text-xs text-red-600">
                    {createForm.formState.errors.submissionDeadline.message}
                  </p>
                )}
              </label>

              <label className="block space-y-1.5">
                <span className="text-sm font-medium text-slate-700">Review Deadline</span>
                <input
                  type="datetime-local"
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-slate-500"
                  {...createForm.register("reviewDeadline")}
                />
                {createForm.formState.errors.reviewDeadline && (
                  <p className="text-xs text-red-600">
                    {createForm.formState.errors.reviewDeadline.message}
                  </p>
                )}
              </label>

              {createMessage && (
                <p className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                  <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                  {createMessage}
                </p>
              )}

              <button
                type="submit"
                disabled={createForm.formState.isSubmitting}
                className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <Plus className="h-4 w-4" aria-hidden="true" />
                {createForm.formState.isSubmitting ? "Saving..." : "Create Deliverable"}
              </button>
            </form>
          </article>

          <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-lg font-semibold text-slate-900">Active Deliverables</h2>

            <div className="mt-4 space-y-4">
              {deliverables.length === 0 ? (
                <p className="rounded-lg border border-dashed border-slate-300 p-4 text-sm text-slate-600">
                  No deliverables yet. Create your first one from the panel.
                </p>
              ) : (
                deliverables.map((item) => (
                  <div
                    key={item.id}
                    className="rounded-xl border border-slate-200 bg-slate-50/40 p-4"
                  >
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <h3 className="text-base font-semibold text-slate-900">{item.name}</h3>
                        <p className="mt-1 text-sm text-slate-600">Type: {item.type}</p>
                      </div>
                      <button
                        type="button"
                        onClick={() => handleOpenEdit(item)}
                        className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition hover:border-slate-400 hover:text-slate-900"
                      >
                        <Edit className="h-3.5 w-3.5" aria-hidden="true" />
                        Edit
                      </button>
                    </div>

                    <div className="mt-3 grid gap-2 text-sm text-slate-700 sm:grid-cols-2">
                      <p className="inline-flex items-center gap-2">
                        <CalendarDays className="h-4 w-4 text-slate-500" aria-hidden="true" />
                        Submission: {formatDeadline(item.submissionDeadline)}
                      </p>
                      <p className="inline-flex items-center gap-2">
                        <CalendarDays className="h-4 w-4 text-slate-500" aria-hidden="true" />
                        Review: {formatDeadline(item.reviewDeadline)}
                      </p>
                    </div>

                    {activeEditId === item.id && (
                      <form
                        onSubmit={handleEditSubmit}
                        className="mt-4 rounded-lg border border-slate-200 bg-white p-3"
                        noValidate
                      >
                        <p className="mb-3 text-sm font-medium text-slate-800">Edit Deadlines</p>

                        <div className="grid gap-3 sm:grid-cols-2">
                          <label className="block space-y-1">
                            <span className="text-xs font-medium text-slate-700">
                              Submission Deadline
                            </span>
                            <input
                              type="datetime-local"
                              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-500"
                              {...editForm.register("submissionDeadline")}
                            />
                            {editForm.formState.errors.submissionDeadline && (
                              <p className="text-xs text-red-600">
                                {editForm.formState.errors.submissionDeadline.message}
                              </p>
                            )}
                          </label>

                          <label className="block space-y-1">
                            <span className="text-xs font-medium text-slate-700">
                              Review Deadline
                            </span>
                            <input
                              type="datetime-local"
                              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-500"
                              {...editForm.register("reviewDeadline")}
                            />
                            {editForm.formState.errors.reviewDeadline && (
                              <p className="text-xs text-red-600">
                                {editForm.formState.errors.reviewDeadline.message}
                              </p>
                            )}
                          </label>
                        </div>

                        <div className="mt-3 flex flex-wrap items-center gap-2">
                          <button
                            type="submit"
                            disabled={editForm.formState.isSubmitting}
                            className="inline-flex items-center rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            {editForm.formState.isSubmitting ? "Saving..." : "Save Changes"}
                          </button>
                          <button
                            type="button"
                            onClick={() => setActiveEditId(null)}
                            className="inline-flex items-center rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition hover:border-slate-400 hover:text-slate-900"
                          >
                            Cancel
                          </button>
                        </div>
                      </form>
                    )}
                  </div>
                ))
              )}
            </div>

            {editMessage && !activeDeliverable && (
              <p className="mt-4 flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                {editMessage}
              </p>
            )}
          </article>
        </section>
      </div>
    </main>
  );
}
