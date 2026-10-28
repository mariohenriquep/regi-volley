import type { ReactNode } from 'react';
import { ErrorSummary } from '@/components/form/ErrorSummary';
import type { FormProblems } from '@/components/form/problems';
import { Button } from '@/components/ui/button';
import {
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

/**
 * The content of a form in a bottom sheet (a centred dialog from `sm` up): title, the error summary of the last attempt,
 * the fields, and *Cancel* / submit. It stays open until the API has answered, so a refusal is read next to the fields.
 * The owner renders it inside a `Dialog` only while it is open, so every opening starts with empty state.
 */
export function FormSheet({
  title,
  description,
  submitLabel,
  pendingLabel,
  pending,
  problems,
  labels,
  onSubmit,
  onClose,
  onCloseAutoFocus,
  children,
}: {
  title: string;
  description: ReactNode;
  submitLabel: string;
  pendingLabel: string;
  pending: boolean;
  problems: FormProblems | null;
  /** Control id to the label shown in the error summary. */
  labels: Record<string, string>;
  onSubmit: () => void;
  onClose: () => void;
  onCloseAutoFocus: (event: Event) => void;
  children: ReactNode;
}) {
  return (
    <DialogContent onCloseAutoFocus={onCloseAutoFocus}>
      <form
        noValidate
        method="post"
        className="grid gap-4"
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit();
        }}
      >
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>

        <ErrorSummary problems={problems} labels={labels} />
        {children}

        <DialogFooter>
          <Button type="button" variant="outline" size="lg" className="h-11" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" size="lg" className="h-11" disabled={pending}>
            {pending ? pendingLabel : submitLabel}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  );
}
