import type { components } from '@/api/schema.gen';
import { isInstant } from '@/lib/dates';
import { addDays, dayHeading, formatTime, lisbonDay, mondayOf } from '@/lib/lisbon';

type Schemas = components['schemas'];

/** How far the week navigator goes: attendance is marked after the session, so staff look back as well as ahead. */
export const MAX_WEEKS_BACK = 12;
export const MAX_WEEKS_AHEAD = 8;

/**
 * A line of the staff's week as the screens use it. The generated type marks every field optional, so the response is
 * read once, here. The three `can...` flags are the API's own answer to "is this open right now"; they are information
 * only, the operations decide again on submit. A missing flag is `false`, so nothing is offered by accident.
 */
export interface CoachingSession {
  id: string;
  groupName: string;
  venueName: string | undefined;
  startsAt: string;
  endsAt: string;
  /** `SCHEDULED`, `COMPLETED` or `CANCELLED`; any other word is shown as the API wrote it. */
  status: string;
  capacity: number;
  /** Seat holders: confirmed, attended and no-show. */
  confirmedCount: number;
  waitlistCount: number;
  attendedCount: number;
  noShowCount: number;
  unmarkedCount: number;
  canMarkAttendance: boolean;
  canCancel: boolean;
  canChangeCapacity: boolean;
}

export function toCoachingSession(raw: Schemas['CoachingSessionResponse']): CoachingSession | null {
  const { sessionId, startsAt, endsAt } = raw;
  if (!sessionId || !isInstant(startsAt) || !isInstant(endsAt)) return null;
  return {
    id: sessionId,
    groupName: raw.groupName ?? 'Training session',
    venueName: raw.venueName,
    startsAt,
    endsAt,
    status: raw.status ?? '',
    capacity: raw.capacity ?? 0,
    confirmedCount: raw.confirmedCount ?? 0,
    waitlistCount: raw.waitlistCount ?? 0,
    attendedCount: raw.attendedCount ?? 0,
    noShowCount: raw.noShowCount ?? 0,
    unmarkedCount: raw.unmarkedCount ?? 0,
    canMarkAttendance: raw.canMarkAttendance === true,
    canCancel: raw.canCancel === true,
    canChangeCapacity: raw.canChangeCapacity === true,
  };
}

/** The lines of a response, in the order the API gave them; unreadable rows are skipped, a non-list is empty. */
export function toCoachingSessions(raw: unknown): CoachingSession[] {
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((row: unknown) =>
    typeof row === 'object' && row !== null ? (toCoachingSession(row) ?? []) : [],
  );
}

/**
 * "5 attended · 1 no-show · 2 to mark". Shown only when the API says attendance can be marked or something is marked
 * already, so the client never works out "has it started" itself. Never for a cancelled session.
 */
export function attendanceLine(session: CoachingSession): string | null {
  const { status, attendedCount, noShowCount, unmarkedCount, canMarkAttendance } = session;
  if (status === 'CANCELLED') return null;
  if (!canMarkAttendance && attendedCount + noShowCount === 0) return null;
  return `${String(attendedCount)} attended · ${String(noShowCount)} no-show · ${String(unmarkedCount)} to mark`;
}

/** The week (its Monday) the address asks for; anything unreadable or outside the navigator's range is this week. */
export function resolveCoachingWeek(asked: string | null, thisWeek: string): string {
  if (asked === null || !/^\d{4}-\d{2}-\d{2}$/.test(asked) || Number.isNaN(Date.parse(asked))) {
    return thisWeek;
  }
  const week = mondayOf(asked);
  const first = addDays(thisWeek, -7 * MAX_WEEKS_BACK);
  const last = addDays(thisWeek, 7 * MAX_WEEKS_AHEAD);
  return week < first || week > last ? thisWeek : week;
}

export interface RosterEntry {
  bookingId: string;
  name: string;
  /** `CONFIRMED`, `ATTENDED`, `NO_SHOW` or (waitlist) `WAITLISTED`; unknown words are shown as written. */
  status: string;
}

export interface Roster {
  startsAt: string;
  endsAt: string;
  status: string;
  capacity: number;
  /** Seat holders in the order they booked. */
  seats: RosterEntry[];
  /** Waiting members in promotion order. */
  waitlist: RosterEntry[];
}

function toEntries(raw: unknown): RosterEntry[] {
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((row: Schemas['RosterEntryView'] | null) =>
    row?.bookingId
      ? [{ bookingId: row.bookingId, name: row.memberName ?? '', status: row.status ?? '' }]
      : [],
  );
}

export function toRoster(raw: Schemas['SessionRosterResponse']): Roster | null {
  const { startsAt, endsAt } = raw;
  if (!isInstant(startsAt) || !isInstant(endsAt)) return null;
  return {
    startsAt,
    endsAt,
    status: raw.status ?? '',
    capacity: raw.capacity ?? 0,
    seats: toEntries(raw.seats),
    waitlist: toEntries(raw.waitlist),
  };
}

export function plural(count: number, one: string, many: string): string {
  return `${String(count)} ${count === 1 ? one : many}`;
}

/** The sentence a screen reader hears after a button: which session. */
export function sessionLabel({ groupName, startsAt }: CoachingSession): string {
  return `${groupName}, ${dayHeading(lisbonDay(startsAt))} at ${formatTime(startsAt)}`;
}
