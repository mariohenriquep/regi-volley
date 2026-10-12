import { useId, useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { durationText } from '@/features/association/schedule';
import { dayHeading, durationMinutes, formatDateTime, formatTime, lisbonDay } from '@/lib/lisbon';
import type { Session } from './model';

const action = 'h-11 min-w-28 px-4 text-sm';

/** The sentence a screen reader hears after "Book": which session. */
function sessionLabel({ groupName, startsAt }: Session): string {
  return `${groupName}, ${dayHeading(lisbonDay(startsAt))} at ${formatTime(startsAt)}`;
}

/**
 * One session of the week: what it is, how full, when booking opens (as the API says), the member's own status and the
 * one action that fits. Book is always offered when the member has no booking: the API decides whether it is allowed.
 */
export function SessionCard({
  session,
  bookingId,
  onBook,
  onCancel,
}: {
  session: Session;
  /** The member's booking id, when this app learned it (see `bookingMemory.ts`). */
  bookingId: string | undefined;
  onBook: (session: Session, opener: HTMLElement) => void;
  onCancel: (session: Session, bookingId: string, opener: HTMLElement) => void;
}) {
  const titleId = useId();
  const [now] = useState(() => Date.now());
  const { groupName, startsAt, endsAt, capacity, freeSeats, waitlistSize, myStatus } = session;
  const opensLater =
    session.bookingOpensAt !== undefined && new Date(session.bookingOpensAt).getTime() > now;
  const label = sessionLabel(session);

  return (
    <article
      aria-labelledby={titleId}
      className="grid gap-3 rounded-xl bg-card p-4 text-card-foreground ring-1 ring-foreground/10"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="grid gap-0.5">
          <h3 id={titleId} className="text-base font-semibold">
            {groupName}
          </h3>
          <p className="text-sm">
            <span className="font-medium">
              {formatTime(startsAt)} – {formatTime(endsAt)}
            </span>{' '}
            <span className="text-muted-foreground">
              {durationText(durationMinutes(startsAt, endsAt))}
            </span>
          </p>
        </div>
        {myStatus === 'CONFIRMED' && <Badge variant="success">You&apos;re confirmed</Badge>}
        {myStatus === 'WAITLISTED' && <Badge variant="warning">You&apos;re on the waitlist</Badge>}
        {myStatus === undefined && freeSeats === 0 && <Badge variant="secondary">Full</Badge>}
      </div>

      <div className="flex flex-wrap gap-x-4 text-sm text-muted-foreground">
        <p>
          {freeSeats} of {capacity} seats free
        </p>
        {waitlistSize > 0 && <p>{waitlistSize} on the waitlist</p>}
      </div>
      {opensLater && session.bookingOpensAt !== undefined && (
        <p className="text-sm">Booking opens {formatDateTime(session.bookingOpensAt)}</p>
      )}

      {myStatus === undefined && (
        <Button
          className={action}
          onClick={(event) => {
            onBook(session, event.currentTarget);
          }}
        >
          Book<span className="sr-only"> {label}</span>
        </Button>
      )}
      {myStatus !== undefined && bookingId !== undefined && (
        <Button
          variant="outline"
          className={action}
          onClick={(event) => {
            onCancel(session, bookingId, event.currentTarget);
          }}
        >
          Cancel<span className="sr-only"> {label}</span>
        </Button>
      )}
      {myStatus !== undefined && bookingId === undefined && (
        <p className="text-sm text-muted-foreground">
          Cancelling isn&apos;t available for this booking in the app yet.
        </p>
      )}
    </article>
  );
}
