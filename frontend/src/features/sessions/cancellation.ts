import { formatDateTime } from '@/lib/lisbon';

export interface CancellationAdvice {
  /** Cancelling now would not use up a session. */
  free: boolean;
  text: string;
}

/**
 * What the member is told before cancelling: free or late, from the deadline the API sends with the session. The API
 * stays the authority (its clock, not this device's, decides on submit), so the outcome it reports is shown afterwards.
 * Nothing is claimed for a confirmed booking whose deadline is missing.
 */
export function cancellationAdvice(
  status: string | undefined,
  freeCancellationUntil: string | undefined,
  now: number,
): CancellationAdvice | undefined {
  if (status === 'WAITLISTED') {
    return { free: true, text: 'Cancelling a place on the waitlist is always free.' };
  }
  if (freeCancellationUntil === undefined) return undefined;
  const until = formatDateTime(freeCancellationUntil);
  // The deadline itself is still free: "until" is the last instant at which it is.
  return now <= new Date(freeCancellationUntil).getTime()
    ? { free: true, text: `Cancelling now is free. You can cancel for free until ${until}.` }
    : {
        free: false,
        text: `Cancelling now counts as a used session (late). Free cancellation ended ${until}.`,
      };
}
