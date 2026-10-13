import { ArrowLeftIcon } from 'lucide-react';
import { useId, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ErrorAlert } from '@/components/ErrorAlert';
import { QueryState } from '@/components/QueryState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { dayHeading, formatTime, lisbonDay, mondayOf } from '@/lib/lisbon';
import { usePageTitle } from '@/lib/usePageTitle';
import { AttendanceToggle } from './AttendanceToggle';
import { attendanceNotice } from './messages';
import { type CoachingSession, type Roster, type RosterEntry, sessionLabel } from './model';
import { SessionActions } from './SessionActions';
import { SessionSummary } from './SessionSummary';
import { StatusBadge } from './StatusBadge';
import { useCoachingWeek, useRoster } from './useCoaching';
import { type Mark, useSaveAttendance } from './useCoachingMutations';
import { useStaffActions } from './useStaffActions';

/** Who holds a seat and who waits, and the attendance form (US-17); the cancel and seats actions of the session (US-11, US-12). */
export function SessionRosterPage() {
  usePageTitle('Roster');
  const { sessionId = '' } = useParams();
  const roster = useRoster(sessionId);

  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">Roster</h1>
      <QueryState query={roster} loadingLabel="Loading the roster">
        {(data) =>
          data ? <RosterBody sessionId={sessionId} roster={data} /> : <ErrorAlert error={null} />
        }
      </QueryState>
    </section>
  );
}

function RosterBody({ sessionId, roster }: { sessionId: string; roster: Roster }) {
  const titleId = useId();
  const actions = useStaffActions();
  // The flags and counts live on the staff's week; the roster tells which week that is.
  const week = mondayOf(lisbonDay(roster.startsAt));
  const weekQuery = useCoachingWeek(week);
  const line = weekQuery.data?.find((candidate) => candidate.id === sessionId);

  return (
    <>
      <Link
        to={`/coaching?week=${week}`}
        className="flex w-fit items-center gap-1 text-sm text-primary underline underline-offset-4"
      >
        <ArrowLeftIcon className="size-4" aria-hidden="true" />
        Back to Coaching
      </Link>

      {actions.notice}

      <section
        aria-labelledby={titleId}
        className="grid gap-3 rounded-xl bg-card p-4 text-card-foreground ring-1 ring-foreground/10"
      >
        {line ? (
          <>
            <SessionSummary session={line} titleId={titleId} heading="h2" />
            <SessionActions
              session={line}
              label={sessionLabel(line)}
              onCancel={actions.openCancel}
              onChangeSeats={actions.openChangeSeats}
            />
          </>
        ) : (
          <div className="flex items-start justify-between gap-2">
            <h2 id={titleId} className="text-base font-semibold">
              {dayHeading(lisbonDay(roster.startsAt))}, {formatTime(roster.startsAt)} –{' '}
              {formatTime(roster.endsAt)}
            </h2>
            <StatusBadge status={roster.status} />
          </div>
        )}
      </section>

      <Seats sessionId={sessionId} roster={roster} line={line} onSaved={actions.announce} />
      <Waitlist entries={roster.waitlist} />
      {actions.dialogs}
    </>
  );
}

const MARK_WORDS = new Map([
  ['CONFIRMED', 'Booked'],
  ['ATTENDED', 'Attended'],
  ['NO_SHOW', 'No-show'],
]);

