import { useEffect, useRef } from 'react';
import type { FormProblems } from './problems';

/**
 * The error summary of a form: it takes keyboard focus whenever a new set of problems arrives (screen readers read it,
 * keyboard users start from it), lists the controls at fault, each a button that moves focus to its control, and shows
 * the general messages and the request id for support.
 */
export function ErrorSummary({
  problems,
  labels,
}: {
  problems: FormProblems | null;
  /** Control id to the label shown in the list. */
  labels: Record<string, string>;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (problems) ref.current?.focus();
  }, [problems]);
  if (!problems) return null;

  const fields = Object.entries(problems.fields);
  return (
    <div
      ref={ref}
      role="alert"
      tabIndex={-1}
      className="grid gap-2 rounded-lg border border-destructive p-3 text-sm text-destructive outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      {problems.general.map((message, index) => (
        // Two messages can be the same text, so the position is part of the key.
        <p key={`${String(index)}-${message}`}>{message}</p>
      ))}
      {fields.length > 0 && (
        <>
          <p className="font-medium">Please check the following:</p>
          <ul className="grid gap-1">
            {fields.map(([id, message]) => (
              <li key={id}>
                <button
                  type="button"
                  className="text-left underline underline-offset-4"
                  onClick={() => {
                    document.getElementById(id)?.focus();
                  }}
                >
                  {labels[id] ?? id}: {message}
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
      {problems.requestId && <p className="text-xs">Reference: {problems.requestId}</p>}
    </div>
  );
}
