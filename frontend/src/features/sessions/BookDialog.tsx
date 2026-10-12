import { useRef } from 'react';
import { Link } from 'react-router';
import { ErrorAlert } from '@/components/ErrorAlert';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { creditNote } from '@/features/plan/model';
import { usePlan } from '@/features/plan/usePlan';
import { dayHeading, formatTime, lisbonDay } from '@/lib/lisbon';
import { adviceFor } from './bookingAdvice';
import type { Session } from './model';
import { type Placed, useBookSession } from './useSessionMutations';

/**
 * Tap 2 of the two-tap booking. A single tap was rejected on purpose: a list that scrolls under a thumb books by
 * accident, a late cancellation costs a credit, and this sheet is where the member learns what a credit does. Two taps is
 * the goal, and this is the second.
 */
export function BookDialog({
  session,
  onClose,
  onBooked,
  onCloseAutoFocus,
}: {
  session: Session | null;
  onClose: () => void;
  onBooked: (placed: Placed) => void;
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
        <BookSheet
          key={session.id}
          session={session}
          onClose={onClose}
          onBooked={onBooked}
          onCloseAutoFocus={onCloseAutoFocus}
        />
      )}
    </Dialog>
  );
}

function BookSheet({
  session,
  onClose,
  onBooked,
  onCloseAutoFocus,
}: {
  session: Session;
  onClose: () => void;
  onBooked: (placed: Placed) => void;
  onCloseAutoFocus: (event: Event) => void;
}) {
  const confirm = useRef<HTMLButtonElement>(null);
  const book = useBookSession();
  const plan = usePlan();
  const full = session.freeSeats === 0;
  const credit = plan.data
    ? creditNote(plan.data.subscriptions, lisbonDay(session.startsAt), full)
    : null;
  const advice = adviceFor(book.error);

  return (
    <DialogContent
      onOpenAutoFocus={(event) => {
        event.preventDefault();
        confirm.current?.focus();
      }}
      onCloseAutoFocus={onCloseAutoFocus}
    >
      <DialogHeader>
        <DialogTitle>Book this session?</DialogTitle>
        <DialogDescription>
          <span className="block font-medium text-foreground">{session.groupName}</span>
          <span className="block">
            {dayHeading(lisbonDay(session.startsAt))}, {formatTime(session.startsAt)} –{' '}
            {formatTime(session.endsAt)}
          </span>
        </DialogDescription>
      </DialogHeader>

      <div className="grid gap-2 text-sm">
        {full && (
          <p>This session is full. You&apos;ll join the waitlist and move up if a seat frees up.</p>
        )}
        {credit && <p>{credit}</p>}
        {book.error && (
          <>
            <ErrorAlert error={book.error} />
            {advice && (
              <p>
                {advice.text}{' '}
                {advice.planLink && (
                  <Link to="/me/plan" className="text-primary underline underline-offset-4">
                    See my plan
                  </Link>
                )}
              </p>
            )}
          </>
        )}
      </div>

      <DialogFooter>
        <Button variant="outline" size="lg" className="h-11" onClick={onClose}>
          Not now
        </Button>
        <Button
          ref={confirm}
          size="lg"
          className="h-11"
          disabled={book.isPending}
          onClick={() => {
            book.mutate(session.id, {
              onSuccess: (placed) => {
                onBooked(placed);
              },
            });
          }}
        >
          {book.isPending ? 'Booking…' : 'Confirm booking'}
        </Button>
      </DialogFooter>
    </DialogContent>
  );
}
