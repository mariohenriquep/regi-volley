import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { myPlan, planHandler, signedIn, subscription } from '@/test/fixtures';
import { apiError } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

describe('My plan', () => {
  it('shows a pack: name, validity, credits left and that it is paid', async () => {
    // Arrange
    server.use(signedIn, planHandler([subscription()]));

    // Act
    renderApp('/me/plan');

    // Assert
    expect(await screen.findByRole('heading', { level: 1, name: 'My plan' })).toBeInTheDocument();
    const card = within(await screen.findByRole('article', { name: 'Pack of 10' }));
    expect(card.getByText('Pack of credits')).toBeInTheDocument();
    expect(card.getByText('1 July 2026 – 29 September 2026')).toBeInTheDocument();
    expect(card.getByText('7 credits left')).toBeInTheDocument();
    expect(card.getByText('Paid')).toBeInTheDocument();
  });

  it.each([
    [1, '1 credit left'],
    [0, '0 credits left'],
  ])('says "%s credit(s)" in the singular and plural correctly', async (remaining, text) => {
    // Arrange
    server.use(signedIn, planHandler([subscription({ remaining })]));

    // Act
    renderApp('/me/plan');

    // Assert
    expect(await screen.findByText(text)).toBeInTheDocument();
  });

  it('shows the sessions left this week for a weekly plan and "unlimited" for a monthly one', async () => {
    // Arrange
    server.use(
      signedIn,
      planHandler([
        subscription({
          subscriptionId: 'a',
          planName: 'Twice a week',
          type: 'MONTHLY_N_PER_WEEK',
          remaining: 1,
        }),
        subscription({
          subscriptionId: 'b',
          planName: 'Open month',
          type: 'MONTHLY_UNLIMITED',
          remaining: undefined,
        }),
      ]),
    );

    // Act
    renderApp('/me/plan');

    // Assert
    const weekly = within(await screen.findByRole('article', { name: 'Twice a week' }));
    expect(weekly.getByText('Monthly, a fixed number of sessions a week')).toBeInTheDocument();
    expect(weekly.getByText('1 session left this week')).toBeInTheDocument();
    const unlimited = within(screen.getByRole('article', { name: 'Open month' }));
    expect(unlimited.getByText('Unlimited sessions')).toBeInTheDocument();
  });

  it('warns clearly that an overdue payment blocks booking', async () => {
    // Arrange
    server.use(signedIn, planHandler([subscription({ paymentStatus: 'OVERDUE' })]));

    // Act
    renderApp('/me/plan');

    // Assert
    const card = within(await screen.findByRole('article', { name: 'Pack of 10' }));
    expect(card.getByText('Overdue')).toBeInTheDocument();
    expect(card.getByRole('alert')).toHaveTextContent(
      "You can't book while your payment is overdue — contact your association.",
    );
  });

  it('shows a pending payment without alarm', async () => {
    // Arrange
    server.use(signedIn, planHandler([subscription({ paymentStatus: 'PENDING' })]));

    // Act
    renderApp('/me/plan');

    // Assert
    const card = within(await screen.findByRole('article', { name: 'Pack of 10' }));
    expect(card.getByText('Payment pending')).toBeInTheDocument();
    expect(card.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('says when an upcoming plan starts, using the day the API computed it for', async () => {
    // Arrange
    server.use(
      signedIn,
      http.get('/api/v1/me/plan', () =>
        HttpResponse.json({
          ...myPlan([subscription({ startDate: '2026-08-01', endDate: '2026-08-31' })]),
          asOf: '2026-07-15',
        }),
      ),
    );

    // Act
    renderApp('/me/plan');

    // Assert
    expect(await screen.findByText('Starts on 1 August 2026')).toBeInTheDocument();
  });

  it.each(['constructor', 'toString'])(
    'shows a plan type and a payment status it does not know (%s) as the API wrote them',
    async (word) => {
      // Arrange
      server.use(signedIn, planHandler([subscription({ type: word, paymentStatus: word })]));

      // Act
      renderApp('/me/plan');

      // Assert
      const card = within(await screen.findByRole('article', { name: 'Pack of 10' }));
      expect(card.getAllByText(word)).toHaveLength(2);
    },
  );

  it('has an empty state', async () => {
    // Arrange
    server.use(signedIn, planHandler([]));

    // Act
    renderApp('/me/plan');

    // Assert
    expect(await screen.findByText('You have no current plan')).toBeInTheDocument();
    expect(screen.getByText(/Ask your association to assign you one/)).toBeInTheDocument();
  });

  it('has a loading state and an error state with a retry', async () => {
    // Arrange
    let fail = true;
    server.use(
      signedIn,
      http.get('/api/v1/me/plan', () =>
        fail ? apiError(500, 'INTERNAL_ERROR', 'boom') : HttpResponse.json(myPlan()),
      ),
    );

    // Act
    const { user } = renderApp('/me/plan');
    expect(await screen.findByRole('status', { name: 'Loading your plan' })).toBeInTheDocument();
    const alert = await screen.findByRole('alert');
    fail = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    // Assert
    expect(alert).toHaveTextContent('Something went wrong. Please try again.');
    expect(await screen.findByRole('article', { name: 'Pack of 10' })).toBeInTheDocument();
  });

  it('is for signed-in members only', async () => {
    // Arrange
    server.use(http.post('/api/v1/auth/refresh', () => apiError(401, 'UNAUTHENTICATED', 'no')));

    // Act
    const { router } = renderApp('/me/plan');

    // Assert
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });
});
