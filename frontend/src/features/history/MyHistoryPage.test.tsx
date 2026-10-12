import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { historyEntry, signedIn } from '@/test/fixtures';
import { apiError } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

afterEach(() => {
  vi.unstubAllEnvs();
});

const history = (entries: unknown[], extra: Record<string, unknown> = {}) =>
  http.get('/api/v1/me/history', () =>
    HttpResponse.json({
      entries,
      noShowsThisMonth: 0,
      noShowLimit: 3,
      nearLimit: false,
      ...extra,
    }),
  );

describe('My history', () => {
  it('lists the last sessions in the order the API gave (newest first) with Lisbon time and status', async () => {
    // Arrange
    vi.stubEnv('TZ', 'Pacific/Auckland');
    server.use(
      signedIn,
      history([
        historyEntry({
          bookingId: 'b1',
          startsAt: '2026-07-07T19:30:00Z',
          endsAt: '2026-07-07T21:00:00Z',
          status: 'ATTENDED',
        }),
        historyEntry({
          bookingId: 'b2',
          startsAt: '2026-06-30T19:30:00Z',
          endsAt: '2026-06-30T21:00:00Z',
          status: 'NO_SHOW',
        }),
      ]),
    );

    // Act
    renderApp('/me/history');

    // Assert
    expect(await screen.findByRole('heading', { level: 1, name: 'History' })).toBeInTheDocument();
    const items = await screen.findAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(within(items[0] as HTMLElement).getByText('Tuesday 7 July')).toBeInTheDocument();
    expect(within(items[0] as HTMLElement).getByText('20:30 – 22:00')).toBeInTheDocument();
    expect(within(items[0] as HTMLElement).getByText('Attended')).toBeInTheDocument();
    expect(within(items[1] as HTMLElement).getByText('Tuesday 30 June')).toBeInTheDocument();
    expect(within(items[1] as HTMLElement).getByText('No-show')).toBeInTheDocument();
  });

  it.each([
    ['CONFIRMED', 'Confirmed'],
    ['WAITLISTED', 'Waitlisted'],
    ['ATTENDED', 'Attended'],
    ['NO_SHOW', 'No-show'],
  ])('names the status %s as %s', async (status, label) => {
    // Arrange
    server.use(signedIn, history([historyEntry({ status })]));

    // Act
    renderApp('/me/history');

    // Assert
    expect(await screen.findByText(label)).toBeInTheDocument();
  });

  it.each(['constructor', 'toString', 'SOMETHING_NEW'])(
    'shows a status it does not know (%s) as the API wrote it',
    async (status) => {
      // Arrange
      server.use(signedIn, history([historyEntry({ status })]));

      // Act
      renderApp('/me/history');

      // Assert
      expect(await screen.findByText(status)).toBeInTheDocument();
    },
  );

  it("shows this month's no-shows against the limit, without a warning when far from it", async () => {
    // Arrange
    server.use(signedIn, history([historyEntry()], { noShowsThisMonth: 1 }));

    // Act
    renderApp('/me/history');

    // Assert
    expect(await screen.findByText('1 of 3 no-shows this month')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('warns when the member is near the no-show limit, and says nothing is blocked', async () => {
    // Arrange
    server.use(
      signedIn,
      history([historyEntry({ status: 'NO_SHOW' })], { noShowsThisMonth: 2, nearLimit: true }),
    );

    // Act
    renderApp('/me/history');

    // Assert
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent("You're close to this month's no-show limit (2 of 3).");
    expect(alert).toHaveTextContent('Please cancel in time when you cannot come.');
  });

  it('says so when the limit is reached', async () => {
    // Arrange
    server.use(signedIn, history([], { noShowsThisMonth: 3, nearLimit: true }));

    // Act
    renderApp('/me/history');

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent(
      "You've reached this month's no-show limit (3 of 3).",
    );
  });

  it('ignores entries it cannot read instead of breaking', async () => {
    // Arrange
    server.use(signedIn, history([historyEntry(), { status: 'ATTENDED' }, 'junk']));

    // Act
    renderApp('/me/history');

    // Assert
    expect(await screen.findAllByRole('listitem')).toHaveLength(1);
  });

  it('has an empty state, a loading state and an error state with a retry', async () => {
    // Arrange
    let mode: 'fail' | 'empty' = 'fail';
    server.use(
      signedIn,
      http.get('/api/v1/me/history', () =>
        mode === 'fail'
          ? apiError(500, 'INTERNAL_ERROR', 'boom')
          : HttpResponse.json({
              entries: [],
              noShowsThisMonth: 0,
              noShowLimit: 3,
              nearLimit: false,
            }),
      ),
    );

    // Act
    const { user } = renderApp('/me/history');
    expect(await screen.findByRole('status', { name: 'Loading your history' })).toBeInTheDocument();
    const alert = await screen.findByRole('alert');
    mode = 'empty';
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    // Assert
    expect(alert).toHaveTextContent('Something went wrong. Please try again.');
    expect(await screen.findByText('No sessions in the last 3 months')).toBeInTheDocument();
  });
});
