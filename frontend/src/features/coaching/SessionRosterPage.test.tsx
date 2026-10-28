import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  COACHING_URL,
  coaching,
  rosterEntry,
  rosterOf,
  SESSION_ID,
  signedIn,
} from '@/test/fixtures';
import { apiError, recorder } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

type Line = ReturnType<typeof coaching>;
type Roster = ReturnType<typeof rosterOf>;

beforeEach(() => {
  // Thursday 16 July 2026, 22:00 in Lisbon: the 20:30 session has started
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-07-16T21:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllEnvs();
});

const rosterUrl = `/api/v1/sessions/${SESSION_ID}/roster`;
const attendanceUrl = `/api/v1/sessions/${SESSION_ID}/attendance`;
const cancelUrl = `/api/v1/sessions/${SESSION_ID}/cancellation`;
const capacityUrl = `/api/v1/sessions/${SESSION_ID}/capacity`;

const ANA = 'b0000000-0000-0000-0000-00000000000a';
const BRUNO = 'b0000000-0000-0000-0000-00000000000b';
const CARLA = 'b0000000-0000-0000-0000-00000000000c';
const DINIS = 'b0000000-0000-0000-0000-00000000000d';
const EVA = 'b0000000-0000-0000-0000-00000000000e';

const seats = [
  rosterEntry({ bookingId: ANA, memberName: 'Ana Silva' }),
  rosterEntry({ bookingId: BRUNO, memberName: 'Bruno Costa' }),
  rosterEntry({ bookingId: CARLA, memberName: 'Carla Dias' }),
];
const waitlist = [
  rosterEntry({ bookingId: DINIS, memberName: 'Dinis Rocha', status: 'WAITLISTED' }),
  rosterEntry({ bookingId: EVA, memberName: 'Eva Pinto', status: 'WAITLISTED' }),
];

const markable = (overrides: Partial<Line> = {}) =>
  coaching({
    confirmedCount: 3,
    waitlistCount: 2,
    unmarkedCount: 3,
    canMarkAttendance: true,
    canCancel: true,
    canChangeCapacity: false,
    ...overrides,
  });

/** The roster and the staff's week for the session's own Lisbon week (13 July). */
function serveSession(roster: Roster = rosterOf({ seats, waitlist }), line: Line = markable()) {
  const asked: (string | null)[] = [];
  const state = { roster, line };
  server.use(
    signedIn,
    http.get(rosterUrl, () => HttpResponse.json(state.roster)),
    http.get(COACHING_URL, ({ request }) => {
      asked.push(new URL(request.url).searchParams.get('weekOf'));
      return HttpResponse.json([state.line]);
    }),
  );
  return { asked, state };
}

const page = `/coaching/sessions/${SESSION_ID}`;

