import { ApiError, type RefusalReason } from '@/api/errors';

/** What the member can do about a refusal. */
export interface Advice {
  text: string;
  /** The refusal is about the plan: offer the My plan page. */
  planLink?: boolean;
}

/**
 * Every refusal of a booking (or of its cancellation) carries a stable `reason`; the message is the API's explanation
 * and is always shown as it came. This adds what to do next. `Record<RefusalReason, ...>` makes the compiler insist on
 * an entry for every reason in the contract, so a reason added to the API breaks the build until it is handled.
 */
const ADVICE: Record<RefusalReason, Advice> = {
  PAYMENT_OVERDUE: {
    text: 'Contact your association to settle the payment, then try again.',
    planLink: true,
  },
  NO_BALANCE: {
    text: 'You have no sessions left on your plan. Ask your association about a new one.',
    planLink: true,
  },
  NO_VALID_SUBSCRIPTION: {
    text: 'You need a plan that is valid on the day of the session. Ask your association to assign one.',
    planLink: true,
  },
  PLAN_LEVEL_NOT_ALLOWED: {
    text: "Your plan doesn't cover this group's levels.",
    planLink: true,
  },
  LEVEL_NOT_ALLOWED: {
    text: 'This group is for other levels. Ask your coach or association if your level should change.',
  },
  MEMBER_INACTIVE: { text: 'Contact your association about your membership.' },
  BOOKING_NOT_OPEN: { text: 'Come back when booking opens.' },
  BOOKING_CLOSED: { text: 'Booking for this session is over.' },
  OVERLAP: {
    text: 'Cancel your other booking first if you would rather play this session.',
  },
  SESSION_NOT_SCHEDULED: { text: "This session isn't available any more." },
  DUPLICATE_BOOKING: { text: 'You already have a booking in this session.' },
  COACH_OF_SESSION: { text: "You coach this session, so you can't book it as a player." },
  CANCELLATION_CLOSED: { text: "It's too late to cancel this booking." },
};

/** A Map for the lookup: the reason is the server's text, and `ADVICE['constructor']` must not find anything. */
const BY_REASON: ReadonlyMap<string, Advice> = new Map(Object.entries(ADVICE));

/** Advice for a refusal; none for another error, no reason, or a reason this version does not know (the message is enough). */
export function adviceFor(error: unknown): Advice | undefined {
  if (!(error instanceof ApiError) || error.reason === undefined) return undefined;
  return BY_REASON.get(error.reason);
}
