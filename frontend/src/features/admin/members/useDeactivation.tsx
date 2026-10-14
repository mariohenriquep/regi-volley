import { Button } from '@/components/ui/button';
import { plural } from '@/features/coaching/model';
import type { AdminNotice } from '../useAdminNotice';
import { errorText } from './errorText';
import type { MemberDetail } from './model';
import { type Deactivated, useDeactivateMember } from './useMemberMutations';

/** The outcome in the API's numbers. A session whose booking could not be cancelled is said, not hidden. */
function outcomeText(name: string, { bookingsCancelled, failedSessions }: Deactivated): string {
  const cancelled =
    bookingsCancelled === 0
      ? 'No future bookings had to be cancelled.'
      : `${plural(bookingsCancelled, 'future booking was', 'future bookings were')} cancelled.`;
  if (failedSessions === 0) return `${name} was deactivated. ${cancelled}`;
  return `${name} was deactivated. ${cancelled} The booking in ${plural(failedSessions, 'session', 'sessions')} could not be cancelled.`;
}

/**
 * Runs the deactivation and reports it. The mutation lives where this hook is used (the page body, which stays mounted when
 * the member turns inactive), because a notice offering "Cancel the remaining bookings" calls it again after the section
 * with the button has gone: repeating the call retries exactly the sessions that failed.
 */
export function useDeactivation(member: MemberDetail, notice: AdminNotice) {
  const deactivate = useDeactivateMember(member.id);

  const run = (onDone?: () => void) => {
    deactivate.mutate(undefined, {
      onSuccess: (outcome) => {
        onDone?.();
        const text = outcomeText(member.name, outcome);
        if (outcome.failedSessions === 0) {
          notice.announce({ text });
          return;
        }
        notice.announce({
          tone: 'warning',
          text: (
            <>
              {text}{' '}
              <Button
                variant="outline"
                size="sm"
                className="h-9"
                onClick={() => {
                  run();
                }}
              >
                Cancel the remaining bookings
              </Button>
            </>
          ),
        });
      },
      onError: (error) => {
        // Only reached by a retry from the notice: the first attempt's refusal is read in its dialog (which is still open).
        if (!onDone) notice.announce({ tone: 'warning', text: errorText(error) });
      },
    });
  };

  return { deactivate, run };
}
