import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  BOOKING_ID,
  bookable,
  planHandler,
  SESSION_ID,
  signedIn,
  subscription,
} from '@/test/fixtures';
import { apiError, recorder } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

type Session = ReturnType<typeof bookable> & { myBookingStatus?: string };

beforeEach(() => {
  // Wednesday 15 July 2026, 11:00 in Lisbon: this week's Monday is 13 July.
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-07-15T10:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllEnvs();
});

/** The week endpoint: answers `weeks[weekOf]` (default: `current`), and remembers each `weekOf` asked for. */
function serveSessions(current: Session[], weeks: Record<string, Session[]> = {}) {
  const asked: (string | null)[] = [];
  const state = { current };
  server.use(
    signedIn,
    http.get('/api/v1/sessions', ({ request }) => {
      const weekOf = new URL(request.url).searchParams.get('weekOf');
      asked.push(weekOf);
      return HttpResponse.json((weekOf && weeks[weekOf]) ?? state.current);
    }),
  );
  return { asked, state };
}

const bookingUrl = `/api/v1/sessions/${SESSION_ID}/bookings`;
const cancelUrl = `${bookingUrl}/${BOOKING_ID}/cancellation`;

describe('this week', () => {
  it("asks for the Monday of the current week in Lisbon, even when the device's day is still Sunday", async () => {
    // Arrange: 23:30 UTC on Sunday 19 July is already Monday 20 July, 00:30, in Lisbon
    vi.setSystemTime(new Date('2026-07-19T23:30:00Z'));
    vi.stubEnv('TZ', 'America/Los_Angeles');
    const { asked } = serveSessions([]);

    // Act
    renderApp('/');
    await screen.findByText('No sessions this week');

    // Assert
    expect(asked).toEqual(['2026-07-20']);
  });

  it('groups the sessions by day and shows each one with its Lisbon time, seats and waitlist', async () => {
    // Arrange
    serveSessions([
      bookable({ freeSeats: 5, capacity: 12, waitlistSize: 2 }),
      bookable({
        sessionId: '77777777-7777-7777-7777-777777777777',
        groupName: 'Thursday open play',
        startsAt: '2026-07-16T20:00:00Z',
        endsAt: '2026-07-16T21:00:00Z',
        freeSeats: 0,
        capacity: 10,
        waitlistSize: 0,
      }),
    ]);

    // Act
    renderApp('/');

    // Assert
    const tuesday = await screen.findByRole('region', { name: 'Tuesday 14 July' });
    const card = within(within(tuesday).getByRole('article', { name: 'Tuesday evening' }));
    expect(card.getByText('20:30 – 22:00')).toBeInTheDocument();
    expect(card.getByText('1 h 30 min')).toBeInTheDocument();
    expect(card.getByText('5 of 12 seats free')).toBeInTheDocument();
    expect(card.getByText('2 on the waitlist')).toBeInTheDocument();
    const thursday = within(await screen.findByRole('region', { name: 'Thursday 16 July' }));
    expect(thursday.getByText('21:00 – 22:00')).toBeInTheDocument();
    expect(thursday.getByText('Full')).toBeInTheDocument();
    expect(thursday.getByText('0 of 10 seats free')).toBeInTheDocument();
  });

  it.each(['Pacific/Auckland', 'America/Los_Angeles'])(
    'shows Lisbon time whatever the time zone of the device (%s)',
    async (timeZone) => {
      // Arrange
      vi.stubEnv('TZ', timeZone);
      serveSessions([bookable()]);

      // Act
      renderApp('/');

      // Assert
      expect(await screen.findByText('20:30 – 22:00')).toBeInTheDocument();
      expect(screen.getByRole('region', { name: 'Tuesday 14 July' })).toBeInTheDocument();
    },
  );

  it("shows the member's own booking: confirmed, or on the waitlist", async () => {
    // Arrange
    serveSessions([
      bookable({ myBookingStatus: 'CONFIRMED', myBookingId: BOOKING_ID }),
      bookable({
        sessionId: '77777777-7777-7777-7777-777777777777',
        groupName: 'Thursday open play',
        startsAt: '2026-07-16T20:00:00Z',
        endsAt: '2026-07-16T21:00:00Z',
        myBookingStatus: 'WAITLISTED',
      }),
    ]);

    // Act
    renderApp('/');

    // Assert
    const confirmed = within(await screen.findByRole('article', { name: 'Tuesday evening' }));
    expect(confirmed.getByText("You're confirmed")).toBeInTheDocument();
    expect(confirmed.queryByRole('button', { name: /^Book/ })).not.toBeInTheDocument();
    const waitlisted = within(screen.getByRole('article', { name: 'Thursday open play' }));
    expect(waitlisted.getByText("You're on the waitlist")).toBeInTheDocument();
  });

  it('shows the venue when the API gives one, and nothing when it does not', async () => {
    // Arrange
    serveSessions([
      bookable({ venueName: 'Pavilhão Central' }),
      bookable({ sessionId: '77777777-7777-7777-7777-777777777777', groupName: 'No venue group' }),
    ]);

    // Act
    renderApp('/');

    // Assert
    const card = within(await screen.findByRole('article', { name: 'Tuesday evening' }));
    expect(card.getByText('Pavilhão Central')).toBeInTheDocument();
    const other = within(screen.getByRole('article', { name: 'No venue group' }));
    expect(other.queryByText(/Pavilh/)).not.toBeInTheDocument();
  });

  it("shows the member's place on the waitlist, and no place for a confirmed booking", async () => {
    // Arrange
    serveSessions([
      bookable({ myBookingStatus: 'CONFIRMED', myBookingId: BOOKING_ID }),
      bookable({
        sessionId: '77777777-7777-7777-7777-777777777777',
        groupName: 'Thursday open play',
        myBookingStatus: 'WAITLISTED',
        myBookingId: '88888888-8888-8888-8888-888888888888',
        waitlistPosition: 3,
        waitlistSize: 5,
      }),
    ]);

    // Act
    renderApp('/');

    // Assert
    const waitlisted = within(await screen.findByRole('article', { name: 'Thursday open play' }));
    expect(waitlisted.getByText("You're number 3 on the waitlist")).toBeInTheDocument();
    const confirmed = within(screen.getByRole('article', { name: 'Tuesday evening' }));
    expect(confirmed.queryByText(/number \d+ on the waitlist/)).not.toBeInTheDocument();
  });

  it('shows when booking opens for a session that is not open yet, and still lets the API decide', async () => {
    // Arrange: opens Monday 20 July at 09:00 Lisbon time
    serveSessions([bookable({ bookingOpensAt: '2026-07-20T08:00:00Z' })]);

    // Act
    renderApp('/');

    // Assert
    const card = within(await screen.findByRole('article', { name: 'Tuesday evening' }));
    expect(card.getByText('Booking opens Mon 20 Jul, 09:00')).toBeInTheDocument();
    expect(card.getByRole('button', { name: /^Book/ })).toBeEnabled();
  });

  it('does not mention opening for a session whose booking is already open', async () => {
    // Arrange
    serveSessions([bookable()]);

    // Act
    renderApp('/');

    // Assert
    await screen.findByRole('article', { name: 'Tuesday evening' });
    expect(screen.queryByText(/Booking opens/)).not.toBeInTheDocument();
  });

  it('has a heading, a loading state and an empty state', async () => {
    // Arrange
    serveSessions([]);

    // Act
    renderApp('/');

    // Assert
    expect(await screen.findByRole('status', { name: 'Loading sessions' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { level: 1, name: 'Sessions' })).toBeInTheDocument();
    expect(await screen.findByText('No sessions this week')).toBeInTheDocument();
    expect(screen.queryByRole('status', { name: 'Loading sessions' })).not.toBeInTheDocument();
  });

  it('shows the error with a way to try again', async () => {
    // Arrange
    let fail = true;
    server.use(
      signedIn,
      http.get('/api/v1/sessions', () =>
        fail
          ? HttpResponse.json(
              { code: 'INTERNAL_ERROR', message: 'boom', requestId: 'req-500' },
              { status: 500 },
            )
          : HttpResponse.json([bookable()]),
      ),
    );
    const { user } = renderApp('/');

    // Act
    const alert = await screen.findByRole('alert');
    fail = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    // Assert
    expect(alert).toHaveTextContent('Something went wrong. Please try again.');
    expect(alert).toHaveTextContent('req-500');
    expect(await screen.findByRole('article', { name: 'Tuesday evening' })).toBeInTheDocument();
  });
});

describe('week navigation', () => {
  it('moves to the next week and back, and shows which week it is', async () => {
    // Arrange
    const { asked } = serveSessions([bookable()], {
      '2026-07-20': [bookable({ groupName: 'Next week group', startsAt: '2026-07-21T19:30:00Z' })],
    });
    const { user } = renderApp('/');
    const week = await screen.findByRole('navigation', { name: 'Week' });
    expect(within(week).getByText('This week · 13 – 19 July')).toBeInTheDocument();

    // Act
    await user.click(within(week).getByRole('button', { name: 'Next week' }));

    // Assert
    expect(await screen.findByRole('article', { name: 'Next week group' })).toBeInTheDocument();
    expect(within(week).getByText('20 – 26 July')).toBeInTheDocument();
    expect(asked).toEqual(['2026-07-13', '2026-07-20']);
    await user.click(within(week).getByRole('button', { name: 'Previous week' }));
    expect(await screen.findByRole('article', { name: 'Tuesday evening' })).toBeInTheDocument();
  });

  it('does not offer weeks that are over: previous is disabled on the current week', async () => {
    // Arrange
    serveSessions([]);

    // Act
    renderApp('/');

    // Assert
    expect(await screen.findByRole('button', { name: 'Previous week' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Next week' })).toBeEnabled();
  });

  it('stops eight weeks ahead', async () => {
    // Arrange: 13 July + 8 weeks = 7 September
    serveSessions([]);

    // Act
    renderApp('/?week=2026-09-07');

    // Assert
    expect(await screen.findByRole('button', { name: 'Next week' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Previous week' })).toBeEnabled();
  });

  it.each(['2020-01-06', '2030-01-07', 'nonsense', '2026-07-15'])(
    'falls back to a week it can show when the address says %s',
    async (week) => {
      // Arrange
      const { asked } = serveSessions([]);

      // Act
      renderApp(`/?week=${week}`);
      await screen.findByText('No sessions this week');

      // Assert: a past, too-far or unreadable week is replaced; a mid-week day is moved to its Monday
      expect(asked).toEqual(['2026-07-13']);
    },
  );

  it('shows a skeleton, not the previous week, while the next week loads', async () => {
    // Arrange
    server.use(
      signedIn,
      http.get('/api/v1/sessions', ({ request }) =>
        new URL(request.url).searchParams.get('weekOf') === '2026-07-13'
          ? HttpResponse.json([bookable()])
          : new Promise<never>(() => undefined),
      ),
    );
    const { user } = renderApp('/');
    await screen.findByRole('article', { name: 'Tuesday evening' });

    // Act
    await user.click(screen.getByRole('button', { name: 'Next week' }));

    // Assert
    expect(screen.getByRole('status', { name: 'Loading sessions' })).toBeInTheDocument();
    expect(screen.queryByRole('article', { name: 'Tuesday evening' })).not.toBeInTheDocument();
  });
});

describe('booking in two taps', () => {
  it('books with Book and Confirm booking, shows the server result and refreshes the list', async () => {
    // Arrange
    const { seen, record } = recorder();
    const { state } = serveSessions([bookable()]);
    server.use(
      planHandler([subscription({ type: 'MONTHLY_UNLIMITED', remaining: undefined })]),
      http.post(bookingUrl, async ({ request }) => {
        await record(request);
        state.current = [
          bookable({ freeSeats: 4, myBookingStatus: 'CONFIRMED', myBookingId: BOOKING_ID }),
        ];
        return HttpResponse.json(
          { bookingId: BOOKING_ID, sessionId: SESSION_ID, status: 'CONFIRMED' },
          { status: 201 },
        );
      }),
    );
    const { user } = renderApp('/');

    // Act: tap 1
    await user.click(await screen.findByRole('button', { name: /^Book/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Book this session?' });
    expect(within(dialog).getByText('Tuesday evening')).toBeInTheDocument();
    expect(within(dialog).getByText(/Tuesday 14 July, 20:30 – 22:00/)).toBeInTheDocument();
    expect(seen).toHaveLength(0);
    // Act: tap 2
    await user.click(within(dialog).getByRole('button', { name: 'Confirm booking' }));

    // Assert
    expect(await screen.findByText("You're booked and confirmed.")).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(seen).toEqual([
      expect.objectContaining({
        method: 'POST',
        path: bookingUrl,
        authorization: 'Bearer test-member-token-fixture',
        body: '',
      }),
    ]);
    const card = within(await screen.findByRole('article', { name: 'Tuesday evening' }));
    expect(await card.findByText("You're confirmed")).toBeInTheDocument();
    expect(card.getByText('4 of 12 seats free')).toBeInTheDocument();
  });

  it('shows the waitlist place the server gave when the session turned out to be full', async () => {
    // Arrange
    const { state } = serveSessions([bookable({ freeSeats: 1 })]);
    server.use(
      planHandler([subscription({ type: 'MONTHLY_UNLIMITED', remaining: undefined })]),
      http.post(bookingUrl, () => {
        state.current = [
          bookable({ freeSeats: 0, waitlistSize: 3, myBookingStatus: 'WAITLISTED' }),
        ];
        return HttpResponse.json(
          {
            bookingId: BOOKING_ID,
            sessionId: SESSION_ID,
            status: 'WAITLISTED',
            waitlistPosition: 3,
          },
          { status: 201 },
        );
      }),
    );
    const { user } = renderApp('/');

    // Act
    await user.click(await screen.findByRole('button', { name: /^Book/ }));
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }));

    // Assert
    expect(
      await screen.findByText("The session was full. You're number 3 on the waitlist."),
    ).toBeInTheDocument();
    expect(await screen.findByText("You're on the waitlist")).toBeInTheDocument();
  });

  it('warns in the dialog that a full session means the waitlist', async () => {
    // Arrange
    serveSessions([bookable({ freeSeats: 0, waitlistSize: 2 })]);
    server.use(planHandler([subscription({ type: 'MONTHLY_UNLIMITED', remaining: undefined })]));
    const { user } = renderApp('/');

    // Act
    await user.click(await screen.findByRole('button', { name: /^Book/ }));

    // Assert
    const dialog = await screen.findByRole('dialog', { name: 'Book this session?' });
    expect(dialog).toHaveTextContent("This session is full. You'll join the waitlist");
  });

  it("says you'll use 1 credit when every plan you hold is made of credits", async () => {
    // Arrange
    serveSessions([bookable()]);
    server.use(planHandler([subscription({ type: 'PACK', remaining: 7 })]));
    const { user } = renderApp('/');

    // Act
    await user.click(await screen.findByRole('button', { name: /^Book/ }));

    // Assert
    const dialog = await screen.findByRole('dialog', { name: 'Book this session?' });
    expect(await within(dialog).findByText("You'll use 1 credit (7 left).")).toBeInTheDocument();
  });

  it('says a waitlisted request uses a credit only once it gets a seat', async () => {
    // Arrange
    serveSessions([bookable({ freeSeats: 0 })]);
    server.use(planHandler([subscription({ type: 'SINGLE_SESSION', remaining: 1 })]));
    const { user } = renderApp('/');

    // Act
    await user.click(await screen.findByRole('button', { name: /^Book/ }));

    // Assert
    const dialog = await screen.findByRole('dialog', { name: 'Book this session?' });
    expect(
      await within(dialog).findByText('A credit is used only if you get a seat.'),
    ).toBeInTheDocument();
  });

  it('does not talk about credits for an unlimited plan, and does not guess for a mix', async () => {
    // Arrange
    serveSessions([bookable()]);
    server.use(planHandler([subscription({ type: 'MONTHLY_UNLIMITED', remaining: undefined })]));
    const { user } = renderApp('/');

    // Act
    await user.click(await screen.findByRole('button', { name: /^Book/ }));

    // Assert
    const dialog = await screen.findByRole('dialog', { name: 'Book this session?' });
    await waitFor(() => {
      expect(within(dialog).getByRole('button', { name: 'Confirm booking' })).toBeEnabled();
    });
    expect(dialog).not.toHaveTextContent(/credit/);
  });

  it('still lets the member book when the plan cannot be loaded', async () => {
    // Arrange
    serveSessions([bookable()]);
    server.use(http.get('/api/v1/me/plan', () => apiError(500, 'INTERNAL_ERROR', 'boom')));
    const { user } = renderApp('/');

    // Act
    await user.click(await screen.findByRole('button', { name: /^Book/ }));

    // Assert
    const dialog = await screen.findByRole('dialog', { name: 'Book this session?' });
    expect(within(dialog).getByRole('button', { name: 'Confirm booking' })).toBeEnabled();
    expect(dialog).not.toHaveTextContent(/credit/);
  });

  it('closes with Not now and sends nothing', async () => {
    // Arrange
    const { seen, record } = recorder();
    serveSessions([bookable()]);
    server.use(
      planHandler(),
      http.post(bookingUrl, async ({ request }) => {
        await record(request);
        return new HttpResponse(null, { status: 500 });
      }),
    );
    const { user } = renderApp('/');
    await user.click(await screen.findByRole('button', { name: /^Book/ }));

    // Act
    await user.click(await screen.findByRole('button', { name: 'Not now' }));

    // Assert
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(seen).toHaveLength(0);
  });

  it('sends one request however often Confirm booking is pressed', async () => {
    // Arrange
    const { seen, record } = recorder();
    serveSessions([bookable()]);
    server.use(
      planHandler(),
      http.post(bookingUrl, async ({ request }) => {
        await record(request);
        return new Promise<never>(() => undefined);
      }),
    );
    const { user } = renderApp('/');
    await user.click(await screen.findByRole('button', { name: /^Book/ }));

    // Act
    const confirm = await screen.findByRole('button', { name: 'Confirm booking' });
    await user.click(confirm);
    await user.click(screen.getByRole('button', { name: 'Booking…' }));

    // Assert
    expect(seen).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Booking…' })).toBeDisabled();
  });
});

describe('when the API refuses the booking', () => {
  it.each([
    [
      'PAYMENT_OVERDUE',
      "The member's subscription payment is overdue",
      'Contact your association to settle the payment, then try again.',
    ],
    [
      'NO_BALANCE',
      "The member's subscription has no balance left for this session",
      'You have no sessions left on your plan.',
    ],
    [
      'NO_VALID_SUBSCRIPTION',
      'The member has no subscription valid on the session date',
      'You need a plan that is valid on the day of the session.',
    ],
    [
      'LEVEL_NOT_ALLOWED',
      "The member's level is not accepted by this group",
      'This group is for other levels.',
    ],
    [
      'PLAN_LEVEL_NOT_ALLOWED',
      "The member's plan does not give access to this group's levels",
      "Your plan doesn't cover this group's levels.",
    ],
    [
      'MEMBER_INACTIVE',
      'The member is not active',
      'Contact your association about your membership.',
    ],
    [
      'BOOKING_NOT_OPEN',
      'Booking for this session opens at 20 July 2026, 09:00',
      'Come back when booking opens.',
    ],
    [
      'BOOKING_CLOSED',
      'Booking for this session closed at 14 July 2026, 20:30',
      'Booking for this session is over.',
    ],
    [
      'OVERLAP',
      'The member already has a booking in another session at the same time (starting 14 July 2026, 20:30)',
      'Cancel your other booking first if you would rather play this session.',
    ],
    [
      'SESSION_NOT_SCHEDULED',
      'The session is no longer scheduled (status: cancelled)',
      "This session isn't available any more.",
    ],
  ])('%s: shows the API message and what to do about it', async (reason, message, hint) => {
    // Arrange
    serveSessions([bookable()]);
    server.use(
      planHandler(),
      http.post(bookingUrl, () => apiError(422, 'BUSINESS_RULE_VIOLATION', message, { reason })),
    );
    const { user } = renderApp('/');

    // Act
    await user.click(await screen.findByRole('button', { name: /^Book/ }));
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }));

    // Assert: the API's own words, then the advice; the dialog stays open and can be retried or closed
    const dialog = await screen.findByRole('dialog', { name: 'Book this session?' });
    const alert = await within(dialog).findByRole('alert');
    expect(alert).toHaveTextContent(message);
    expect(dialog).toHaveTextContent(hint);
    expect(within(dialog).getByRole('button', { name: 'Confirm booking' })).toBeEnabled();
  });

  it('shows only the API message when the reason is one this version does not know', async () => {
    // Arrange
    serveSessions([bookable()]);
    server.use(
      planHandler(),
      http.post(bookingUrl, () =>
        apiError(422, 'BUSINESS_RULE_VIOLATION', 'A rule from the future', {
          reason: 'SOMETHING_NEW',
        }),
      ),
    );
    const { user } = renderApp('/');
    await user.click(await screen.findByRole('button', { name: /^Book/ }));

    // Act
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }));

    // Assert
    const dialog = await screen.findByRole('dialog', { name: 'Book this session?' });
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('A rule from the future');
    expect(within(dialog).queryByRole('link', { name: 'See my plan' })).not.toBeInTheDocument();
    expect(dialog).not.toHaveTextContent(/Contact your|Come back|You have no|Ask your/);
  });

  it('does not guess the advice from the message when the API sends no reason', async () => {
    // Arrange
    serveSessions([bookable()]);
    server.use(
      planHandler(),
      http.post(bookingUrl, () =>
        apiError(422, 'BUSINESS_RULE_VIOLATION', "The member's subscription payment is overdue"),
      ),
    );
    const { user } = renderApp('/');
    await user.click(await screen.findByRole('button', { name: /^Book/ }));

    // Act
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }));

    // Assert
    const dialog = await screen.findByRole('dialog', { name: 'Book this session?' });
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('payment is overdue');
    expect(dialog).not.toHaveTextContent('Contact your association');
  });

  it('links to My plan when the reason is about the plan', async () => {
    // Arrange
    serveSessions([bookable()]);
    server.use(
      planHandler(),
      http.post(bookingUrl, () =>
        apiError(422, 'BUSINESS_RULE_VIOLATION', "The member's subscription payment is overdue", {
          reason: 'PAYMENT_OVERDUE',
        }),
      ),
    );
    const { user, router } = renderApp('/');
    await user.click(await screen.findByRole('button', { name: /^Book/ }));
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }));

    // Act
    await user.click(await screen.findByRole('link', { name: 'See my plan' }));

    // Assert
    expect(await screen.findByRole('heading', { level: 1, name: 'My plan' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/me/plan');
  });

  it('reloads the list when the member already has a booking (409), so the card tells the truth', async () => {
    // Arrange
    const { state, asked } = serveSessions([bookable()]);
    server.use(
      planHandler(),
      http.post(bookingUrl, () => {
        state.current = [bookable({ myBookingStatus: 'CONFIRMED', myBookingId: BOOKING_ID })];
        return apiError(409, 'CONFLICT', 'The member already has a booking in this session', {
          reason: 'DUPLICATE_BOOKING',
        });
      }),
    );
    const { user } = renderApp('/');
    await user.click(await screen.findByRole('button', { name: /^Book/ }));

    // Act
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }));

    // Assert
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'The member already has a booking in this session',
    );
    expect(dialog).toHaveTextContent('You already have a booking in this session.');
    await waitFor(() => {
      expect(asked).toHaveLength(2);
    });
  });

  it('shows generic text and the request id for a server error', async () => {
    // Arrange
    serveSessions([bookable()]);
    server.use(
      planHandler(),
      http.post(bookingUrl, () => apiError(500, 'INTERNAL_ERROR', 'SQL exploded')),
    );
    const { user } = renderApp('/');
    await user.click(await screen.findByRole('button', { name: /^Book/ }));

    // Act
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }));

    // Assert
    const alert = await within(await screen.findByRole('dialog')).findByRole('alert');
    expect(alert).toHaveTextContent('Something went wrong. Please try again.');
    expect(alert).toHaveTextContent('req-500');
    expect(alert).not.toHaveTextContent('SQL');
  });
});