function Seats({
  sessionId,
  roster,
  line,
  onSaved,
}: {
  sessionId: string;
  roster: Roster;
  line: CoachingSession | undefined;
  onSaved: (notice: { text: string; tone: 'info' }) => void;
}) {
  const save = useSaveAttendance(sessionId);
  const [chosen, setChosen] = useState<Record<string, Mark>>({});
  const canMark = line?.canMarkAttendance === true;
  const unmarked = roster.seats.filter((entry) => entry.status === 'CONFIRMED');
  // Only people still unmarked can be marked: a choice for someone the roster now shows as marked is dropped.
  const marks = Object.fromEntries(
    unmarked.flatMap(({ bookingId }) => {
      const mark = chosen[bookingId];
      return mark ? [[bookingId, mark] as const] : [];
    }),
  );
  const count = Object.keys(marks).length;

  const hint = !canMark ? attendanceHint(line, unmarked.length) : null;

  return (
    <section aria-labelledby="seats-heading" className="grid gap-3">
      <h2 id="seats-heading" className="text-lg font-semibold">
        Seats · {roster.seats.length} of {roster.capacity}
      </h2>
      {hint && <p className="text-sm text-muted-foreground">{hint}</p>}

      {canMark && unmarked.length > 0 && (
        <Button
          variant="outline"
          className="h-11"
          onClick={() => {
            setChosen(Object.fromEntries(unmarked.map(({ bookingId }) => [bookingId, 'ATTENDED'])));
          }}
        >
          Mark everyone as attended
        </Button>
      )}

      {roster.seats.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nobody holds a seat.</p>
      ) : (
        <ul aria-label="Seats" className="grid gap-2">
          {roster.seats.map((entry) => (
            <li
              key={entry.bookingId}
              className="rounded-xl bg-card p-3 text-card-foreground ring-1 ring-foreground/10"
            >
              {canMark && entry.status === 'CONFIRMED' ? (
                <AttendanceToggle
                  bookingId={entry.bookingId}
                  name={entry.name}
                  value={marks[entry.bookingId]}
                  onChange={(mark) => {
                    setChosen((current) => ({ ...current, [entry.bookingId]: mark }));
                  }}
                />
              ) : (
                <SeatLine entry={entry} />
              )}
            </li>
          ))}
        </ul>
      )}

      {save.error && <ErrorAlert error={save.error} />}

      {canMark && unmarked.length > 0 && (
        <div className="sticky bottom-[calc(4.5rem+env(safe-area-inset-bottom))] z-30 -mx-4 bg-background/95 px-4 py-3 shadow-[0_-1px_0_var(--border)]">
          <Button
            className="h-12 w-full text-base"
            disabled={count === 0 || save.isPending}
            onClick={() => {
              save.mutate(marks, {
                onSuccess: (saved) => {
                  setChosen({});
                  onSaved({ text: attendanceNotice(saved), tone: 'info' });
                },
              });
            }}
          >
            {save.isPending
              ? 'Saving…'
              : count === 0
                ? 'Save attendance'
                : `Save attendance (${String(count)})`}
          </Button>
        </div>
      )}
    </section>
  );
}

/**
 * Why there is nothing to mark, from what the API says: unmarked people but marking closed means the session has not
 * started; none left means attendance is done. The client reads no clock.
 */
function attendanceHint(line: CoachingSession | undefined, unmarked: number): string | null {
  if (!line || line.status === 'CANCELLED') return null;
  if (unmarked > 0) return 'Attendance can be marked once the session has started.';
  return line.attendedCount + line.noShowCount > 0 ? 'Attendance is complete.' : null;
}

function SeatLine({ entry }: { entry: RosterEntry }) {
  return (
    <div className="flex items-center justify-between gap-2">
      <span className="text-base font-medium">{entry.name}</span>
      <Badge
        variant={
          entry.status === 'ATTENDED'
            ? 'success'
            : entry.status === 'NO_SHOW'
              ? 'destructive'
              : 'secondary'
        }
      >
        {MARK_WORDS.get(entry.status) ?? entry.status}
      </Badge>
    </div>
  );
}

function Waitlist({ entries }: { entries: RosterEntry[] }) {
  if (entries.length === 0) return null;
  return (
    <section aria-labelledby="waitlist-heading" className="grid gap-3">
      <h2 id="waitlist-heading" className="text-lg font-semibold">
        Waitlist · {entries.length}
      </h2>
      <ol aria-label="Waitlist" className="grid gap-2">
        {entries.map((entry, index) => (
          <li
            key={entry.bookingId}
            className="flex items-center gap-3 rounded-xl bg-card p-3 text-card-foreground ring-1 ring-foreground/10"
          >
            <span aria-hidden="true" className="w-6 text-sm text-muted-foreground">
              {index + 1}
            </span>
            <span className="text-base font-medium">{entry.name}</span>
          </li>
        ))}
      </ol>
    </section>
  );
}
