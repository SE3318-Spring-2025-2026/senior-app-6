"use client";

import { useFieldArray, useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  AlertCircle,
  CheckCircle2,
  Plus,
  Scale,
  Trash2,
  Loader,
} from "lucide-react";
import { getAuthToken, type GradingCriterion } from "@/lib/api-client";

// ==================== Constants ====================

/**
 * Available grading types for rubric criteria
 * - Binary: Pass/Fail grading
 * - Soft: Point-based grading (0-100)
 */
const gradingTypes = ["Binary", "Soft"] as const;
type GradingType = (typeof gradingTypes)[number];

// ==================== Form Schema ====================

/**
 * Rubric creation schema
 * Validates: deliverable selection, criteria definition, and weight distribution
 * Note: Total weight should ideally equal 100
 */
const rubricSchema = z.object({
  deliverableId: z.string().min(1, "Please select a deliverable."),
  criteria: z
    .array(
      z.object({
        criterionName: z
          .string()
          .trim()
          .min(1, "Criterion name is required."),
        weight: z
          .number({ error: "Weight is required." })
          .min(1, "Weight must be at least 1.")
          .max(100, "Weight cannot exceed 100."),
        gradingType: z.enum(gradingTypes, {
          error: "Please select a grading type.",
        }),
      })
    )
    .min(1, "At least one criterion is required."),
});

type RubricFormValues = z.infer<typeof rubricSchema>;

/**
 * Coordinator Evaluation Setup Page
 *
 * Features:
 * - Define grading rubrics for deliverables
 * - Add multiple criteria with weighted scoring
 * - Support for both binary and soft grading types
 * - Real-time validation and error handling
 * - Integration with backend API
 */
