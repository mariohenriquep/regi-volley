import { type SubmitEvent, useEffect, useRef, useState } from 'react';
import { ApiError } from '@/api/errors';
import { ErrorAlert } from '@/components/ErrorAlert';
import { TextField } from '@/components/form/TextField';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { dayHeading, formatTime, lisbonDay } from '@/lib/lisbon';
import type { CoachingSession } from './model';
import { type CapacityChanged, useChangeCapacity } from './useCoachingMutations';

/** The API's limits for a session's capacity (`ChangeCapacityRequest`). */
const MIN_SEATS = 1;
const MAX_SEATS = 1000;
const SEATS_INVALID = `Enter a whole number from ${String(MIN_SEATS)} to ${String(MAX_SEATS)}.`;

/**
 * Changes the seats of one session (US-12). Raising them promotes people from the waitlist, and the answer says how many.
 * Going below the confirmed is the API's refusal (422): its message is shown on the field.
 */
export function ChangeSeatsDialog({
  session,
  onClose,
  onChanged,
  onCloseAutoFocus,
}: {
  session: CoachingSession | null;
  onClose: () => void;
  onChanged: (outcome: CapacityChanged) => void;
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
        <SeatsSheet
          key={session.id}
          session={session}
          onClose={onClose}
          onChanged={onChanged}
          onCloseAutoFocus={onCloseAutoFocus}
        />
      )}
    </Dialog>
  );
}

function SeatsSheet({
  session,
  onClose,
  onChanged,
  onCloseAutoFocus,
}: {
  session: CoachingSession;
  onClose: () => void;
  onChanged: (outcome: CapacityChanged) => void;
  onCloseAutoFocus: (event: Event) => void;
}) {
  const change = useChangeCapacity();
  const [value, setValue] = useState(String(session.capacity));
  const [invalid, setInvalid] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  // The only field is the capacity: a 422 is a rule about it (below the confirmed), a 400 names it as malformed.
  const { error } = change;
  const apiError = error instanceof ApiError ? error : null;
  const apiFieldError =
    apiError?.status === 422
      ? apiError.message
      : apiError?.status === 400
        ? SEATS_INVALID
        : undefined;
  const fieldError = invalid ? SEATS_INVALID : apiFieldError;

  useEffect(() => {
    if (apiFieldError) input.current?.focus();
  }, [error, apiFieldError]);

  const submit = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    const seats = Number(value);
    if (value.trim() === '' || !Number.isInteger(seats) || seats < MIN_SEATS || seats > MAX_SEATS) {
      setInvalid(true);
      input.current?.focus();
      return;
    }
    change.mutate(
      { sessionId: session.id, capacity: seats },
      {
        onSuccess: (outcome) => {
          onChanged(outcome);
        },
      },
    );
  };

  const waiting =
    session.waitlistCount > 0 ? `${String(session.waitlistCount)} waiting` : 'nobody is waiting';

  return (
    <DialogContent onCloseAutoFocus={onCloseAutoFocus}>
      <form noValidate method="post" onSubmit={submit} className="grid gap-4">
        <DialogHeader>
          <DialogTitle>Change the seats</DialogTitle>
          <DialogDescription>
            <span className="block font-medium text-foreground">{session.groupName}</span>
            <span className="block">
              {dayHeading(lisbonDay(session.startsAt))}, {formatTime(session.startsAt)} –{' '}
              {formatTime(session.endsAt)}
            </span>
          </DialogDescription>
        </DialogHeader>

        <p className="text-sm">
          Raising the seats moves people up from the waitlist ({waiting}). {session.confirmedCount}{' '}
          confirmed now.
        </p>

        <TextField
          id="capacity"
          ref={input}
          label="Seats"
          type="number"
          inputMode="numeric"
          min={MIN_SEATS}
          max={MAX_SEATS}
          value={value}
          error={fieldError}
          onChange={(event) => {
            setValue(event.target.value);
            setInvalid(false);
          }}
        />

        {error && !apiFieldError && <ErrorAlert error={error} />}

        <DialogFooter>
          <Button type="button" variant="outline" size="lg" className="h-11" onClick={onClose}>
            Not now
          </Button>
          <Button type="submit" size="lg" className="h-11" disabled={change.isPending}>
            {change.isPending ? 'Saving…' : 'Save seats'}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  );
}
