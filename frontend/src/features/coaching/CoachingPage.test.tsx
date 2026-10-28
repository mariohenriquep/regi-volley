import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { COACHING_URL, coaching, SESSION_ID, signedIn } from '@/test/fixtures';
import { apiError, recorder } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

type Line = ReturnType<typeof coaching>;

beforeEach(() => {
  // Wednesday 15 July 2026, 11:00 in Lisbon: this week's Monday is 13 July.
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-07-15T10:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllEnvs();
});

/** The staff's week: answers `weeks[weekOf]` (default: `current`) and remembers each `weekOf` asked for. */
function serveCoaching(current: Line[], weeks: Record<string, Line[]> = {}) {
  const asked: (string | null)[] = [];
  const state = { current };
  server.use(
    signedIn,
    http.get(COACHING_URL, ({ request }) => {
      const weekOf = new URL(request.url).searchParams.get('weekOf');
      asked.push(weekOf);
      return HttpResponse.json((weekOf && weeks[weekOf]) ?? state.current);
    }),
  );
  return { asked, state };
}

const cancelUrl = `/api/v1/sessions/${SESSION_ID}/cancellation`;
const capacityUrl = `/api/v1/sessions/${SESSION_ID}/capacity`;

const started = (overrides: Partial<Line> = {}) =>
  coaching({
    sessionId: 'a0000000-0000-0000-0000-000000000001',
    groupName: 'Wednesday morning',
    startsAt: '2026-07-15T08:00:00Z',
    endsAt: '2026-07-15T09:30:00Z',
    attendedCount: 5,
    noShowCount: 1,
    unmarkedCount: 2,
    confirmedCount: 8,
    waitlistCount: 0,
    canMarkAttendance: true,
    canCancel: true,
    canChangeCapacity: false,
    ...overrides,
  });

describe('the Coaching section in the navigation', () => {
  it('is offered to staff, and the request it makes is this week in Lisbon', async () => {
    // Arrange: 23:30 UTC on Sunday 19 July is already Monday 20 July in Lisbon
    vi.setSystemTime(new Date('2026-07-19T23:30:00Z'));
    vi.stubEnv('TZ', 'America/Los_Angeles');
    const { asked } = serveCoaching([]);

    // Act
    renderApp('/');

    // Assert
    const bar = await screen.findByRole('navigation', { name: 'Sections' });
    expect(await within(bar).findByRole('link', { name: 'Coaching' })).toHaveAttribute(
      'href',
      '/coaching',
    );
    expect(asked[0]).toBe('2026-07-20');
  });

  it('is not offered to a member, whom the API answers 403', async () => {
    // Arrange: the default handler is the member's 403
    server.use(
      signedIn,
      http.get('/api/v1/sessions', () => HttpResponse.json([])),
    );

    // Act
    renderApp('/');
    await screen.findByText('No sessions this week');

    // Assert
    const bar = screen.getByRole('navigation', { name: 'Sections' });
    expect(
      within(bar)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['Sessions', 'My plan', 'History']);
  });

  it('stays hidden when the check fails for another reason, and shows nothing about it', async () => {
    // Arrange
    server.use(
      signedIn,
      http.get('/api/v1/sessions', () => HttpResponse.json([])),
      http.get(COACHING_URL, () => apiError(500, 'INTERNAL_ERROR', 'boom')),
    );

    // Act
    renderApp('/');
    await screen.findByText('No sessions this week');

    // Assert
    await waitFor(() => {
      expect(screen.queryByRole('link', { name: 'Coaching' })).not.toBeInTheDocument();
    });
  });
});