describe('cancelling', () => {
  it('says cancelling is free before the deadline, and shows the deadline in Lisbon time', async () => {
    // Arrange: now is Wed 15 July 11:00 Lisbon; free until Thu 16 July 14:30 Lisbon
    const { dialog } = await openCancelDialog({ freeCancellationUntil: '2026-07-16T13:30:00Z' });

    // Assert
    expect(dialog).toHaveTextContent(
      'Cancelling now is free. You can cancel for free until Thu 16 Jul, 14:30.',
    );
    expect(dialog).not.toHaveTextContent('used session');
  });

  it('says cancelling counts as a used session once the deadline has passed', async () => {
    // Arrange: free until Wed 15 July 10:59 Lisbon, a minute ago
    const { dialog } = await openCancelDialog({ freeCancellationUntil: '2026-07-15T09:59:00Z' });

    // Assert
    expect(dialog).toHaveTextContent(
      'Cancelling now counts as a used session (late). Free cancellation ended Wed 15 Jul, 10:59.',
    );
  });

  it('treats exactly the deadline as still free', async () => {
    // Arrange: the deadline is the very instant of now
    const { dialog } = await openCancelDialog({ freeCancellationUntil: '2026-07-15T10:00:00Z' });

    // Assert
    expect(dialog).toHaveTextContent('Cancelling now is free.');
  });

  it('says cancelling a waitlist place is always free, even after the deadline', async () => {
    // Arrange / Act
    const { dialog } = await openCancelDialog({
      status: 'WAITLISTED',
      freeCancellationUntil: '2026-07-14T13:30:00Z',
    });

    // Assert
    expect(dialog).toHaveTextContent('Cancelling a place on the waitlist is always free.');
    expect(dialog).not.toHaveTextContent('counts as a used session');
  });

  it('claims nothing about the cost when the deadline is missing', async () => {
    // Arrange / Act
    const { dialog } = await openCancelDialog({ freeCancellationUntil: 'soon' });

    // Assert
    expect(dialog).not.toHaveTextContent('Cancelling now');
    expect(within(dialog).getByRole('button', { name: 'Cancel booking' })).toBeEnabled();
  });

  it('cancels with the booking id from the list, also after a reload (nothing was booked in this page)', async () => {
    // Arrange: the page has just opened and the list already carries the booking id
    const { user, seen, dialog } = await openCancelDialog();

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Cancel booking' }));

    // Assert
    expect(await screen.findByText(/^Booking cancelled/)).toBeInTheDocument();
    expect(seen).toEqual([expect.objectContaining({ method: 'POST', path: cancelUrl })]);
  });

  it('cancels a booking made in this page from the refreshed list', async () => {
    // Arrange
    const { seen, record } = recorder();
    const { state } = serveSessions([bookable()]);
    server.use(
      planHandler(),
      http.post(bookingUrl, () => {
        state.current = [bookable({ myBookingStatus: 'CONFIRMED', myBookingId: BOOKING_ID })];
        return HttpResponse.json(
          { bookingId: BOOKING_ID, sessionId: SESSION_ID, status: 'CONFIRMED' },
          { status: 201 },
        );
      }),
      http.post(cancelUrl, async ({ request }) => {
        await record(request);
        return HttpResponse.json({ late: false, creditRefunded: true, promotedCount: 0 });
      }),
    );
    const { user } = renderApp('/');
    await user.click(await screen.findByRole('button', { name: /^Book/ }));
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }));

    // Act
    await user.click(await screen.findByRole('button', { name: /^Cancel/ }));
    await user.click(await screen.findByRole('button', { name: 'Cancel booking' }));

    // Assert
    expect(await screen.findByText(/^Booking cancelled/)).toBeInTheDocument();
    expect(seen).toHaveLength(1);
  });

  it('cancels and shows the free outcome the API reports, with the credit back', async () => {
    // Arrange
    const { user, seen, dialog } = await openCancelDialog({
      outcome: {
        bookingId: BOOKING_ID,
        sessionId: SESSION_ID,
        late: false,
        creditRefunded: true,
        promotedCount: 0,
      },
    });

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Cancel booking' }));

    // Assert
    expect(
      await screen.findByText('Booking cancelled. It was free and your credit was given back.'),
    ).toBeInTheDocument();
    expect(seen).toEqual([
      expect.objectContaining({
        method: 'POST',
        path: cancelUrl,
        authorization: 'Bearer test-member-token-fixture',
      }),
    ]);
    expect(screen.queryByText("You're confirmed")).not.toBeInTheDocument();
    expect(await screen.findByRole('button', { name: /^Book/ })).toBeInTheDocument();
  });

  it('shows that a late cancellation counted as used', async () => {
    // Arrange
    const { user, dialog } = await openCancelDialog({
      outcome: {
        bookingId: BOOKING_ID,
        sessionId: SESSION_ID,
        late: true,
        creditRefunded: false,
        promotedCount: 1,
      },
    });

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Cancel booking' }));

    // Assert
    expect(
      await screen.findByText(
        'Booking cancelled late, so it counts as a used session. Someone on the waitlist took your seat.',
      ),
    ).toBeInTheDocument();
  });

  it('shows a free cancellation without a credit as simply free', async () => {
    // Arrange
    const { user, dialog } = await openCancelDialog({
      outcome: {
        bookingId: BOOKING_ID,
        sessionId: SESSION_ID,
        late: false,
        creditRefunded: false,
        promotedCount: 0,
      },
    });

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Cancel booking' }));

    // Assert
    expect(await screen.findByText('Booking cancelled. It was free.')).toBeInTheDocument();
  });

  it("shows the API's outcome even when it differs from what the dialog said", async () => {
    // Arrange: the dialog says free (this device's clock), the API says it was late
    const { user, dialog } = await openCancelDialog({
      freeCancellationUntil: '2026-07-16T13:30:00Z',
      outcome: { late: true, creditRefunded: false, promotedCount: 0 },
    });
    expect(dialog).toHaveTextContent('Cancelling now is free.');

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Cancel booking' }));

    // Assert
    expect(
      await screen.findByText('Booking cancelled late, so it counts as a used session.'),
    ).toBeInTheDocument();
  });

  it('keeps the booking when the member changes their mind', async () => {
    // Arrange
    const { user, seen, dialog } = await openCancelDialog();

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Keep booking' }));

    // Assert
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(seen).toHaveLength(0);
    expect(screen.getByText("You're confirmed")).toBeInTheDocument();
  });

  it("shows the API's words when cancelling is closed, and keeps the dialog open", async () => {
    // Arrange
    const { user, dialog } = await openCancelDialog();
    const message =
      'The booking can no longer be cancelled: the session started at 14 July 2026, 20:30';
    server.use(
      http.post(cancelUrl, () =>
        apiError(422, 'BUSINESS_RULE_VIOLATION', message, { reason: 'CANCELLATION_CLOSED' }),
      ),
    );

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Cancel booking' }));

    // Assert
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(message);
    expect(dialog).toHaveTextContent("It's too late to cancel this booking.");
  });
});

