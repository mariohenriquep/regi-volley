import type { components } from '@/api/schema.gen';
import { isInstant } from '@/lib/dates';

type Schemas = components['schemas'];

/** One past booking of the member, as `GET /api/v1/me/history` sends it. */
export interface HistoryEntry {
  bookingId: string;
  sessionId: string;
  startsAt: string;
  endsAt: string;
  status: string;
}

export interface History {
  entries: HistoryEntry[];
  noShowsThisMonth: number;
  noShowLimit: number;
  nearLimit: boolean;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/**
 * BACKEND DEFECT, and the one place the client does not trust the generated type: the history response carries
 * `MemberHistoryResponse.EntryView` (sessionId, trainingGroupId, startsAt, endsAt, bookingId, status), but springdoc
 * gives two nested records called `EntryView` the same schema name, and `docs/api/openapi.json` publishes the roster's
 * (bookingId, memberId, memberName, status). The generated `entries` items therefore have no `startsAt`. Until the
 * records get distinct schema names, entries are read from `unknown` and validated here; the fixture in
 * `test/fixtures.ts` is the real wire shape. When the contract is fixed this function can read the generated type.
 */
function toEntry(raw: unknown): HistoryEntry | null {
  if (!isRecord(raw)) return null;
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
  return { bookingId, sessionId, startsAt, endsAt, status };
}

export function toHistory(raw: Schemas['MemberHistoryResponse']): History {
  const entries: unknown = raw.entries;
  return {
    entries: Array.isArray(entries)
      ? entries.flatMap((entry: unknown) => toEntry(entry) ?? [])
      : [],
    noShowsThisMonth: raw.noShowsThisMonth ?? 0,
    noShowLimit: raw.noShowLimit ?? 0,
    nearLimit: raw.nearLimit ?? false,
  };
}