describe('the roster', () => {
  it('lists the seats and then the waitlist in the order the API gave them, names only', async () => {
    // Arrange
    serveSession();

    // Act
    renderApp(page);

    // Assert
    const seatList = await screen.findByRole('list', { name: 'Seats' });
    expect(
      within(seatList)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual([
      expect.stringContaining('Ana Silva'),
      expect.stringContaining('Bruno Costa'),
      expect.stringContaining('Carla Dias'),
    ]);
    const waiting = screen.getByRole('list', { name: 'Waitlist' });
    expect(
      within(waiting)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual([expect.stringContaining('Dinis Rocha'), expect.stringContaining('Eva Pinto')]);
    expect(within(waiting).getAllByRole('listitem')[0]).toHaveTextContent('1');
    expect(screen.getByRole('heading', { level: 2, name: 'Seats · 3 of 12' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Waitlist · 2' })).toBeInTheDocument();
  });

  it('shows the group, the Lisbon time, the venue and the counts of the session', async () => {
    // Arrange: a device in Los Angeles
    vi.stubEnv('TZ', 'America/Los_Angeles');
    serveSession();

    // Act
    renderApp(page);

    // Assert
    const summary = await screen.findByRole('region', { name: 'Thursday evening' });
    expect(summary).toHaveTextContent('20:30 – 22:00');
    expect(summary).toHaveTextContent('Pavilhão Central');
    expect(summary).toHaveTextContent('3 of 12 confirmed');
  });

  it('asks for the staff week of the session itself, not of today, and links back to it', async () => {
    // Arrange: the session is in the week of 6 July, today is in the week of 13 July
    vi.setSystemTime(new Date('2026-07-16T21:00:00Z'));
    const { asked } = serveSession(
      rosterOf({
        startsAt: '2026-07-08T19:30:00Z',
        endsAt: '2026-07-08T21:00:00Z',
        seats,
        waitlist,
      }),
    );

    // Act
    renderApp(page);

    // Assert
    const back = await screen.findByRole('link', { name: 'Back to Coaching' });
    expect(back).toHaveAttribute('href', '/coaching?week=2026-07-06');
    expect(asked).toContain('2026-07-06');
  });

  it('shows the roster read-only, with a reason, while the API does not allow marking', async () => {
    // Arrange: not started yet
    serveSession(
      undefined,
      coaching({ canMarkAttendance: false, confirmedCount: 3, unmarkedCount: 3 }),
    );

    // Act
    renderApp(page);

    // Assert
    expect(
      await screen.findByText('Attendance can be marked once the session has started.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Save attendance/ })).not.toBeInTheDocument();
    expect(screen.getByText('Ana Silva')).toBeInTheDocument();
  });

  it('shows who was marked and offers no toggle for them', async () => {
    // Arrange
    serveSession(
      rosterOf({
        seats: [
          rosterEntry({ bookingId: ANA, memberName: 'Ana Silva', status: 'ATTENDED' }),
          rosterEntry({ bookingId: BRUNO, memberName: 'Bruno Costa', status: 'NO_SHOW' }),
          rosterEntry({ bookingId: CARLA, memberName: 'Carla Dias' }),
        ],
        waitlist: [],
      }),
    );

    // Act
    renderApp(page);

    // Assert
    const list = await screen.findByRole('list', { name: 'Seats' });
    const [ana, bruno, carla] = within(list).getAllByRole('listitem');
    expect(ana).toHaveTextContent('Attended');
    expect(bruno).toHaveTextContent('No-show');
    expect(within(ana as HTMLElement).queryByRole('radio')).not.toBeInTheDocument();
    expect(within(bruno as HTMLElement).queryByRole('radio')).not.toBeInTheDocument();
    expect(within(carla as HTMLElement).getAllByRole('radio')).toHaveLength(2);
  });

  it('says what went wrong and offers a retry when the roster cannot be read', async () => {
    // Arrange
    server.use(
      signedIn,
      http.get(rosterUrl, () => apiError(404, 'NOT_FOUND', 'Not found')),
    );

    // Act
    renderApp(page);

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent('Not found');
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('is refused for a member, in the API’s words', async () => {
    // Arrange
    server.use(
      signedIn,
      http.get(rosterUrl, () =>
        apiError(
          403,
          'NOT_ALLOWED',
          'Only the coach of the session or an administrator can see who is in it',
        ),
      ),
    );

    // Act
    renderApp(page);

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent('Only the coach of the session');
  });
});

describe('marking attendance', () => {
  async function open() {
    const { seen, record } = recorder();
    const served = serveSession();
    const app = renderApp(page);
    await screen.findByRole('list', { name: 'Seats' });
    // The flags come with the second request
    await screen.findByRole('button', { name: 'Mark everyone as attended' });
    return { ...app, seen, record, ...served };
  }

  const toggle = (name: string, mark: 'Attended' | 'No-show') =>
    within(screen.getByRole('group', { name })).getByRole('radio', { name: mark });

  it('gives every seat holder two labelled big toggles', async () => {
    // Arrange / Act
    await open();

    // Assert
    for (const name of ['Ana Silva', 'Bruno Costa', 'Carla Dias']) {
      expect(toggle(name, 'Attended')).not.toBeChecked();
      expect(toggle(name, 'No-show')).not.toBeChecked();
    }
    expect(screen.getAllByRole('radio')).toHaveLength(6);
  });

  it('sends one batch with exactly the marks chosen, and reports what the API saved', async () => {
    // Arrange
    const { user, seen, record, state } = await open();
    server.use(
      http.put(attendanceUrl, async ({ request }) => {
        await record(request);
        state.roster = rosterOf({
          seats: [
            rosterEntry({ bookingId: ANA, memberName: 'Ana Silva', status: 'ATTENDED' }),
            rosterEntry({ bookingId: BRUNO, memberName: 'Bruno Costa', status: 'NO_SHOW' }),
            rosterEntry({ bookingId: CARLA, memberName: 'Carla Dias' }),
          ],
          waitlist,
        });
        state.line = markable({ attendedCount: 1, noShowCount: 1, unmarkedCount: 1 });
        return HttpResponse.json({ session: {}, warnedCount: 1 });
      }),
    );

    // Act: Ana attended, Bruno a no-show, Carla left unmarked
    await user.click(toggle('Ana Silva', 'Attended'));
    await user.click(toggle('Bruno Costa', 'No-show'));
    await user.click(screen.getByRole('button', { name: 'Save attendance (2)' }));

    // Assert
    const notice = await screen.findByRole('status');
    expect(notice).toHaveTextContent(
      'Attendance saved: 1 attended, 1 no-show. 1 member reached the no-show limit and was warned.',
    );
    expect(notice).toHaveFocus();
    expect(seen).toHaveLength(1);
    expect(seen[0]?.method).toBe('PUT');
    expect(JSON.parse(seen[0]?.body ?? '{}')).toEqual({
      entries: [
        { bookingId: ANA, mark: 'ATTENDED' },
        { bookingId: BRUNO, mark: 'NO_SHOW' },
      ],
    });
    // The roster and the counts are read again
    await waitFor(() => {
      expect(screen.getByRole('region', { name: 'Thursday evening' })).toHaveTextContent(
        '1 attended · 1 no-show · 1 to mark',
      );
    });
    expect(screen.getAllByRole('radio')).toHaveLength(2);
  });

  it('lets one tap mark everyone as attended, and a toggle then correct a single person', async () => {
    // Arrange
    const { user, seen, record } = await open();
    server.use(
      http.put(attendanceUrl, async ({ request }) => {
        await record(request);
        return HttpResponse.json({ session: {}, warnedCount: 0 });
      }),
    );

    // Act
    await user.click(screen.getByRole('button', { name: 'Mark everyone as attended' }));
    await user.click(toggle('Carla Dias', 'No-show'));
    await user.click(screen.getByRole('button', { name: 'Save attendance (3)' }));

    // Assert
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Attendance saved: 2 attended, 1 no-show.',
    );
    expect(JSON.parse(seen[0]?.body ?? '{}')).toEqual({
      entries: [
        { bookingId: ANA, mark: 'ATTENDED' },
        { bookingId: BRUNO, mark: 'ATTENDED' },
        { bookingId: CARLA, mark: 'NO_SHOW' },
      ],
    });
  });

  it('cannot save before anything is chosen', async () => {
    // Arrange / Act
    await open();

    // Assert
    expect(screen.getByRole('button', { name: 'Save attendance' })).toBeDisabled();
  });

  it('works from the keyboard: arrows move between the two answers of a person', async () => {
    // Arrange
    const { user } = await open();

    // Act
    toggle('Ana Silva', 'Attended').focus();
    await user.keyboard('{ArrowRight}');

    // Assert: the radio group behaviour of the browser, kept by using real radios
    expect(toggle('Ana Silva', 'No-show')).toBeChecked();
    expect(screen.getByRole('button', { name: 'Save attendance (1)' })).toBeEnabled();
  });

  it('shows the API’s refusal, keeps what was chosen and lets the coach try again', async () => {
    // Arrange
    const { user, seen, record } = await open();
    let refused = true;
    server.use(
      http.put(attendanceUrl, async ({ request }) => {
        await record(request);
        if (refused) {
          return apiError(
            422,
            'BUSINESS_RULE_VIOLATION',
            'Attendance cannot be marked before the session starts',
          );
        }
        return HttpResponse.json({ session: {}, warnedCount: 0 });
      }),
    );

    // Act
    await user.click(toggle('Ana Silva', 'Attended'));
    await user.click(screen.getByRole('button', { name: 'Save attendance (1)' }));

    // Assert: nothing was saved, the choice is still there
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('before the session starts');
    expect(alert).toHaveTextContent('Reference: req-422');
    expect(toggle('Ana Silva', 'Attended')).toBeChecked();
    refused = false;
    await user.click(screen.getByRole('button', { name: 'Save attendance (1)' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Attendance saved');
    expect(seen).toHaveLength(2);
  });
});

describe('staff actions from the roster', () => {
  it('cancels the session with a reason and shows the outcome', async () => {
    // Arrange
    const { seen, record } = recorder();
    serveSession();
    server.use(
      http.post(cancelUrl, async ({ request }) => {
        await record(request);
        return HttpResponse.json({ bookingsCancelled: 5, creditsRefunded: 3, session: {} });
      }),
    );
    const { user } = renderApp(page);

    // Act
    await user.click(await screen.findByRole('button', { name: /^Cancel session/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Cancel this session?' });
    expect(dialog).toHaveTextContent('5 members will be notified and their credits refunded.');
    await user.type(within(dialog).getByLabelText('Reason'), 'No referee');
    await user.click(within(dialog).getByRole('button', { name: 'Cancel session' }));

    // Assert
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Session cancelled. 5 bookings were cancelled and 3 credits refunded.',
    );
    expect(JSON.parse(seen[0]?.body ?? '{}')).toEqual({ reason: 'No referee' });
  });

  it('changes the seats and shows how many moved up', async () => {
    // Arrange
    serveSession(undefined, markable({ canChangeCapacity: true, canMarkAttendance: false }));
    server.use(
      http.put(capacityUrl, () =>
        HttpResponse.json({ promotedCount: 2, session: { capacity: 14 } }),
      ),
    );
    const { user } = renderApp(page);

    // Act
    await user.click(await screen.findByRole('button', { name: /^Change seats/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Change the seats' });
    const input = within(dialog).getByLabelText('Seats');
    await user.clear(input);
    await user.type(input, '14');
    await user.click(within(dialog).getByRole('button', { name: 'Save seats' }));

    // Assert
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Seats changed to 14. 2 people moved up from the waitlist.',
    );
  });

  it('offers no staff action for a cancelled session, whatever it lists', async () => {
    // Arrange
    serveSession(
      rosterOf({ status: 'CANCELLED', seats: [], waitlist: [] }),
      coaching({
        status: 'CANCELLED',
        confirmedCount: 0,
        waitlistCount: 0,
        canCancel: false,
        canChangeCapacity: false,
      }),
    );

    // Act
    renderApp(page);

    // Assert
    expect(await screen.findByRole('region', { name: 'Thursday evening' })).toHaveTextContent(
      'Cancelled',
    );
    expect(
      screen.queryByRole('button', { name: /Cancel session|Change seats/ }),
    ).not.toBeInTheDocument();
    expect(screen.getByText('Nobody holds a seat.')).toBeInTheDocument();
  });
});
