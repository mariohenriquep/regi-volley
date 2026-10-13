import { useId } from 'react';
import { type CoachingSession, sessionLabel } from './model';
import { SessionActions } from './SessionActions';
import { SessionSummary } from './SessionSummary';

/** One session of the staff's week: the summary and the actions the API's flags allow. */
export function CoachingCard({
  session,
  onCancel,
  onChangeSeats,
}: {
  session: CoachingSession;
  onCancel: (session: CoachingSession, opener: HTMLElement) => void;
  onChangeSeats: (session: CoachingSession, opener: HTMLElement) => void;
}) {
  const titleId = useId();
  return (
    <article
      aria-labelledby={titleId}
      className="grid gap-3 rounded-xl bg-card p-4 text-card-foreground ring-1 ring-foreground/10"
    >
      <SessionSummary session={session} titleId={titleId} />
      <SessionActions
        session={session}
        label={sessionLabel(session)}
        onCancel={onCancel}
        onChangeSeats={onChangeSeats}
        roster
      />
    </article>
  );
}
