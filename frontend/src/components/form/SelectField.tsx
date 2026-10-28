import type { ComponentProps } from 'react';
import { Label } from '@/components/ui/label';
import { cn } from '@/lib/utils';

export interface SelectFieldProps extends Omit<ComponentProps<'select'>, 'id'> {
  id: string;
  label: string;
  /** Help shown under the control and read with it. */
  hint?: string;
  /** The error for this control: read with the select, which is flagged invalid. */
  error?: string;
}

/**
 * A labelled native `<select>`: the platform's own picker is the most accessible and the easiest to use on a phone. Styled
 * like `Input` so the two sit side by side.
 */
export function SelectField({
  id,
  label,
  hint,
  error,
  className,
  children,
  ...selectProps
}: SelectFieldProps) {
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ');
  return (
    <div className="grid gap-2">
      <Label htmlFor={id}>{label}</Label>
      <select
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        className={cn(
          'h-10 w-full min-w-0 rounded-lg border border-input bg-background px-2.5 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive md:text-sm',
          className,
        )}
        {...selectProps}
      >
        {children}
      </select>
      {hint && (
        <p id={hintId} className="text-sm text-muted-foreground">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
