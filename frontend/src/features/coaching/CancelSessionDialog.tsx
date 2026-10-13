import { type SubmitEvent, useEffect, useRef, useState } from 'react';
import { ApiError } from '@/api/errors';
import { ErrorAlert } from '@/components/ErrorAlert';
import { FIELD_NOT_VALID } from '@/components/form/problems';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { dayHeading, formatTime, lisbonDay } from '@/lib/lisbon';
import { cancellationWarning } from './messages';
import type { CoachingSession } from './model';
import { type SessionCancelled, useCancelSession } from './useCoachingMutations';

/** The API's limit for a cancellation reason. */
const MAX_REASON = 500;
const REASON_REQUIRED = 'Enter the reason the members will be told.';

/**
 * Cancels a whole session (US-11). The reason is required and is what the members are told; the warning uses the counts
 * of the line on screen. The dialog stays open until the API has answered, and shows a refusal where it happened.
 */
export function CancelSessionDialog({
  session,
  onClose,
  onCancelled,
  onCloseAutoFocus,
}: {
  session: CoachingSession | null;
  onClose: () => void;
  onCancelled: (outcome: SessionCancelled) => void;
  onCloseAutoFocus: (event: Event) => void;
}) {
  return (
    <Dialog
      open={session !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      {session && (
        <CancelSheet
          key={session.id}
          session={session}
          onClose={onClose}
          onCancelled={onCancelled}
          onCloseAutoFocus={onCloseAutoFocus}
        />
      )}
    </Dialog>
  );
}

function CancelSheet({
  session,
  onClose,
  onCancelled,
  onCloseAutoFocus,
}: {
  session: CoachingSession;
  onClose: () => void;
  onCancelled: (outcome: SessionCancelled) => void;
  onCloseAutoFocus: (event: Event) => void;
}) {
  const cancel = useCancelSession();
  const [reason, setReason] = useState('');
  const [missing, setMissing] = useState(false);
  const field = useRef<HTMLTextAreaElement>(null);

  // The API names the reason itself (a 400 or 422 with `fields`): that is a field problem; anything else is the dialog's.
  const apiError = cancel.error instanceof ApiError ? cancel.error : null;
  const apiNamesReason = apiError?.fields.some((name) => name.toLowerCase().includes('reason'));
  const fieldError = missing
    ? REASON_REQUIRED
    : apiNamesReason
      ? apiError?.status === 422
        ? apiError.message
        : FIELD_NOT_VALID
      : undefined;

  const { error } = cancel;
  useEffect(() => {
    if (error && apiNamesReason) field.current?.focus();
  }, [error, apiNamesReason]);

  const submit = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmed = reason.trim();
    if (trimmed === '') {
      setMissing(true);
      field.current?.focus();
      return;
    }
    cancel.mutate(
      { sessionId: session.id, reason: trimmed },
      {
        onSuccess: (outcome) => {
          onCancelled(outcome);
        },
      },
    );
  };

  return (
    <DialogContent onCloseAutoFocus={onCloseAutoFocus}>
      <form noValidate method="post" onSubmit={submit} className="grid gap-4">
        <DialogHeader>
          <DialogTitle>Cancel this session?</DialogTitle>
          <DialogDescription>
            <span className="block font-medium text-foreground">{session.groupName}</span>
            <span className="block">
              {dayHeading(lisbonDay(session.startsAt))}, {formatTime(session.startsAt)} –{' '}
              {formatTime(session.endsAt)}
            </span>
          </DialogDescription>
        </DialogHeader>

        <p className="text-sm font-medium">
          {cancellationWarning(session.confirmedCount + session.waitlistCount)}
        </p>

        <div className="grid gap-2">
          <Label htmlFor="cancel-reason">Reason</Label>
          <Textarea
            id="cancel-reason"
            ref={field}
            value={reason}
            maxLength={MAX_REASON}
            aria-invalid={fieldError ? true : undefined}
            aria-describedby={fieldError ? 'cancel-reason-error' : undefined}
            onChange={(event) => {
              setReason(event.target.value);
              setMissing(false);
            }}
          />
          {fieldError && (
            <p id="cancel-reason-error" className="text-sm text-destructive">
              {fieldError}
            </p>
          )}
        </div>

        {error && !apiNamesReason && <ErrorAlert error={error} />}

        <DialogFooter>
          <Button type="button" variant="outline" size="lg" className="h-11" onClick={onClose}>
            Keep session
          </Button>
          <Button
            type="submit"
            variant="destructive"
            size="lg"
            className="h-11"
            disabled={cancel.isPending}
          >
            {cancel.isPending ? 'Cancelling…' : 'Cancel session'}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  );
}
