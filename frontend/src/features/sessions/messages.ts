import type { Cancelled, Placed } from './useSessionMutations';

/** What the server decided about a booking, in words. Nothing is claimed before the server has answered. */
export function bookedNotice({ status, waitlistPosition }: Placed): string {
  if (status === 'WAITLISTED') {
    return waitlistPosition === undefined
      ? "The session was full. You're on the waitlist."
      : `The session was full. You're number ${String(waitlistPosition)} on the waitlist.`;
  }
  return "You're booked and confirmed.";
}

/** What the server decided about a cancellation: late or free, credit back or not, and who moved up. */
export function cancelledNotice({ late, creditRefunded, promotedCount }: Cancelled): string {
  const outcome = late
    ? 'Booking cancelled late, so it counts as a used session.'
    : creditRefunded
      ? 'Booking cancelled. It was free and your credit was given back.'
      : 'Booking cancelled. It was free.';
  if (promotedCount === 0) return outcome;
  return promotedCount === 1
    ? `${outcome} Someone on the waitlist took your seat.`
    : `${outcome} ${String(promotedCount)} people on the waitlist moved up.`;
}
