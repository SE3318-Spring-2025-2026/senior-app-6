"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  CalendarDays,
  CheckCircle2,
  Flag,
  Loader2,
  Lock,
  Megaphone,
  Plus,
} from "lucide-react";

const sprintSchema = z
  .object({
    sprintName: z
      .string()
      .trim()
      .min(2, "Sprint name must be at least 2 characters."),
    startDate: z.string().min(1, "Start date is required."),
    endDate: z.string().min(1, "End date is required."),
    storyPointTarget: z
      .number({ error: "Story point target is required." })
      .int("Story point target must be a whole number.")
      .min(1, "Story point target must be at least 1."),
  })
  .refine(
    (values) =>
      new Date(values.endDate).getTime() > new Date(values.startDate).getTime(),
    {
      path: ["endDate"],
      message: "End date must be after start date.",
    }
  );

type SprintFormValues = z.infer<typeof sprintSchema>;

type Sprint = {
  id: string;
  sprintName: string;
  startDate: string;
  endDate: string;
  storyPointTarget: number;
};

function formatDate(value: string): string {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "Invalid date";
  }

  return parsed.toLocaleDateString("en-US", {
    dateStyle: "medium",
  });
}

function mockCreateSprint(values: SprintFormValues): Promise<Sprint> {
  return new Promise((resolve) => {
    window.setTimeout(() => {
      resolve({
        id:
          typeof crypto !== "undefined" && "randomUUID" in crypto
            ? crypto.randomUUID()
            : String(Date.now()),
        sprintName: values.sprintName.trim(),
        startDate: values.startDate,
        endDate: values.endDate,
        storyPointTarget: values.storyPointTarget,
      });
    }, 500);
  });
}

function mockPublishConfiguration(): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(() => {
      resolve();
    }, 700);
  });
}