export default function CoordinatorEvaluationSetupPage() {
  // TODO: When backend provides /coordinator/deliverables GET endpoint,
  // fetch deliverables from backend. For now, using mock deliverables.
  const mockDeliverables = [
    { id: "1", name: "Proposal" },
    { id: "2", name: "Demonstration" },
    { id: "3", name: "SoW" },
  ];

  const form = useForm<RubricFormValues>({
    resolver: zodResolver(rubricSchema),
    defaultValues: {
      deliverableId: "",
      criteria: [
        {
          criterionName: "Code Quality",
          weight: 30,
          gradingType: "Soft",
        },
      ],
    },
    mode: "onChange",
  });

  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: "criteria",
  });

  // ==================== Calculations ====================

  /**
   * Calculate total weight from all criteria
   * Used for validation and display purposes
   */
  const totalWeight = form.watch("criteria").reduce((sum, c) => sum + c.weight, 0);

  /**
   * Check if total weight equals exactly 100
   * Used to provide visual feedback to users
   */
  const isWeightValid = totalWeight === 100;

  // ==================== Event Handlers ====================

  /**
   * Handle rubric submission
   * Validates form, calls backend API, shows success message
   */
  const onSubmit = form.handleSubmit(async (values) => {
    try {
      const token = getAuthToken();
      if (!token) {
        throw new Error("Authentication required");
      }

      const { createRubric } = await import("@/lib/api-client");
      await createRubric(values.deliverableId, values.criteria, token);
      
      console.log("✓ Rubric created successfully");
      alert("✓ Rubric created successfully");
      
      // Reset form
      form.reset({
        deliverableId: "",
        criteria: [
          {
            criterionName: "Code Quality",
            weight: 30,
            gradingType: "Soft",
          },
        ],
      });
    } catch (err) {
      const errorMsg =
        err && typeof err === "object" && "message" in err
          ? String(err.message)
          : "Failed to create rubric";
      alert(`✗ Error: ${errorMsg}`);
    }
  });

  /**
   * Add a new criterion to the rubric
   * Appends empty criterion structure for user to fill
   */
  const handleAddCriterion = () => {
    append({
      criterionName: "",
      weight: 10,
      gradingType: "Soft",
    });
  };

  /**
   * Remove a criterion from the rubric
   * @param index - Index of criterion to remove
   */
  const handleRemoveCriterion = (index: number) => {
    if (fields.length > 1) {
      remove(index);
    }
  };

  // ==================== Render ====================

  return (
    <main className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
      <div className="mx-auto w-full max-w-3xl space-y-6">
        {/* Page Header */}
        <header className="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
            Evaluation Setup
          </h1>
          <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
            Define grading rubrics and evaluation criteria for project deliverables.
          </p>
        </header>

        {/* Main Form */}
        <form onSubmit={onSubmit} className="space-y-6" noValidate>
          {/* Deliverable Selection */}
          <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
            <label className="block space-y-2">
              <span className="text-sm font-semibold text-slate-900 dark:text-white">
                Select Deliverable
              </span>
              <p className="text-xs text-slate-600 dark:text-slate-400">
                Choose which deliverable to create an evaluation rubric for
              </p>
              <select
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400 dark:focus:ring-blue-400/20"
                {...form.register("deliverableId")}
              >
                <option value="">-- Choose a deliverable --</option>
                {mockDeliverables.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
              {form.formState.errors.deliverableId && (
                <p className="text-xs text-red-600 dark:text-red-400">
                  {form.formState.errors.deliverableId.message}
                </p>
              )}
            </label>
          </article>

          {/* Criteria Definition */}
          <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-800 dark:shadow-lg">
            <div className="mb-6 flex items-center justify-between">
              <div>
                <h2 className="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
                  <Scale className="h-5 w-5" />
                  Grading Criteria
                </h2>
                <p className="mt-1 text-xs text-slate-600 dark:text-slate-400">
                  Define evaluation criteria and assign weights. Total weight should equal 100.
                </p>
              </div>
              <button
                type="button"
                onClick={handleAddCriterion}
                className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600"
              >
                <Plus className="h-4 w-4" />
                Add Criterion
              </button>
            </div>

            {/* Weight Validation Alert */}
            {form.watch("criteria").length > 0 && (
              <div
                className={`mb-4 flex items-start gap-3 rounded-lg border p-3 text-sm ${
                  isWeightValid
                    ? "border-emerald-300 bg-emerald-50 text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-300"
                    : "border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-800 dark:bg-amber-950/50 dark:text-amber-300"
                }`}
              >
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
                <div>
                  <p className="font-medium">
                    Total Weight: {totalWeight}/100
                  </p>
                  {!isWeightValid && (
                    <p className="mt-1 text-xs opacity-90">
                      Adjust criterion weights so the total equals exactly 100
                    </p>
                  )}
                </div>
              </div>
            )}

            {/* Criteria List */}
            <div className="space-y-4">
              {fields.map((field, index) => (
                <div
                  key={field.id}
                  className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/50"
                >
                  <div className="grid gap-4 md:grid-cols-[1fr_80px_140px_40px]">
                    {/* Criterion Name */}
                    <label className="block space-y-1">
                      <span className="text-xs font-medium text-slate-700 dark:text-slate-300">
                        Criterion Name
                      </span>
                      <input
                        type="text"
                        placeholder="e.g., Code Quality, Design"
                        className="w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:placeholder:text-slate-500 dark:focus:border-blue-400"
                        {...form.register(`criteria.${index}.criterionName`)}
                      />
                      {form.formState.errors.criteria?.[index]?.criterionName && (
                        <p className="text-xs text-red-600 dark:text-red-400">
                          {
                            form.formState.errors.criteria[index]?.criterionName
                              ?.message
                          }
                        </p>
                      )}
                    </label>

                    {/* Weight */}
                    <label className="block space-y-1">
                      <span className="text-xs font-medium text-slate-700 dark:text-slate-300">
                        Weight
                      </span>
                      <input
                        type="number"
                        min="1"
                        max="100"
                        className="w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-900 outline-none transition focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400"
                        {...form.register(`criteria.${index}.weight`, {
                          valueAsNumber: true,
                        })}
                      />
                      {form.formState.errors.criteria?.[index]?.weight && (
                        <p className="text-xs text-red-600 dark:text-red-400">
                          {form.formState.errors.criteria[index]?.weight?.message}
                        </p>
                      )}
                    </label>

                    {/* Grading Type */}
                    <label className="block space-y-1">
                      <span className="text-xs font-medium text-slate-700 dark:text-slate-300">
                        Type
                      </span>
                      <select
                        className="w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-900 outline-none transition focus:border-blue-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:focus:border-blue-400"
                        {...form.register(`criteria.${index}.gradingType`)}
                      >
                        {gradingTypes.map((type) => (
                          <option key={type} value={type}>
                            {type}
                          </option>
                        ))}
                      </select>
                      {form.formState.errors.criteria?.[index]?.gradingType && (
                        <p className="text-xs text-red-600 dark:text-red-400">
                          {
                            form.formState.errors.criteria[index]?.gradingType
                              ?.message
                          }
                        </p>
                      )}
                    </label>

                    {/* Delete Button */}
                    <div className="flex items-end">
                      <button
                        type="button"
                        onClick={() => handleRemoveCriterion(index)}
                        disabled={fields.length === 1}
                        className="w-full rounded-md bg-red-100 p-1 text-red-600 transition hover:bg-red-200 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-red-900/30 dark:text-red-400 dark:hover:bg-red-900/50"
                        title="Remove criterion"
                      >
                        <Trash2 className="h-4 w-4 mx-auto" />
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {form.formState.errors.criteria && (
              <div className="mt-4 flex items-start gap-2 rounded-lg border border-red-300 bg-red-50 p-3 dark:border-red-800 dark:bg-red-950/50">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-600 dark:text-red-400" />
                <p className="text-xs text-red-700 dark:text-red-400">
                  {form.formState.errors.criteria.message}
                </p>
              </div>
            )}
          </article>

          {/* Submit Button */}
          <button
            type="submit"
            disabled={form.formState.isSubmitting || !isWeightValid}
            className="w-full rounded-lg bg-blue-600 px-4 py-3 font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-blue-700 dark:hover:bg-blue-600"
          >
            {form.formState.isSubmitting ? "Creating Rubric..." : "Create Rubric"}
          </button>
        </form>
      </div>
    </main>
  );
}
