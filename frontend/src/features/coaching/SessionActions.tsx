import { Link } from 'react-router';
import { Button } from '@/components/ui/button';
import type { CoachingSession } from './model';

const action = 'h-11 px-4 text-sm';

/** The buttons the API's flags allow. They are information only: each operation decides again when it is sent. */
export function SessionActions({
  session,
  label,
  onCancel,
  onChangeSeats,
  roster,
}: {
  session: CoachingSession;
  /** Names the session for a screen reader, after the visible text of each button. */
  label: string;
  onCancel: (session: CoachingSession, opener: HTMLElement) => void;
  onChangeSeats: (session: CoachingSession, opener: HTMLElement) => void;
  /** The card links to the detail page, which has this very screen already. */
  roster?: boolean;
}) {
  if (session.status === 'CANCELLED') return null;
  const { canMarkAttendance, canChangeCapacity, canCancel } = session;
  return (
    <div className="grid grid-cols-2 gap-2">
      {roster && (
        <Button asChild variant={canMarkAttendance ? 'default' : 'outline'} className={action}>
          <Link to={`/coaching/sessions/${session.id}`}>
            {canMarkAttendance ? 'Mark attendance' : 'Roster'}
            <span className="sr-only"> {label}</span>
          </Link>
        </Button>
      )}
      {canChangeCapacity && (
        <Button
          variant="outline"
          className={action}
          onClick={(event) => {
            onChangeSeats(session, event.currentTarget);
          }}
        >
          Change seats<span className="sr-only"> {label}</span>
        </Button>
      )}
      {canCancel && (
        <Button
          variant="destructive"
          className={action}
          onClick={(event) => {
            onCancel(session, event.currentTarget);
          }}
        >
          Cancel session<span className="sr-only"> {label}</span>
        </Button>
      )}
    </div>
  );
}