describe('focus and keyboard', () => {
  it('moves focus into the dialog, closes on Escape and gives focus back to the Book button', async () => {
    // Arrange
    serveSessions([bookable()]);
    server.use(planHandler());
    const { user } = renderApp('/');
    const book = await screen.findByRole('button', { name: /^Book/ });

    // Act
    await user.click(book);
    const dialog = await screen.findByRole('dialog', { name: 'Book this session?' });

    // Assert
    expect(dialog).toContainElement(document.activeElement as HTMLElement);
    await user.keyboard('{Escape}');
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(book).toHaveFocus();
  });

  it('books with the keyboard alone', async () => {
    // Arrange
    const { seen, record } = recorder();
    serveSessions([bookable()]);
    server.use(
      planHandler(),
      http.post(bookingUrl, async ({ request }) => {
        await record(request);
        return HttpResponse.json(
          { bookingId: BOOKING_ID, sessionId: SESSION_ID, status: 'CONFIRMED' },
          { status: 201 },
        );
      }),
    );
    const { user } = renderApp('/');
    const book = await screen.findByRole('button', { name: /^Book/ });
    book.focus();

    // Act
    await user.keyboard('{Enter}');
    await screen.findByRole('dialog');
    await user.keyboard('{Enter}');

    // Assert
    expect(await screen.findByText("You're booked and confirmed.")).toBeInTheDocument();
    expect(seen).toHaveLength(1);
  });

  it('announces the result and moves focus to it, since the Book button is gone', async () => {
    // Arrange
    const { state } = serveSessions([bookable()]);
    server.use(
      planHandler(),
      http.post(bookingUrl, () => {
        state.current = [bookable({ myBookingStatus: 'CONFIRMED', myBookingId: BOOKING_ID })];
        return HttpResponse.json(
          { bookingId: BOOKING_ID, sessionId: SESSION_ID, status: 'CONFIRMED' },
          { status: 201 },
        );
      }),
    );
    const { user } = renderApp('/');
    await user.click(await screen.findByRole('button', { name: /^Book/ }));

    // Act
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }));

    // Assert
    const notice = await screen.findByText("You're booked and confirmed.");
    await waitFor(() => {
      expect(notice.closest('[role="status"]')).toHaveFocus();
    });
  });
});

