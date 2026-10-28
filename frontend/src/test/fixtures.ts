import { http, HttpResponse } from 'msw';
import type { components } from '@/api/schema.gen';
import { me, tokenResponse } from './handlers';

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

type Role = NonNullable<Schemas['MeResponse']['roles']>[number];

/** `GET /me` as the API answers it for a member holding these roles (a plain member when none are given). */
export function meAs(...roles: Role[]) {
  return http.get('/api/v1/me', () =>
    HttpResponse.json({
      ...me,
      roles: roles.length > 0 ? roles : ['MEMBER'],
    } satisfies Schemas['MeResponse']),
  );
}

export const COACHING_URL = '/api/v1/coaching/sessions';

/** A coach's line for Thursday 16 July 2026, 20:30-22:00 in Lisbon: not started, 8 of 12 seats taken, every action open. */
export function coaching(overrides: Partial<Schemas['CoachingSessionResponse']> = {}) {
  return {
    sessionId: SESSION_ID,
    trainingGroupId: '55555555-5555-5555-5555-555555555555',
    groupName: 'Thursday evening',
    venueName: 'Pavilhão Central',
    coachId: '22222222-2222-2222-2222-222222222222',
    startsAt: '2026-07-16T19:30:00Z',
    endsAt: '2026-07-16T21:00:00Z',
    status: 'SCHEDULED',
    capacity: 12,
    confirmedCount: 8,
    waitlistCount: 2,
    attendedCount: 0,
    noShowCount: 0,
    unmarkedCount: 8,
    canMarkAttendance: false,
    canCancel: true,
    canChangeCapacity: true,
    ...overrides,
  } satisfies Schemas['CoachingSessionResponse'];
}

export function rosterEntry(overrides: Partial<Schemas['RosterEntryView']> = {}) {
  return {
    bookingId: BOOKING_ID,
    memberId: '77777777-7777-7777-7777-777777777777',
    memberName: 'Ana Silva',
    status: 'CONFIRMED',
    ...overrides,
  } satisfies Schemas['RosterEntryView'];
}

export function rosterOf(overrides: Partial<Schemas['SessionRosterResponse']> = {}) {
  return {
    sessionId: SESSION_ID,
    trainingGroupId: '55555555-5555-5555-5555-555555555555',
    coachId: '22222222-2222-2222-2222-222222222222',
    startsAt: '2026-07-16T19:30:00Z',
    endsAt: '2026-07-16T21:00:00Z',
    capacity: 12,
    status: 'SCHEDULED',
    coaching: coaching(),
    seats: [rosterEntry()],
    waitlist: [],
    ...overrides,
  } satisfies Schemas['SessionRosterResponse'];
}
