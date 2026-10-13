import { durationText } from '@/features/association/schedule';
import { durationMinutes, formatTime } from '@/lib/lisbon';
import { attendanceLine, type CoachingSession } from './model';
import { StatusBadge } from './StatusBadge';

/** What a coach wants at a glance: group, time, venue, status, seats, waitlist and attendance. */
export function SessionSummary({
  session,
  titleId,
  heading: Heading = 'h3',
}: {
  session: CoachingSession;
  titleId: string;
  heading?: 'h2' | 'h3';
}) {
  const {
    groupName,
    venueName,
    startsAt,
    endsAt,
    status,
    capacity,
    confirmedCount,
    waitlistCount,
  } = session;
  const attendance = attendanceLine(session);
  return (
    <>
      <div className="flex items-start justify-between gap-2">
        <div className="grid gap-0.5">
          <Heading id={titleId} className="text-base font-semibold">
            {groupName}
          </Heading>
          <p className="text-sm">
            <span className="font-medium">
              {formatTime(startsAt)} – {formatTime(endsAt)}
            </span>{' '}
            <span className="text-muted-foreground">
              {durationText(durationMinutes(startsAt, endsAt))}
            </span>
          </p>
          {venueName && <p className="text-sm text-muted-foreground">{venueName}</p>}
        </div>
        <StatusBadge status={status} />
      </div>
      {status !== 'CANCELLED' && (
        <div className="flex flex-wrap gap-x-4 text-sm text-muted-foreground">
          <p>
            {confirmedCount} of {capacity} confirmed
          </p>
          {waitlistCount > 0 && <p>{waitlistCount} on the waitlist</p>}
        </div>
      )}
      {attendance && <p className="text-sm font-medium">{attendance}</p>}
    </>
  );
}
