import type { components } from '@/api/schema.gen';
import { isInstant } from '@/lib/dates';

type Schemas = components['schemas'];

/** One past booking of the member, as `GET /api/v1/me/history` sends it (`HistoryEntryView`), read into non-optional ids and dates. */
export interface HistoryEntry {
  bookingId: string;
  sessionId: string;
  startsAt: string;
  endsAt: string;
  status: string;
  trainingGroupName: string | undefined;
  venueName: string | undefined;
}

export interface History {
  entries: HistoryEntry[];
  noShowsThisMonth: number;
  noShowLimit: number;
  nearLimit: boolean;
}

/** An entry without an id, a status or readable dates would show "Invalid Date" or throw in `Intl`: it is skipped. */
function toEntry(raw: Schemas['HistoryEntryView'] | null): HistoryEntry | null {
  if (typeof raw !== 'object' || raw === null) return null;
  const { bookingId, sessionId, startsAt, endsAt, status } = raw;
  if (
    typeof bookingId !== 'string' ||
    typeof sessionId !== 'string' ||
    !isInstant(startsAt) ||
    !isInstant(endsAt) ||
    typeof status !== 'string'
  ) {
    return null;
  }
  return {
    bookingId,
    sessionId,
    startsAt,
    endsAt,
    status,
    trainingGroupName: raw.trainingGroupName,
    venueName: raw.venueName,
  };
}

export function toHistory(raw: Schemas['MemberHistoryResponse']): History {
  const entries: unknown = raw.entries;
  return {
    entries: Array.isArray(entries)
      ? entries.flatMap((entry: Schemas['HistoryEntryView'] | null) => toEntry(entry) ?? [])
      : [],
    noShowsThisMonth: raw.noShowsThisMonth ?? 0,
    noShowLimit: raw.noShowLimit ?? 0,
    nearLimit: raw.nearLimit ?? false,
  };
}
