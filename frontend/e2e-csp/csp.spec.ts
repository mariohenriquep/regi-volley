import { expect, test, type Page } from '@playwright/test';

/**
 * The production build under the documented Content-Security-Policy (`style-src 'self'`, no inline styles or scripts) with the
 * API mocked: the Book dialog, the Cancel dialog and the Account menu must open, work and close without one violation.
 * Radix modals add a `<style>` element for the scroll lock unless told otherwise, which this policy would block.
 */
const HOUR = 3_600_000;
const SESSION_ID = '33333333-3333-3333-3333-333333333333';
const BOOKING_ID = '44444444-4444-4444-4444-444444444444';

declare global {
  interface Window {
    __violations: string[];
  }
}

async function mockApi(page: Page) {
  const startsAt = new Date(Math.ceil((Date.now() + 30 * HOUR) / HOUR) * HOUR);
  let booked = false;
  const session = () => ({
    sessionId: SESSION_ID,
    trainingGroupId: '55555555-5555-5555-5555-555555555555',
    groupName: 'CSP evening',
    startsAt: startsAt.toISOString(),
    endsAt: new Date(startsAt.getTime() + 1.5 * HOUR).toISOString(),
    bookingOpensAt: new Date(Date.now() - HOUR).toISOString(),
    capacity: 12,
    freeSeats: booked ? 11 : 12,
    waitlistSize: 0,
    venueName: 'CSP Pavilion',
    freeCancellationUntil: new Date(startsAt.getTime() - 6 * HOUR).toISOString(),
    ...(booked ? { myBookingStatus: 'CONFIRMED', myBookingId: BOOKING_ID } : {}),
  });
  await page.route('**/api/v1/**', async (route) => {
    const { pathname } = new URL(route.request().url());
    const method = route.request().method();
    const json = (body: unknown, status = 200) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (pathname.endsWith('/auth/refresh')) {
      return json({ accessToken: 'test-csp-token-fixture', tokenType: 'Bearer', expiresIn: 600 });
    }
    if (pathname.endsWith('/sessions')) return json([session()]);
    if (pathname.endsWith('/me/plan')) {
      return json({
        asOf: '2026-07-15',
        subscriptions: [
          {
            subscriptionId: '66666666-6666-6666-6666-666666666666',
            planName: 'Pack of 10',
            type: 'PACK',
            startDate: '2020-01-01',
            endDate: '2099-12-31',
            paymentStatus: 'PAID',
            remaining: 7,
          },
        ],
      });
    }
    if (method === 'POST' && pathname.endsWith('/bookings')) {
      booked = true;
      return json({ bookingId: BOOKING_ID, sessionId: SESSION_ID, status: 'CONFIRMED' }, 201);
    }
    if (method === 'POST' && pathname.endsWith('/cancellation')) {
      booked = false;
      return json({
        bookingId: BOOKING_ID,
        sessionId: SESSION_ID,
        late: false,
        creditRefunded: true,
        promotedCount: 0,
      });
    }
    return json({ code: 'NOT_FOUND', message: 'Not found' }, 404);
  });
}

test('the dialogs and the account menu raise no Content-Security-Policy violation', async ({
  page,
}) => {
  // Arrange: record every violation the browser reports, and every console error that mentions the policy
  const consoleErrors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });
  await page.addInitScript(() => {
    window.__violations = [];
    document.addEventListener('securitypolicyviolation', (event) => {
      window.__violations.push(`${event.violatedDirective} blocked ${event.blockedURI}`);
    });
  });
  await mockApi(page);
  await page.goto('/');
  const card = page.getByRole('article', { name: 'CSP evening' });
  await expect(card).toBeVisible();

  // Act: Book dialog (open, book), Cancel dialog (open, keep, open, cancel), Account menu (open, close)
  await card.getByRole('button', { name: /^Book/ }).tap();
  const book = page.getByRole('dialog', { name: 'Book this session?' });
  await expect(book).toContainText("You'll use 1 credit (7 left).");
  // The scroll lock still works: its rule is in the stylesheet, not in an injected <style>
  await expect(page.locator('body')).toHaveCSS('overflow', 'hidden');
  await book.getByRole('button', { name: 'Confirm booking' }).tap();
  await expect(page.getByText("You're booked and confirmed.")).toBeVisible();

  await card.getByRole('button', { name: /^Cancel/ }).tap();
  const cancel = page.getByRole('alertdialog', { name: 'Cancel your booking?' });
  await expect(cancel).toBeVisible();
  await cancel.getByRole('button', { name: 'Keep booking' }).tap();
  await expect(cancel).toBeHidden();
  await card.getByRole('button', { name: /^Cancel/ }).tap();
  await page.getByRole('alertdialog').getByRole('button', { name: 'Cancel booking' }).tap();
  await expect(page.getByText(/Booking cancelled/)).toBeVisible();

  await page.getByRole('button', { name: 'Account' }).tap();
  await expect(page.getByRole('menuitem', { name: 'Sign out', exact: true })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('menu')).toBeHidden();

  // Assert: the policy was in force, and nothing was blocked
  const response = await page.request.get('/');
  expect(response.headers()['content-security-policy']).toContain("style-src 'self'");
  expect(await page.evaluate(() => window.__violations)).toEqual([]);
  expect(consoleErrors.filter((text) => /content security policy/i.test(text))).toEqual([]);
});