describe('the week', () => {
  it('groups the sessions by Lisbon day with time, venue, status, seats and waitlist', async () => {
    // Arrange
    serveCoaching([
      started(),
      coaching({
        groupName: 'Thursday evening',
        confirmedCount: 8,
        capacity: 12,
        waitlistCount: 2,
      }),
    ]);

    // Act
    renderApp('/coaching');

    // Assert
    expect(await screen.findByRole('heading', { level: 1, name: 'Coaching' })).toBeInTheDocument();
    expect(
      await screen.findByRole('heading', { level: 2, name: 'Wednesday 15 July' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Thursday 16 July' })).toBeInTheDocument();
    const evening = screen.getByRole('article', { name: 'Thursday evening' });
    expect(evening).toHaveTextContent('20:30 – 22:00');
    expect(evening).toHaveTextContent('Pavilhão Central');
    expect(evening).toHaveTextContent('Scheduled');
    expect(evening).toHaveTextContent('8 of 12 confirmed');
    expect(evening).toHaveTextContent('2 on the waitlist');
    expect(screen.getByRole('article', { name: 'Wednesday morning' })).toHaveTextContent(
      '09:00 – 10:30',
    );
  });

  it('shows Lisbon times whatever the device time zone is', async () => {
    // Arrange: a device in Los Angeles
    vi.stubEnv('TZ', 'America/Los_Angeles');
    serveCoaching([coaching()]);

    // Act
    renderApp('/coaching');

    // Assert: 19:30 UTC in July is 20:30 in Lisbon
    expect(await screen.findByRole('article', { name: 'Thursday evening' })).toHaveTextContent(
      '20:30 – 22:00',
    );
  });

  it('shows what is attended, missed and still to mark once the API allows marking', async () => {
    // Arrange
    serveCoaching([started(), coaching()]);

    // Act
    renderApp('/coaching');

    // Assert
    const morning = await screen.findByRole('article', { name: 'Wednesday morning' });
    expect(morning).toHaveTextContent('5 attended · 1 no-show · 2 to mark');
    expect(screen.getByRole('article', { name: 'Thursday evening' })).not.toHaveTextContent(
      'attended',
    );
  });

  it('offers exactly the actions the flags allow', async () => {
    // Arrange
    serveCoaching([
      started({ canChangeCapacity: false }),
      coaching({ groupName: 'Open', canCancel: true, canChangeCapacity: true }),
      coaching({
        sessionId: 'a0000000-0000-0000-0000-000000000002',
        groupName: 'Locked',
        canCancel: false,
        canChangeCapacity: false,
      }),
    ]);

    // Act
    renderApp('/coaching');

    // Assert
    const marking = await screen.findByRole('article', { name: 'Wednesday morning' });
    expect(within(marking).getByRole('link', { name: /^Mark attendance/ })).toBeInTheDocument();
    expect(within(marking).getByRole('button', { name: /^Cancel session/ })).toBeInTheDocument();
    expect(
      within(marking).queryByRole('button', { name: /^Change seats/ }),
    ).not.toBeInTheDocument();
    const open = screen.getByRole('article', { name: 'Open' });
    expect(within(open).getByRole('link', { name: /^Roster/ })).toBeInTheDocument();
    expect(within(open).queryByRole('link', { name: /^Mark attendance/ })).not.toBeInTheDocument();
    expect(within(open).getByRole('button', { name: /^Change seats/ })).toBeInTheDocument();
    expect(within(open).getByRole('button', { name: /^Cancel session/ })).toBeInTheDocument();
    const locked = screen.getByRole('article', { name: 'Locked' });
    expect(within(locked).getByRole('link', { name: /^Roster/ })).toBeInTheDocument();
    expect(within(locked).queryByRole('button')).not.toBeInTheDocument();
  });

  it('shows a cancelled session as cancelled, without counts or actions', async () => {
    // Arrange
    serveCoaching([
      coaching({
        status: 'CANCELLED',
        confirmedCount: 0,
        waitlistCount: 0,
        unmarkedCount: 0,
        canCancel: false,
        canChangeCapacity: false,
      }),
    ]);

    // Act
    renderApp('/coaching');

    // Assert
    const card = await screen.findByRole('article', { name: 'Thursday evening' });
    expect(card).toHaveTextContent('Cancelled');
    expect(card).not.toHaveTextContent('confirmed');
    expect(within(card).queryByRole('button')).not.toBeInTheDocument();
    expect(within(card).queryByRole('link')).not.toBeInTheDocument();
  });

  it('links the roster and the attendance form to the session', async () => {
    // Arrange
    serveCoaching([started({ sessionId: SESSION_ID })]);

    // Act
    renderApp('/coaching');

    // Assert
    const link = await screen.findByRole('link', { name: /^Mark attendance/ });
    expect(link).toHaveAttribute('href', `/coaching/sessions/${SESSION_ID}`);
  });

  it('says so when there is nothing this week', async () => {
    // Arrange
    serveCoaching([]);

    // Act
    renderApp('/coaching');

    // Assert
    expect(await screen.findByText('No sessions this week')).toBeInTheDocument();
  });

  it('shows the API error with its reference and tries again on request', async () => {
    // Arrange: the shell's check and the page share one request; a first failure then a success
    let calls = 0;
    server.use(
      signedIn,
      http.get(COACHING_URL, () => {
        calls += 1;
        return calls <= 3
          ? apiError(500, 'INTERNAL_ERROR', 'internal detail')
          : HttpResponse.json([coaching()]);
      }),
    );

    // Act
    const { user } = renderApp('/coaching');
    const alert = await screen.findByRole('alert');

    // Assert: generic text for a 5xx, the request id for support
    expect(alert).toHaveTextContent('Something went wrong');
    expect(alert).toHaveTextContent('Reference: req-500');
    expect(alert).not.toHaveTextContent('internal detail');
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('article', { name: 'Thursday evening' })).toBeInTheDocument();
  });

  it('tells a member who opens the address that the section is for coaches and administrators', async () => {
    // Arrange: the default answer is 403
    server.use(signedIn);

    // Act
    renderApp('/coaching');

    // Assert
    expect(
      await screen.findByText('Coaching is for coaches and administrators.'),
    ).toBeInTheDocument();
  });
});

describe('the week navigator', () => {
  it('goes back to a past week, where yesterday’s attendance is still to mark, and forward again', async () => {
    // Arrange
    const { asked } = serveCoaching([coaching()], {
      '2026-07-06': [started({ groupName: 'Last week' })],
    });

    // Act
    const { user, router } = renderApp('/coaching');
    await screen.findByRole('article', { name: 'Thursday evening' });
    await user.click(screen.getByRole('button', { name: 'Previous week' }));

    // Assert
    expect(await screen.findByRole('article', { name: 'Last week' })).toBeInTheDocument();
    expect(router.state.location.search).toBe('?week=2026-07-06');
    expect(asked).toContain('2026-07-06');
    await user.click(screen.getByRole('button', { name: 'Next week' }));
    expect(await screen.findByRole('article', { name: 'Thursday evening' })).toBeInTheDocument();
    expect(router.state.location.search).toBe('');
  });

  it('opens the week named in the address and ignores a week it cannot read', async () => {
    // Arrange
    const { asked } = serveCoaching([coaching()], {
      '2026-07-20': [coaching({ groupName: 'Next' })],
    });

    // Act
    const { router } = renderApp('/coaching?week=2026-07-22');
    await screen.findByRole('article', { name: 'Next' });
    await router.navigate('/coaching?week=whenever');

    // Assert
    expect(await screen.findByRole('article', { name: 'Thursday evening' })).toBeInTheDocument();
    expect(asked).toContain('2026-07-20');
  });
});

describe('cancel a session', () => {
  async function openCancel(lines: Line[] = [coaching({ confirmedCount: 8, waitlistCount: 2 })]) {
    const { seen, record } = recorder();
    serveCoaching(lines);
    const app = renderApp('/coaching');
    const card = await screen.findByRole('article', { name: 'Thursday evening' });
    await app.user.click(within(card).getByRole('button', { name: /^Cancel session/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Cancel this session?' });
    return { ...app, seen, record, dialog, card };
  }

  it('warns how many members are told and refunded, and puts focus on the reason', async () => {
    // Arrange / Act
    const { dialog } = await openCancel();

    // Assert: 8 confirmed and 2 waiting
    expect(dialog).toHaveTextContent('10 members will be notified and their credits refunded.');
    expect(within(dialog).getByLabelText('Reason')).toHaveFocus();
  });

  it('asks for a reason before it sends anything', async () => {
    // Arrange
    const { dialog, user, seen, record } = await openCancel();
    server.use(
      http.post(cancelUrl, async ({ request }) => {
        await record(request);
        return HttpResponse.json({});
      }),
    );

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Cancel session' }));

    // Assert
    const reason = within(dialog).getByLabelText('Reason');
    expect(reason).toBeInvalid();
    expect(reason).toHaveAccessibleDescription('Enter the reason the members will be told.');
    expect(reason).toHaveFocus();
    expect(seen).toHaveLength(0);
  });

  it('refuses a reason that is only spaces', async () => {
    // Arrange
    const { dialog, user } = await openCancel();

    // Act
    await user.type(within(dialog).getByLabelText('Reason'), '   ');
    await user.click(within(dialog).getByRole('button', { name: 'Cancel session' }));

    // Assert
    expect(within(dialog).getByLabelText('Reason')).toBeInvalid();
  });

  it('sends the reason, then reports what the API did and refreshes the week', async () => {
    // Arrange
    const { dialog, user, seen, record } = await openCancel();
    server.use(
      http.post(cancelUrl, async ({ request }) => {
        await record(request);
        return HttpResponse.json({ bookingsCancelled: 10, creditsRefunded: 8, session: {} });
      }),
    );

    // Act
    await user.type(within(dialog).getByLabelText('Reason'), '  Court flooded ');
    server.use(
      http.get(COACHING_URL, () =>
        HttpResponse.json([
          coaching({ status: 'CANCELLED', confirmedCount: 0, waitlistCount: 0, canCancel: false }),
        ]),
      ),
    );
    await user.click(within(dialog).getByRole('button', { name: 'Cancel session' }));

    // Assert: the reason is trimmed; the notice has the API's numbers; the card turns cancelled
    const notice = await screen.findByRole('status');
    expect(notice).toHaveTextContent(
      'Session cancelled. 10 bookings were cancelled and 8 credits refunded.',
    );
    expect(JSON.parse(seen[0]?.body ?? '{}')).toEqual({ reason: 'Court flooded' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByRole('article', { name: 'Thursday evening' })).toHaveTextContent(
        'Cancelled',
      );
    });
  });

  it("shows the API's refusal in the dialog and keeps it open", async () => {
    // Arrange
    const { dialog, user } = await openCancel();
    server.use(
      http.post(cancelUrl, () =>
        apiError(
          422,
          'BUSINESS_RULE_VIOLATION',
          'The session cannot be cancelled: attendance was already marked',
        ),
      ),
    );

    // Act
    await user.type(within(dialog).getByLabelText('Reason'), 'Court flooded');
    await user.click(within(dialog).getByRole('button', { name: 'Cancel session' }));

    // Assert
    const alert = await within(dialog).findByRole('alert');
    expect(alert).toHaveTextContent('attendance was already marked');
    expect(alert).toHaveTextContent('Reference: req-422');
    expect(screen.getByRole('dialog', { name: 'Cancel this session?' })).toBeInTheDocument();
  });

  it('puts an API complaint about the reason on the field', async () => {
    // Arrange
    const { dialog, user } = await openCancel();
    server.use(
      http.post(cancelUrl, () =>
        apiError(422, 'INVALID_FIELD', 'A cancellation reason is required', { fields: ['reason'] }),
      ),
    );

    // Act
    await user.type(within(dialog).getByLabelText('Reason'), 'x');
    await user.click(within(dialog).getByRole('button', { name: 'Cancel session' }));

    // Assert
    await waitFor(() => {
      expect(within(dialog).getByLabelText('Reason')).toHaveAccessibleDescription(
        'A cancellation reason is required',
      );
    });
  });

  it('closes without sending on Keep session, and gives focus back to the button that opened it', async () => {
    // Arrange
    const { dialog, user, card } = await openCancel();

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Keep session' }));

    // Assert
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(within(card).getByRole('button', { name: /^Cancel session/ })).toHaveFocus();
  });

  it('says nobody needs telling when nobody has booked', async () => {
    // Arrange / Act
    const { dialog } = await openCancel([coaching({ confirmedCount: 0, waitlistCount: 0 })]);

    // Assert
    expect(dialog).toHaveTextContent('Nobody has booked this session, so no one will be notified.');
  });
});

describe('change the seats', () => {
  async function openCapacity() {
    const { seen, record } = recorder();
    serveCoaching([coaching({ capacity: 12, confirmedCount: 12, waitlistCount: 3 })]);
    const app = renderApp('/coaching');
    const card = await screen.findByRole('article', { name: 'Thursday evening' });
    await app.user.click(within(card).getByRole('button', { name: /^Change seats/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Change the seats' });
    return { ...app, seen, record, dialog };
  }

  it('starts from the current number and explains the waitlist', async () => {
    // Arrange / Act
    const { dialog } = await openCapacity();

    // Assert
    const input = within(dialog).getByLabelText('Seats');
    expect(input).toHaveValue(12);
    expect(input).toHaveFocus();
    expect(dialog).toHaveTextContent(
      'Raising the seats moves people up from the waitlist (3 waiting).',
    );
  });

  it('sends the number and reports how many were promoted', async () => {
    // Arrange
    const { dialog, user, seen, record } = await openCapacity();
    server.use(
      http.put(capacityUrl, async ({ request }) => {
        await record(request);
        return HttpResponse.json({ promotedCount: 2, session: { capacity: 14 } });
      }),
    );

    // Act
    const input = within(dialog).getByLabelText('Seats');
    await user.clear(input);
    await user.type(input, '14');
    await user.click(within(dialog).getByRole('button', { name: 'Save seats' }));

    // Assert
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Seats changed to 14. 2 people moved up from the waitlist.',
    );
    expect(JSON.parse(seen[0]?.body ?? '{}')).toEqual({ capacity: 14 });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('says when nobody moved up', async () => {
    // Arrange
    const { dialog, user } = await openCapacity();
    server.use(
      http.put(capacityUrl, () =>
        HttpResponse.json({ promotedCount: 0, session: { capacity: 12 } }),
      ),
    );

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Save seats' }));

    // Assert
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Seats changed to 12. Nobody moved up.',
    );
  });

  it("shows the API's 422 on the field when the number is below the confirmed", async () => {
    // Arrange
    const { dialog, user } = await openCapacity();
    server.use(
      http.put(capacityUrl, () =>
        apiError(
          422,
          'BUSINESS_RULE_VIOLATION',
          'Capacity (5) cannot be lower than the number of confirmed bookings (12)',
        ),
      ),
    );

    // Act
    const input = within(dialog).getByLabelText('Seats');
    await user.clear(input);
    await user.type(input, '5');
    await user.click(within(dialog).getByRole('button', { name: 'Save seats' }));

    // Assert
    await waitFor(() => {
      expect(input).toBeInvalid();
    });
    expect(input).toHaveAccessibleDescription(
      /cannot be lower than the number of confirmed bookings/,
    );
    expect(input).toHaveFocus();
    expect(screen.getByRole('dialog', { name: 'Change the seats' })).toBeInTheDocument();
  });

  it('checks the number is a whole number from 1 to 1000 before it sends anything', async () => {
    // Arrange
    const { dialog, user, seen } = await openCapacity();
    const input = within(dialog).getByLabelText('Seats');

    // Act
    await user.clear(input);
    await user.click(within(dialog).getByRole('button', { name: 'Save seats' }));

    // Assert
    expect(input).toBeInvalid();
    expect(input).toHaveAccessibleDescription('Enter a whole number from 1 to 1000.');
    await user.type(input, '1001');
    await user.click(within(dialog).getByRole('button', { name: 'Save seats' }));
    expect(input).toBeInvalid();
    expect(seen).toHaveLength(0);
  });

  it('shows another refusal in the dialog', async () => {
    // Arrange
    const { dialog, user } = await openCapacity();
    server.use(
      http.put(capacityUrl, () =>
        apiError(
          409,
          'CONFLICT',
          'The data was changed by someone else in the meantime. Reload and try again',
        ),
      ),
    );

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Save seats' }));

    // Assert
    const alert = await within(dialog).findByRole('alert');
    expect(alert).toHaveTextContent('changed by someone else');
  });
});
