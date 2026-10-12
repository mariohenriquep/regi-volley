import type { components } from '@/api/schema.gen';
import { isCalendarDay } from '@/lib/dates';

type Schemas = components['schemas'];

export type PlanType = 'MONTHLY_UNLIMITED' | 'MONTHLY_N_PER_WEEK' | 'PACK' | 'SINGLE_SESSION';
export type PaymentStatus = 'PENDING' | 'PAID' | 'OVERDUE';

/** One subscription as My plan shows it (generated types have every field optional; this one has none missing). */
export interface Subscription {
  id: string;
  planName: string;
  /** The API's plan type; an unknown one is kept as text. */
  type: string;
  startDate: string;
  endDate: string;
  paymentStatus: string;
  /** Credits left (pack, single session) or sessions left this week (weekly plan); absent for an unlimited plan. */
  remaining: number | undefined;
}

export interface Plan {
  /** The Lisbon day the API computed this for. */
  asOf: string;
  subscriptions: Subscription[];
}

function toSubscription(raw: Schemas['SubscriptionView']): Subscription | null {
  const { subscriptionId, planName, type, startDate, endDate, paymentStatus } = raw;
  if (
    !subscriptionId ||
    !planName ||
    !type ||
    !paymentStatus ||
    !isCalendarDay(startDate) ||
    !isCalendarDay(endDate)
  ) {
    return null;
  }
  return {
    id: subscriptionId,
    planName,
    type,
    startDate,
    endDate,
    paymentStatus,
    remaining: raw.remaining,
  };
}

export function toPlan(raw: Schemas['MyPlanResponse']): Plan {
  const views: unknown = raw.subscriptions;
  return {
    asOf: isCalendarDay(raw.asOf) ? raw.asOf : '',
    subscriptions: Array.isArray(views)
      ? views.flatMap((view: unknown) =>
          typeof view === 'object' && view !== null ? (toSubscription(view) ?? []) : [],
        )
      : [],
  };
}

const isCredit = (subscription: Subscription) =>
  subscription.type === 'PACK' || subscription.type === 'SINGLE_SESSION';

/**
 * What the booking dialog says about credits, from the plans that cover the session's day. The API does not say
 * which subscription a booking will charge, so the text is only as specific as the plans allow: "You'll use 1
 * credit" when every covering plan is made of credits, "may" for a mix, nothing for unlimited or weekly plans or when
 * no plan covers the day (the API refuses that booking with its own reason).
 */
export function creditNote(
  subscriptions: readonly Subscription[],
  sessionDay: string,
  full: boolean,
): string | null {
  const covering = subscriptions.filter(
    ({ startDate, endDate }) => startDate <= sessionDay && sessionDay <= endDate,
  );
  const credits = covering.filter(isCredit);
  if (credits.length === 0) return null;
  if (credits.length < covering.length) {
    return full
      ? 'A credit may be used if you get a seat.'
      : 'This may use 1 credit from your plan.';
  }
  if (full) return 'A credit is used only if you get a seat.';
  const left = credits.reduce((sum, { remaining }) => sum + (remaining ?? 0), 0);
  return `You'll use 1 credit (${String(left)} left).`;
}
