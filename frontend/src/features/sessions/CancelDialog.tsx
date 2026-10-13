import { useState } from 'react';
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
import { dayHeading, formatTime, lisbonDay } from '@/lib/lisbon';
import { adviceFor } from './bookingAdvice';
import { cancellationAdvice } from './cancellation';
import type { Session } from './model';
import { type Cancelled, useCancelBooking } from './useSessionMutations';

export interface CancelTarget {
  session: Session;
  bookingId: string;
}

export function CancelDialog({
  target,
  onClose,
  onCancelled,
  onCloseAutoFocus,
}: {
  target: CancelTarget | null;
  onClose: () => void;
  onCancelled: (outcome: Cancelled) => void;
  onCloseAutoFocus: (event: Event) => void;
}) {
  return (
    <AlertDialog
      open={target !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      {target && (
        <CancelSheet
          key={target.bookingId}
          target={target}
          onCancelled={onCancelled}
          onCloseAutoFocus={onCloseAutoFocus}
        />
      )}
    </AlertDialog>
  );
}

function CancelSheet({
  target,
  onCancelled,
  onCloseAutoFocus,
}: {
  target: CancelTarget;
  onCancelled: (outcome: Cancelled) => void;
  onCloseAutoFocus: (event: Event) => void;
}) {
  const { session, bookingId } = target;
  const cancel = useCancelBooking();
  // Taken once, when the dialog opens: the text must not change under the member's thumb.
  const [now] = useState(() => Date.now());
  const outlook = cancellationAdvice(session.myStatus, session.freeCancellationUntil, now);
  const advice = adviceFor(cancel.error);
  return (
    <AlertDialogContent onCloseAutoFocus={onCloseAutoFocus}>
      <AlertDialogHeader>
        <AlertDialogTitle>Cancel your booking?</AlertDialogTitle>
        <AlertDialogDescription>
          <span className="block font-medium text-foreground">{session.groupName}</span>
          <span className="block">
            {dayHeading(lisbonDay(session.startsAt))}, {formatTime(session.startsAt)} –{' '}
            {formatTime(session.endsAt)}
          </span>
        </AlertDialogDescription>
      </AlertDialogHeader>

      <div className="grid gap-2 text-sm">
        {outlook && <p>{outlook.text}</p>}
        {cancel.error && (
          <>
            <ErrorAlert error={cancel.error} />
            {advice && <p>{advice.text}</p>}
          </>
        )}
      </div>

      <AlertDialogFooter>
        <AlertDialogCancel className="h-11">Keep booking</AlertDialogCancel>
        <AlertDialogAction
          variant="destructive"
          className="h-11"
          disabled={cancel.isPending}
          onClick={(event) => {
            // Stay open until the API has answered: a refusal is shown here, in the dialog.
            event.preventDefault();
            cancel.mutate(
              { sessionId: session.id, bookingId },
              {
                onSuccess: (outcome) => {
                  onCancelled(outcome);
                },
              },
            );
          }}
        >
          {cancel.isPending ? 'Cancelling…' : 'Cancel booking'}
        </AlertDialogAction>
      </AlertDialogFooter>
    </AlertDialogContent>
  );
}