describe('what stays fresh', () => {
  it('shows the new balance on My plan after a booking (the plan is fetched again)', async () => {
    // Arrange
    let planReads = 0;
    const { state } = serveSessions([bookable()]);
    server.use(
      http.get('/api/v1/me/plan', () => {
        planReads += 1;
        return HttpResponse.json({
          asOf: '2026-07-15',
          subscriptions: [subscription({ remaining: planReads === 1 ? 7 : 6 })],
        });
      }),
      http.post(bookingUrl, () => {
        state.current = [bookable({ myBookingStatus: 'CONFIRMED', myBookingId: BOOKING_ID })];
        return HttpResponse.json(
          { bookingId: BOOKING_ID, sessionId: SESSION_ID, status: 'CONFIRMED' },
          { status: 201 },
        );
      }),
    );
    const { user } = renderApp('/');
    await user.click(await screen.findByRole('button', { name: /^Book/ }));
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }));
    await screen.findByText("You're booked and confirmed.");

    // Act
    await user.click(screen.getByRole('link', { name: 'My plan' }));

    // Assert
    expect(await screen.findByText('6 credits left')).toBeInTheDocument();
  });
});

// ---- helpers -----------------------------------------------------------------------------------------------------

/** The member's confirmed (or waitlisted) booking as the list reports it; opens its cancel dialog. */
async function openCancelDialog(
  options: {
    status?: 'CONFIRMED' | 'WAITLISTED';
    outcome?: Record<string, unknown>;
    freeCancellationUntil?: string;
  } = {},
) {
  const {
    status = 'CONFIRMED',
    outcome = {},
    freeCancellationUntil = '2026-07-16T13:30:00Z',
  } = options;
  const { seen, record } = recorder();
  const served = serveSessions([
    bookable({ myBookingStatus: status, myBookingId: BOOKING_ID, freeCancellationUntil }),
  ]);
  server.use(
    planHandler([subscription({ type: 'MONTHLY_UNLIMITED', remaining: undefined })]),
    http.post(cancelUrl, async ({ request }) => {
      await record(request);
      served.state.current = [bookable()];
      return HttpResponse.json(outcome);
    }),
  );
  const app = renderApp('/');
  await app.user.click(await screen.findByRole('button', { name: /^Cancel/ }));
  const dialog = await screen.findByRole('alertdialog', { name: 'Cancel your booking?' });
  return { ...app, seen, dialog };
}
