"use client";

import { useMemo, useState } from "react";
import { useFieldArray, useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  AlertTriangle,
  CheckCircle2,
  Plus,
  Save,
  Scale,
  Trash2,
} from "lucide-react";

const gradingTypes = ["Binary", "Soft"] as const;

type DeliverableWeight = {
  id: string;
  name: string;
  weight: number;
};

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

function mockPatchWeights(weights: DeliverableWeight[]): Promise<DeliverableWeight[]> {
  return new Promise((resolve) => {
    window.setTimeout(() => {
      resolve(weights);
    }, 500);
  });
}

function mockSaveRubric(payload: RubricFormValues): Promise<RubricFormValues> {
  return new Promise((resolve) => {
    window.setTimeout(() => {
      resolve(payload);
    }, 550);
  });
}

export default function EvaluationSetupPage() {
  const [deliverableWeights, setDeliverableWeights] = useState<DeliverableWeight[]>([
    { id: "proposal", name: "Proposal", weight: 30 },
    { id: "sow", name: "SoW", weight: 30 },
    { id: "demonstration", name: "Demonstration", weight: 40 },
  ]);
  const [weightsMessage, setWeightsMessage] = useState("");
  const [rubricMessage, setRubricMessage] = useState("");
  const [isSavingWeights, setIsSavingWeights] = useState(false);

  const totalWeight = useMemo(
    () => deliverableWeights.reduce((total, item) => total + item.weight, 0),
    [deliverableWeights]
  );

  const isWeightTotalValid = totalWeight === 100;

  const rubricForm = useForm<RubricFormValues>({
    resolver: zodResolver(rubricSchema),
    defaultValues: {
      deliverableId: "proposal",
      criteria: [
        {
          criterionName: "",
          weight: 0,
          gradingType: "Binary",
        },
      ],
    },
    mode: "onSubmit",
  });

  const rubricCriteria = useFieldArray({
    control: rubricForm.control,
    name: "criteria",
  });

  const handleWeightChange = (id: string, rawValue: string) => {
    setWeightsMessage("");
    const numericValue = Number(rawValue);
    const safeValue = Number.isFinite(numericValue)
      ? Math.min(100, Math.max(0, Math.trunc(numericValue)))
      : 0;

    setDeliverableWeights((previous) =>
      previous.map((item) =>
        item.id === id
          ? {
              ...item,
              weight: safeValue,
            }
          : item
      )
    );
  };

  const handleSaveWeights = async () => {
    if (!isWeightTotalValid) {
      return;
    }

    setWeightsMessage("");
    setIsSavingWeights(true);

    await mockPatchWeights(deliverableWeights);

    setIsSavingWeights(false);
    setWeightsMessage("Weights updated successfully");
  };

  const handleRubricSubmit = rubricForm.handleSubmit(async (values) => {
    setRubricMessage("");
    await mockSaveRubric(values);

    setRubricMessage("Rubric saved successfully");
    rubricForm.reset({
      deliverableId: values.deliverableId,
      criteria: [
        {
          criterionName: "",
          weight: 0,
          gradingType: "Binary",
        },
      ],
    });
  });

  return (
    <main className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 md:p-8">
      <div className="mx-auto w-full max-w-7xl space-y-6">
        <header className="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur">
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900 md:text-3xl">
            Evaluation Setup
          </h1>
          <p className="mt-2 text-sm text-slate-600">
            Configure deliverable-level weighting and detailed grading criteria.
          </p>
        </header>

        <section className="grid gap-6 lg:grid-cols-2">
          <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="flex items-center gap-2 text-lg font-semibold text-slate-900">
              <Scale className="h-5 w-5 text-slate-700" aria-hidden="true" />
              Deliverable Weights
            </h2>

            <div className="mt-4 space-y-4">
              {deliverableWeights.map((item) => (
                <label key={item.id} className="block space-y-1.5">
                  <span className="text-sm font-medium text-slate-700">{item.name}</span>
                  <div className="flex items-center gap-2">
                    <input
                      type="number"
                      inputMode="numeric"
                      min={0}
                      max={100}
                      value={item.weight}
                      onChange={(event) => handleWeightChange(item.id, event.target.value)}
                      className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-slate-500"
                    />
                    <span className="text-sm text-slate-500">%</span>
                  </div>
                </label>
              ))}

              <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                <p
                  className={`text-sm font-semibold ${
                    isWeightTotalValid ? "text-slate-900" : "text-red-700"
                  }`}
                >
                  Total: {totalWeight}%
                </p>
                {!isWeightTotalValid && (
                  <p className="mt-1 flex items-center gap-1 text-xs text-red-700">
                    <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />
                    Total must equal 100%
                  </p>
                )}
              </div>

              {weightsMessage && (
                <p className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                  <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                  {weightsMessage}
                </p>
              )}

              <button
                type="button"
                onClick={handleSaveWeights}
                disabled={!isWeightTotalValid || isSavingWeights}
                className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <Save className="h-4 w-4" aria-hidden="true" />
                {isSavingWeights ? "Saving..." : "Save Weights"}
              </button>
            </div>
          </article>

          <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-lg font-semibold text-slate-900">Rubric Builder</h2>

            <form onSubmit={handleRubricSubmit} className="mt-4 space-y-4" noValidate>
              <label className="block space-y-1.5">
                <span className="text-sm font-medium text-slate-700">Deliverable</span>
                <select
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-slate-500"
                  {...rubricForm.register("deliverableId")}
                >
                  {deliverableWeights.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </select>
                {rubricForm.formState.errors.deliverableId && (
                  <p className="text-xs text-red-600">
                    {rubricForm.formState.errors.deliverableId.message}
                  </p>
                )}
              </label>

              <div className="space-y-3">
                {rubricCriteria.fields.map((field, index) => (
                  <div
                    key={field.id}
                    className="rounded-xl border border-slate-200 bg-slate-50/70 p-3"
                  >
                    <div className="grid gap-3 md:grid-cols-[1fr_120px_130px_auto] md:items-end">
                      <label className="block space-y-1">
                        <span className="text-xs font-medium uppercase tracking-wide text-slate-500">
                          Criterion Name
                        </span>
                        <input
                          type="text"
                          placeholder="e.g. Problem Definition"
                          className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-slate-500"
                          {...rubricForm.register(`criteria.${index}.criterionName`)}
                        />
                        {rubricForm.formState.errors.criteria?.[index]?.criterionName && (
                          <p className="text-xs text-red-600">
                            {
                              rubricForm.formState.errors.criteria[index]?.criterionName
                                ?.message
                            }
                          </p>
                        )}
                      </label>

                      <label className="block space-y-1">
                        <span className="text-xs font-medium uppercase tracking-wide text-slate-500">
                          Weight
                        </span>
                        <input
                          type="number"
                          inputMode="numeric"
                          min={1}
                          max={100}
                          className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-slate-500"
                          {...rubricForm.register(`criteria.${index}.weight`, {
                            valueAsNumber: true,
                          })}
                        />
                        {rubricForm.formState.errors.criteria?.[index]?.weight && (
                          <p className="text-xs text-red-600">
                            {rubricForm.formState.errors.criteria[index]?.weight?.message}
                          </p>
                        )}
                      </label>

                      <label className="block space-y-1">
                        <span className="text-xs font-medium uppercase tracking-wide text-slate-500">
                          Grading Type
                        </span>
                        <select
                          className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-slate-500"
                          {...rubricForm.register(`criteria.${index}.gradingType`)}
                        >
                          {gradingTypes.map((gradingType) => (
                            <option key={gradingType} value={gradingType}>
                              {gradingType}
                            </option>
                          ))}
                        </select>
                        {rubricForm.formState.errors.criteria?.[index]?.gradingType && (
                          <p className="text-xs text-red-600">
                            {
                              rubricForm.formState.errors.criteria[index]?.gradingType
                                ?.message
                            }
                          </p>
                        )}
                      </label>

                      <button
                        type="button"
                        onClick={() => rubricCriteria.remove(index)}
                        disabled={rubricCriteria.fields.length === 1}
                        className="inline-flex h-10 items-center justify-center rounded-lg border border-slate-300 bg-white px-3 text-slate-700 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
                        aria-label="Remove criterion"
                      >
                        <Trash2 className="h-4 w-4" aria-hidden="true" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>

              {rubricForm.formState.errors.criteria?.message && (
                <p className="text-xs text-red-600">
                  {rubricForm.formState.errors.criteria.message}
                </p>
              )}

              <button
                type="button"
                onClick={() =>
                  rubricCriteria.append({
                    criterionName: "",
                    weight: 0,
                    gradingType: "Binary",
                  })
                }
                className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100"
              >
                <Plus className="h-4 w-4" aria-hidden="true" />
                Add Criterion
              </button>

              {rubricMessage && (
                <p className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                  <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                  {rubricMessage}
                </p>
              )}

              <button
                type="submit"
                disabled={rubricForm.formState.isSubmitting}
                className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <Save className="h-4 w-4" aria-hidden="true" />
                {rubricForm.formState.isSubmitting ? "Saving..." : "Save Rubric"}
              </button>
            </form>
          </article>
        </section>
      </div>
    </main>
  );
}
