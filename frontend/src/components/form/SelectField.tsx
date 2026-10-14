import type { ComponentProps } from 'react';
import { Label } from '@/components/ui/label';
import { cn } from '@/lib/utils';

export interface SelectFieldProps extends Omit<ComponentProps<'select'>, 'id'> {
  id: string;
  label: string;
  /** Help shown under the control and read with it. */
  hint?: string;
}

/**
 * A labelled native `<select>`: the platform's own picker is the most accessible and the easiest to use on a phone. Styled
 * like `Input` so the two sit side by side.
 */
export function SelectField({
  id,
  label,
  hint,
  className,
  children,
  ...selectProps
}: SelectFieldProps) {
  const hintId = `${id}-hint`;
  return (
    <div className="grid gap-2">
      <Label htmlFor={id}>{label}</Label>
      <select
        id={id}
        aria-describedby={hint ? hintId : undefined}
        className={cn(
          'h-10 w-full min-w-0 rounded-lg border border-input bg-background px-2.5 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 md:text-sm',
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
    </div>
  );
}
