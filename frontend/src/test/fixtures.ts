import { http, HttpResponse } from 'msw';
import type { components } from '@/api/schema.gen';
import { tokenResponse } from './handlers';

type Schemas = components['schemas'];

/** A signed-in member: the start-up refresh succeeds. The token is a fixture, not a credential. */
export const signedIn = http.post('/api/v1/auth/refresh', () =>
  tokenResponse('test-member-token-fixture'),
);

export const SESSION_ID = '33333333-3333-3333-3333-333333333333';
export const BOOKING_ID = '44444444-4444-4444-4444-444444444444';

/** Tuesday 14 July 2026, 20:30-22:00 in Lisbon (summer time, UTC+1). */
export function bookable(overrides: Partial<Schemas['BookableSessionResponse']> = {}) {
  return {
    sessionId: SESSION_ID,
    trainingGroupId: '55555555-5555-5555-5555-555555555555',
    groupName: 'Tuesday evening',
    startsAt: '2026-07-14T19:30:00Z',
    endsAt: '2026-07-14T21:00:00Z',
    bookingOpensAt: '2026-07-07T19:30:00Z',
    freeCancellationUntil: '2026-07-14T13:30:00Z',
    capacity: 12,
    freeSeats: 5,
    waitlistSize: 0,
    ...overrides,
  } satisfies Schemas['BookableSessionResponse'];
}

export function subscription(overrides: Partial<Schemas['SubscriptionView']> = {}) {
  return {
    subscriptionId: '66666666-6666-6666-6666-666666666666',
    planName: 'Pack of 10',
    type: 'PACK',
    startDate: '2026-07-01',
    endDate: '2026-09-29',
    paymentStatus: 'PAID',
    remaining: 7,
    ...overrides,
  } satisfies Schemas['SubscriptionView'];
}

export function myPlan(subscriptions: Schemas['SubscriptionView'][] = [subscription()]) {
  return { asOf: '2026-07-15', subscriptions } satisfies Schemas['MyPlanResponse'];
}

export const planHandler = (subscriptions?: Schemas['SubscriptionView'][]) =>
  http.get('/api/v1/me/plan', () => HttpResponse.json(myPlan(subscriptions)));

export function historyEntry(overrides: Partial<Schemas['HistoryEntryView']> = {}) {
  return {
    sessionId: SESSION_ID,
    trainingGroupId: '55555555-5555-5555-5555-555555555555',
    startsAt: '2026-07-07T19:30:00Z',
    endsAt: '2026-07-07T21:00:00Z',
    bookingId: BOOKING_ID,
    status: 'ATTENDED',
    ...overrides,
  } satisfies Schemas['HistoryEntryView'];
}
