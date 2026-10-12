import type { components } from '@/api/schema.gen';
import { isInstant } from '@/lib/dates';
import { lisbonDay, mondayOf } from '@/lib/lisbon';

type Schemas = components['schemas'];

/**
 * A session of the week as the screens use it. The generated type marks every field optional (the contract has no
 * `required` lists), so the response is read once, here, into a type where nothing is missing.
 */
export interface Session {
  id: string;
  groupName: string;
  startsAt: string;
  endsAt: string;
  /** When booking opens, as the API says; the client never works it out. */
  bookingOpensAt: string | undefined;
  capacity: number;
  freeSeats: number;
  waitlistSize: number;
  /** The member's own live booking: `CONFIRMED` or `WAITLISTED` (absent when they have none). */
  myStatus: string | undefined;
}

export function toSession(raw: Schemas['BookableSessionResponse']): Session | null {
  const { sessionId, groupName, startsAt, endsAt } = raw;
  if (!sessionId || groupName === undefined || !isInstant(startsAt) || !isInstant(endsAt)) {
    return null;
  }
  return {
    id: sessionId,
    groupName,
    startsAt,
    endsAt,
    bookingOpensAt: isInstant(raw.bookingOpensAt) ? raw.bookingOpensAt : undefined,
    capacity: raw.capacity ?? 0,
    freeSeats: raw.freeSeats ?? 0,
    waitlistSize: raw.waitlistSize ?? 0,
    myStatus: raw.myBookingStatus,
  };
}

/** The sessions of a response; rows that cannot be read are skipped, and a payload that is not a list is empty. */
export function toSessions(raw: unknown): Session[] {
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((row: unknown) =>
    typeof row === 'object' && row !== null ? (toSession(row) ?? []) : [],
  );
}

/** The sessions of a response grouped by Lisbon calendar day, in the order the API gave them. */
export function groupByDay(sessions: readonly Session[]): { day: string; sessions: Session[] }[] {
  const days = new Map<string, Session[]>();
  for (const session of sessions) {
    const day = lisbonDay(session.startsAt);
    days.set(day, [...(days.get(day) ?? []), session]);
  }
  return [...days].map(([day, inDay]) => ({ day, sessions: inDay }));
}

/**
 * The week (its Monday, `YYYY-MM-DD`) the address asks for. Weeks that are over (the API lists sessions that have not
 * started) or too far ahead, and anything that is not a date, give the first week instead.
 */
export function resolveWeek(asked: string | null, first: string, last: string): string {
  if (asked === null || !/^\d{4}-\d{2}-\d{2}$/.test(asked) || Number.isNaN(Date.parse(asked))) {
    return first;
  }
  const week = mondayOf(asked);
  return week < first || week > last ? first : week;
}
