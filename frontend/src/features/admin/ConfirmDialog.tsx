import type { ReactNode } from 'react';
import { ErrorAlert } from '@/components/ErrorAlert';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';

/**
 * A question with a safe answer: focus starts on the cancelling button, and the dialog stays open until the API has
 * answered, so a refusal is read where the question was asked. The owner of the mutation resets it when it opens the dialog.
 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  pendingLabel,
  cancelLabel = 'Not now',
  destructive = false,
  pending,
  error,
  onConfirm,
  onClose,
  onCloseAutoFocus,
}: {
  open: boolean;
  title: string;
  description: ReactNode;
  confirmLabel: string;
  pendingLabel: string;
  cancelLabel?: string;
  destructive?: boolean;
  pending: boolean;
  error: unknown;
  onConfirm: () => void;
  onClose: () => void;
  onCloseAutoFocus: (event: Event) => void;
}) {
  return (
    <AlertDialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
    >
      <AlertDialogContent onCloseAutoFocus={onCloseAutoFocus}>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>{description}</AlertDialogDescription>
        </AlertDialogHeader>

        {error != null && <ErrorAlert error={error} />}

        <AlertDialogFooter>
          <AlertDialogCancel className="h-11">{cancelLabel}</AlertDialogCancel>
          <AlertDialogAction
            variant={destructive ? 'destructive' : 'default'}
            className="h-11"
            disabled={pending}
            onClick={(event) => {
              // Stay open until the API has answered: a refusal is shown here, in the dialog.
              event.preventDefault();
              onConfirm();
            }}
          >
            {pending ? pendingLabel : confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