export default function SprintSetupPage() {
  const [sprints, setSprints] = useState<Sprint[]>([
    {
      id: "seed-sprint-1",
      sprintName: "Sprint 1",
      startDate: "2026-04-01",
      endDate: "2026-04-14",
      storyPointTarget: 15,
    },
    {
      id: "seed-sprint-2",
      sprintName: "Sprint 2",
      startDate: "2026-04-15",
      endDate: "2026-04-28",
      storyPointTarget: 20,
    },
  ]);
  const [sprintMessage, setSprintMessage] = useState("");
  const [publishMessage, setPublishMessage] = useState("");
  const [showPublishConfirm, setShowPublishConfirm] = useState(false);
  const [isPublishing, setIsPublishing] = useState(false);
  const [isPlatformLocked, setIsPlatformLocked] = useState(false);

  const sprintForm = useForm<SprintFormValues>({
    resolver: zodResolver(sprintSchema),
    defaultValues: {
      sprintName: "",
      startDate: "",
      endDate: "",
      storyPointTarget: 10,
    },
    mode: "onSubmit",
  });

  const handleSprintCreate = sprintForm.handleSubmit(async (values) => {
    if (isPlatformLocked) {
      return;
    }

    setSprintMessage("");
    const created = await mockCreateSprint(values);

    setSprints((previous) => [created, ...previous]);
    sprintForm.reset({
      sprintName: "",
      startDate: "",
      endDate: "",
      storyPointTarget: 10,
    });
    setSprintMessage("Sprint saved successfully");
  });

  const handleConfirmPublish = async () => {
    setPublishMessage("");
    setIsPublishing(true);
    await mockPublishConfiguration();
    setIsPublishing(false);
    setShowPublishConfirm(false);
    setIsPlatformLocked(true);
    setPublishMessage(
      "System is currently ACTIVE and LOCKED. No further changes can be made."
    );
  };

  return (
    <main className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 md:p-8">
      <div className="mx-auto w-full max-w-7xl space-y-6">
        <header className="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur">
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900 md:text-3xl">
            Sprint Configuration &amp; Publishing
          </h1>
          <p className="mt-2 text-sm text-slate-600">
            Configure sprint cadence and publish final platform settings for
            students.
          </p>
        </header>

        {isPlatformLocked && (
          <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
            <p className="flex items-center gap-2 font-medium">
              <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
              System is currently ACTIVE and LOCKED. No further changes can be
              made.
            </p>
          </div>
        )}

        <section className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
          <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="flex items-center gap-2 text-lg font-semibold text-slate-900">
              <Flag className="h-5 w-5 text-slate-700" aria-hidden="true" />
              Sprint Management
            </h2>

            <form onSubmit={handleSprintCreate} className="mt-4 space-y-4" noValidate>
              <label className="block space-y-1.5">
                <span className="text-sm font-medium text-slate-700">Sprint Name</span>
                <input
                  type="text"
                  placeholder="e.g., Sprint 1"
                  disabled={isPlatformLocked || sprintForm.formState.isSubmitting}
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-slate-500 disabled:cursor-not-allowed disabled:bg-slate-100"
                  {...sprintForm.register("sprintName")}
                />
                {sprintForm.formState.errors.sprintName && (
                  <p className="text-xs text-red-600">
                    {sprintForm.formState.errors.sprintName.message}
                  </p>
                )}
              </label>

              <div className="grid gap-4 md:grid-cols-2">
                <label className="block space-y-1.5">
                  <span className="text-sm font-medium text-slate-700">Start Date</span>
                  <input
                    type="date"
                    disabled={isPlatformLocked || sprintForm.formState.isSubmitting}
                    className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-slate-500 disabled:cursor-not-allowed disabled:bg-slate-100"
                    {...sprintForm.register("startDate")}
                  />
                  {sprintForm.formState.errors.startDate && (
                    <p className="text-xs text-red-600">
                      {sprintForm.formState.errors.startDate.message}
                    </p>
                  )}
                </label>

                <label className="block space-y-1.5">
                  <span className="text-sm font-medium text-slate-700">End Date</span>
                  <input
                    type="date"
                    disabled={isPlatformLocked || sprintForm.formState.isSubmitting}
                    className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-slate-500 disabled:cursor-not-allowed disabled:bg-slate-100"
                    {...sprintForm.register("endDate")}
                  />
                  {sprintForm.formState.errors.endDate && (
                    <p className="text-xs text-red-600">
                      {sprintForm.formState.errors.endDate.message}
                    </p>
                  )}
                </label>
              </div>

              <label className="block space-y-1.5">
                <span className="text-sm font-medium text-slate-700">
                  Story Point Target (per student)
                </span>
                <input
                  type="number"
                  inputMode="numeric"
                  min={1}
                  disabled={isPlatformLocked || sprintForm.formState.isSubmitting}
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-slate-500 disabled:cursor-not-allowed disabled:bg-slate-100"
                  {...sprintForm.register("storyPointTarget", {
                    valueAsNumber: true,
                  })}
                />
                {sprintForm.formState.errors.storyPointTarget && (
                  <p className="text-xs text-red-600">
                    {sprintForm.formState.errors.storyPointTarget.message}
                  </p>
                )}
              </label>

              {sprintMessage && (
                <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                  {sprintMessage}
                </p>
              )}

              <button
                type="submit"
                disabled={isPlatformLocked || sprintForm.formState.isSubmitting}
                className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <Plus className="h-4 w-4" aria-hidden="true" />
                {sprintForm.formState.isSubmitting ? "Saving..." : "Create Sprint"}
              </button>
            </form>

            <div className="mt-6 space-y-3">
              {sprints.map((sprint) => (
                <div
                  key={sprint.id}
                  className="rounded-xl border border-slate-200 bg-slate-50/70 p-4"
                >
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <h3 className="text-sm font-semibold text-slate-900">
                        {sprint.sprintName}
                      </h3>
                      <p className="mt-1 flex items-center gap-1 text-xs text-slate-600">
                        <CalendarDays className="h-3.5 w-3.5" aria-hidden="true" />
                        {formatDate(sprint.startDate)} - {formatDate(sprint.endDate)}
                      </p>
                    </div>
                    <span className="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-medium text-slate-700">
                      Target: {sprint.storyPointTarget} pts
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </article>

          <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="flex items-center gap-2 text-lg font-semibold text-slate-900">
              <Megaphone className="h-5 w-5 text-slate-700" aria-hidden="true" />
              Publish Platform
            </h2>

            <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                System Status
              </p>
              <p className="mt-2 text-sm text-slate-700">
                Ready to publish: 3 Deliverables, Rubrics set, {sprints.length} Sprints
                configured.
              </p>
            </div>

            {publishMessage && (
              <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                {publishMessage}
              </div>
            )}

            <button
              type="button"
              onClick={() => setShowPublishConfirm(true)}
              disabled={isPlatformLocked || isPublishing || sprintForm.formState.isSubmitting}
              className="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-emerald-600 px-4 py-3 text-sm font-semibold text-white transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <Lock className="h-4 w-4" aria-hidden="true" />
              Publish Configuration
            </button>
          </article>
        </section>

        {showPublishConfirm && !isPlatformLocked && (
          <div
            className="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/35 p-4"
            role="dialog"
            aria-modal="true"
            aria-labelledby="publish-confirmation-title"
          >
            <div className="w-full max-w-lg rounded-2xl border border-slate-200 bg-white p-5 shadow-xl">
              <h3
                id="publish-confirmation-title"
                className="text-lg font-semibold text-slate-900"
              >
                Confirm Publishing
              </h3>
              <p className="mt-2 text-sm text-slate-600">
                Are you sure? This will lock in the rules for the system and
                activate the platform for students.
              </p>

              <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                <button
                  type="button"
                  onClick={() => setShowPublishConfirm(false)}
                  disabled={isPublishing}
                  className="inline-flex items-center justify-center rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleConfirmPublish}
                  disabled={isPublishing}
                  className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {isPublishing && (
                    <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                  )}
                  {isPublishing ? "Publishing..." : "Confirm Publish"}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </main>
  );
}