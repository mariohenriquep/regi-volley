import type { components } from '@/api/schema.gen';
import { type Role, toRoles } from '@/auth/useMe';
import { isCalendarDay, isInstant } from '@/lib/dates';

type Schemas = components['schemas'];

/** The longest search text the API accepts (`ListMembers`). */
export const MAX_SEARCH = 100;

/** The statuses the list can be narrowed to (`status` of `GET /members`). */
export const STATUS_FILTERS = ['ACTIVE', 'INACTIVE'] as const;
export type StatusFilter = (typeof STATUS_FILTERS)[number];

/** The filter an address asks for; anything else (or nothing) is "all", so a hand-edited address cannot send a word the API refuses. */
export function resolveStatus(asked: string | null): StatusFilter | undefined {
  return STATUS_FILTERS.find((status) => status === asked);
}

/** A row of the member list. The generated type marks every field optional, so the answer is read once, here. */
export interface MemberRow {
  id: string;
  name: string;
  email: string | undefined;
  levelName: string | undefined;
  roles: Role[];
  /** `ACTIVE` or `INACTIVE`; any other word is shown as the API wrote it. */
  status: string;
  /** `undefined` when the API does not say: nothing is claimed then. */
  accountActivated: boolean | undefined;
}

export interface MemberListing {
  members: MemberRow[];
  truncated: boolean;
}

export function toMemberRow(raw: Schemas['MemberListEntryResponse']): MemberRow | null {
  if (!raw.memberId) return null;
  return {
    id: raw.memberId,
    name: raw.name ?? '',
    email: raw.email,
    levelName: raw.levelName,
    roles: toRoles(raw.roles),
    status: raw.status ?? '',
    accountActivated: typeof raw.accountActivated === 'boolean' ? raw.accountActivated : undefined,
  };
}

export function toMemberListing(raw: Schemas['MemberListResponse']): MemberListing {
  const rows: unknown = raw.members;
  return {
    members: Array.isArray(rows)
      ? rows.flatMap((row: unknown) =>
          typeof row === 'object' && row !== null ? (toMemberRow(row) ?? []) : [],
        )
      : [],
    truncated: raw.truncated === true,
  };
}

export interface LevelChange {
  changedAt: string;
  by: string | undefined;
  from: string | undefined;
  to: string | undefined;
}

export interface MemberSubscription {
  id: string;
  planName: string;
  type: string;
  startDate: string | undefined;
  endDate: string | undefined;
  paymentStatus: string;
  priceCents: number | undefined;
  paidCents: number | undefined;
  creditsLeft: number | undefined;
}

/** One member in full. For an anonymised member the contact data is left out, whatever the API sent. */
export interface MemberDetail {
  id: string;
  name: string;
  anonymised: boolean;
  email: string | undefined;
  phone: string | undefined;
  status: string;
  accountActivated: boolean | undefined;
  joinedAt: string | undefined;
  levelId: string | undefined;
  levelName: string | undefined;
  roles: Role[];
  /** Newest first. */
  levelChanges: LevelChange[];
  subscriptions: MemberSubscription[];
  subscriptionsTruncated: boolean;
}

function list<T>(raw: unknown, read: (row: Record<string, unknown>) => T | null): T[] {
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((row: unknown) =>
    typeof row === 'object' && row !== null ? (read(row as Record<string, unknown>) ?? []) : [],
  );
}

function toLevelChange(raw: Schemas['LevelChangeView']): LevelChange | null {
  if (!isInstant(raw.changedAt)) return null;
  return {
    changedAt: raw.changedAt,
    by: raw.changedByName,
    from: raw.fromLevelName,
    to: raw.toLevelName,
  };
}

function toSubscription(raw: Schemas['MemberSubscriptionView']): MemberSubscription | null {
  if (!raw.subscriptionId) return null;
  return {
    id: raw.subscriptionId,
    planName: raw.planName ?? 'Plan',
    type: raw.type ?? '',
    startDate: isCalendarDay(raw.startDate) ? raw.startDate : undefined,
    endDate: isCalendarDay(raw.endDate) ? raw.endDate : undefined,
    paymentStatus: raw.paymentStatus ?? '',
    priceCents: raw.priceCents,
    paidCents: raw.paidCents,
    creditsLeft: raw.creditsLeft,
  };
}

export function toMemberDetail(raw: Schemas['MemberDetailResponse']): MemberDetail | null {
  if (!raw.memberId) return null;
  const anonymised = raw.anonymised === true;
  return {
    id: raw.memberId,
    name: raw.name ?? '',
    anonymised,
    email: anonymised ? undefined : raw.email,
    phone: anonymised ? undefined : raw.phone,
    status: raw.status ?? '',
    accountActivated: typeof raw.accountActivated === 'boolean' ? raw.accountActivated : undefined,
    joinedAt: isInstant(raw.joinedAt) ? raw.joinedAt : undefined,
    levelId: raw.levelId,
    levelName: raw.levelName,
    roles: toRoles(raw.roles),
    levelChanges: list(raw.levelChanges, toLevelChange).sort(
      (a, b) => Date.parse(b.changedAt) - Date.parse(a.changedAt),
    ),
    subscriptions: list(raw.subscriptions, toSubscription),
    subscriptionsTruncated: raw.subscriptionsTruncated === true,
  };
}

export interface LevelOption {
  id: string;
  name: string;
  isEntry: boolean;
}

/** The association's levels in the order the API gives them (lowest rank first), the entry level marked. */
export function toLevelOptions(raw: Schemas['LevelsResponse']): LevelOption[] {
  return list(raw.levels, (row) =>
    typeof row.id === 'string' && typeof row.name === 'string'
      ? { id: row.id, name: row.name, isEntry: row.id === raw.entryLevelId }
      : null,
  );
}
