import type { ComponentProps } from 'react';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

export interface TextFieldProps extends Omit<ComponentProps<typeof Input>, 'id'> {
  id: string;
  label: string;
  /** Help shown under the input and read with it. */
  hint?: string;
  /** The error for this control: read with the input, which is flagged invalid. */
  error?: string;
}

/** A labelled input whose hint and error are tied to it (`aria-describedby`, `aria-invalid`). */
export function TextField({ id, label, hint, error, ...inputProps }: TextFieldProps) {
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ');
  return (
    <div className="grid gap-2">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        className="h-10"
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        {...inputProps}
      />
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
