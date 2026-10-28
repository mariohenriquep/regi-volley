import type { components } from '@/api/schema.gen';
import { formatCents } from '@/lib/money';

type Schemas = components['schemas'];

/** The plan types, in the order the form offers them (`PlanRequest.type`), with the words the screens use. */
export const PLAN_TYPES = [
  { type: 'MONTHLY_UNLIMITED', label: 'Monthly, unlimited sessions' },
  { type: 'MONTHLY_N_PER_WEEK', label: 'Monthly, N sessions per week' },
  { type: 'PACK', label: 'Pack of sessions' },
  { type: 'SINGLE_SESSION', label: 'Single session' },
] as const;
export type PlanType = (typeof PLAN_TYPES)[number]['type'];

export function isPlanType(value: string | undefined): value is PlanType {
  return PLAN_TYPES.some(({ type }) => type === value);
}

/** The API's limits for a plan (`PlanRequest`). */
export const MAX_PLAN_NAME = 100;
export const SESSIONS_PER_WEEK = { min: 1, max: 7 } as const;
export const CREDITS = { min: 1, max: 1000 } as const;
export const VALIDITY_DAYS = { min: 1, max: 3650 } as const;

export interface Plan {
  id: string;
  name: string;
  /** A word of this version's list for a known type; any other word is shown as the API wrote it. */
  type: string;
  priceCents: number;
  sessionsPerWeek: number | undefined;
  credits: number | undefined;
  validityDays: number | undefined;
  /** Empty: every level is allowed. */
  allowedLevels: { id: string; name: string }[];
}

export function typeLabel(type: string): string {
  return PLAN_TYPES.find((known) => known.type === type)?.label ?? type;
}

export function toPlan(raw: Schemas['PlanEntryResponse']): Plan | null {
  if (!raw.id || typeof raw.priceCents !== 'number') return null;
  const levels: unknown = raw.allowedLevels;
  return {
    id: raw.id,
    name: raw.name ?? '',
    type: raw.type ?? '',
    priceCents: raw.priceCents,
    sessionsPerWeek: raw.sessionsPerWeek,
    credits: raw.credits,
    validityDays: raw.validityDays,
    allowedLevels: Array.isArray(levels)
      ? levels.flatMap((level: Schemas['NamedRefView'] | null) =>
          level?.id && level.name ? [{ id: level.id, name: level.name }] : [],
        )
      : [],
  };
}

/** The plans in the order the API gives them; a payload that is not a list is an empty one. */
export function toPlans(raw: unknown): Plan[] {
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((row: unknown) =>
    typeof row === 'object' && row !== null ? (toPlan(row) ?? []) : [],
  );
}

const count = (n: number, one: string, many: string) => `${String(n)} ${n === 1 ? one : many}`;

/** What the plan gives, in words: "10 credits, valid for 90 days". A type this version does not know gets no terms. */
export function termsText(plan: Plan): string | null {
  const days = plan.validityDays === undefined ? null : count(plan.validityDays, 'day', 'days');
  switch (plan.type) {
    case 'MONTHLY_UNLIMITED':
      return 'Unlimited sessions for one month';
    case 'MONTHLY_N_PER_WEEK':
      return plan.sessionsPerWeek === undefined
        ? null
        : `${count(plan.sessionsPerWeek, 'session', 'sessions')} per week for one month`;
    case 'PACK':
      return plan.credits === undefined || days === null
        ? null
        : `${count(plan.credits, 'credit', 'credits')}, valid for ${days}`;
    case 'SINGLE_SESSION':
      return days === null ? null : `1 session, valid for ${days}`;
    default:
      return null;
  }
}

export const priceText = (plan: Plan) => formatCents(plan.priceCents);
