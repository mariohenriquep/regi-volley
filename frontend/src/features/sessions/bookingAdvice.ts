import { ApiError } from '@/api/errors';

/** What the member can do about a refusal. */
export interface Advice {
  text: string;
  /** The refusal is about the plan: offer the My plan page. */
  planLink?: boolean;
}

/**
 * The API gives every business refusal the same code (`BUSINESS_RULE_VIOLATION`) and tells them apart by their message,
 * which is the member's explanation and is always shown as it came. This adds what to do next for the refusals the
 * backend is known to send (BookingNotAllowedException, the booking window, overlap, duplicate). A message it does not
 * recognise simply gets no advice.
 */
const RULES: { pattern: RegExp; advice: Advice }[] = [
  {
    pattern: /payment is overdue/i,
    advice: {
      text: 'Contact your association to settle the payment, then try again.',
      planLink: true,
    },
  },
  {
    pattern: /no balance left/i,
    advice: {
      text: 'You have no sessions left on your plan. Ask your association about a new one.',
      planLink: true,
    },
  },
  {
    pattern: /no subscription valid/i,
    advice: {
      text: 'You need a plan that is valid on the day of the session. Ask your association to assign one.',
      planLink: true,
    },
  },
  {
    pattern: /plan does not give access/i,
    advice: { text: "Your plan doesn't cover this group's levels.", planLink: true },
  },
  {
    pattern: /level is not accepted/i,
    advice: {
      text: 'This group is for other levels. Ask your coach or association if your level should change.',
    },
  },
  {
    pattern: /member is not active|member is deactivated/i,
    advice: { text: 'Contact your association about your membership.' },
  },
  { pattern: /opens at/i, advice: { text: 'Come back when booking opens.' } },
  {
    pattern: /closed at|already started/i,
    advice: { text: 'Booking for this session is over.' },
  },
  {
    pattern: /another session at the same time/i,
    advice: {
      text: 'Cancel your other booking first if you would rather play this session.',
    },
  },
  {
    pattern: /no longer scheduled/i,
    advice: { text: "This session isn't available any more." },
  },
  {
    pattern: /already has a booking in this session/i,
    advice: { text: 'You already have a booking in this session.' },
  },
];

export function adviceFor(error: unknown): Advice | undefined {
  if (!(error instanceof ApiError) || (error.status !== 422 && error.status !== 409)) {
    return undefined;
  }
  return RULES.find(({ pattern }) => pattern.test(error.message))?.advice;
}
