import { expect, test, type Page } from '@playwright/test';

/**
 * The production build under the documented Content-Security-Policy (`style-src 'self'`, no inline styles or scripts) with the
 * API mocked: the Book dialog, the Cancel dialog and the Account menu must open, work and close without one violation, and so
 * must the coaching screens (cancel-session and seats dialogs, the roster with its attendance toggles).
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
    // A plain member: the shell reads the roles from /me and offers no Coaching section
    if (pathname.endsWith('/api/v1/me')) {
      return json({ memberId: BOOKING_ID, associationId: SESSION_ID, roles: ['MEMBER'] });
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

test('the coaching dialogs and the roster raise no Content-Security-Policy violation', async ({
  page,
}) => {
  // Arrange: a coach whose session has started, and every violation the browser reports
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
  const startsAt = new Date(Math.ceil((Date.now() - 2 * HOUR) / HOUR) * HOUR);
  const endsAt = new Date(startsAt.getTime() + 1.5 * HOUR);
  const line = {
    sessionId: SESSION_ID,
    trainingGroupId: '55555555-5555-5555-5555-555555555555',
    groupName: 'CSP coaching',
    venueName: 'CSP Pavilion',
    coachId: '22222222-2222-2222-2222-222222222222',
    startsAt: startsAt.toISOString(),
    endsAt: endsAt.toISOString(),
    status: 'SCHEDULED',
    capacity: 12,
    confirmedCount: 1,
    waitlistCount: 0,
    attendedCount: 0,
    noShowCount: 0,
    unmarkedCount: 1,
    canMarkAttendance: true,
    canCancel: true,
    canChangeCapacity: true,
  };
  await page.route('**/api/v1/**', async (route) => {
    const { pathname } = new URL(route.request().url());
    const method = route.request().method();
    const json = (body: unknown, status = 200) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (pathname.endsWith('/auth/refresh')) {
      return json({ accessToken: 'test-csp-token-fixture', tokenType: 'Bearer', expiresIn: 600 });
    }
    if (pathname.endsWith('/api/v1/me')) {
      return json({ memberId: BOOKING_ID, associationId: SESSION_ID, roles: ['MEMBER', 'COACH'] });
    }
    if (pathname.endsWith('/coaching/sessions')) return json([line]);
    if (pathname.endsWith('/roster')) {
      return json({
        sessionId: SESSION_ID,
        startsAt: line.startsAt,
        endsAt: line.endsAt,
        status: 'SCHEDULED',
        capacity: 12,
        coaching: line,
        seats: [{ bookingId: BOOKING_ID, memberName: 'Ana Silva', status: 'CONFIRMED' }],
        waitlist: [],
      });
    }
    if (method === 'PUT' && pathname.endsWith('/attendance')) {
      return json({ session: {}, warnedCount: 0 });
    }
    return json({ code: 'NOT_FOUND', message: 'Not found' }, 404);
  });

  // Act: the week, the cancel dialog (open, keep), the seats dialog (open, Escape), the roster and the attendance form
  await page.goto('/coaching');
  const card = page.getByRole('article', { name: 'CSP coaching' });
  await expect(card).toBeVisible();
  await card.getByRole('button', { name: /^Cancel session/ }).tap();
  const cancel = page.getByRole('dialog', { name: 'Cancel this session?' });
  await expect(cancel).toContainText('1 member will be notified and their credit refunded.');
  await expect(page.locator('body')).toHaveCSS('overflow', 'hidden');
  await cancel.getByLabel('Reason').fill('Court flooded');
  await cancel.getByRole('button', { name: 'Keep session' }).tap();
  await expect(cancel).toBeHidden();
  await card.getByRole('button', { name: /^Change seats/ }).tap();
  await expect(page.getByRole('dialog', { name: 'Change the seats' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toBeHidden();
  await card.getByRole('link', { name: /^Mark attendance/ }).tap();
  await expect(page.getByRole('heading', { level: 1, name: 'Roster' })).toBeVisible();
  await page.getByRole('radio', { name: 'Attended' }).check({ force: true });
  await page.getByRole('button', { name: 'Save attendance (1)' }).tap();
  await expect(page.getByText('Attendance saved: 1 attended, 0 no-show.')).toBeVisible();

  // Assert: the policy was in force, and nothing was blocked
  expect(await page.evaluate(() => window.__violations)).toEqual([]);
  expect(consoleErrors.filter((text) => /content security policy/i.test(text))).toEqual([]);
});

test('the admin dialogs, the member screens and the level picker raise no Content-Security-Policy violation', async ({
  page,
}) => {
  // Arrange: an administrator, one pending request, one member who has not activated, and every violation the browser reports
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
  const requestId = 'b0000000-0000-0000-0000-000000000001';
  const memberId = 'c0000000-0000-0000-0000-000000000001';
  const beginner = 'd0000000-0000-0000-0000-000000000001';
  const intermediate = 'd0000000-0000-0000-0000-000000000002';
  const member = {
    memberId,
    name: 'CSP Member',
    email: 'csp-member@example.com',
    phone: '+351 910 000 000',
    levelId: beginner,
    levelName: 'Beginner',
    roles: ['MEMBER'],
    status: 'ACTIVE',
    anonymised: false,
    accountActivated: false,
    joinedAt: new Date(Date.now() - 30 * HOUR).toISOString(),
    levelChanges: [],
    subscriptions: [],
    subscriptionsTruncated: false,
  };
  await page.route('**/api/v1/**', async (route) => {
    const { pathname } = new URL(route.request().url());
    const method = route.request().method();
    const json = (body: unknown, status = 200) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (pathname.endsWith('/auth/refresh')) {
      return json({ accessToken: 'test-csp-token-fixture', tokenType: 'Bearer', expiresIn: 600 });
    }
    if (pathname.endsWith('/api/v1/me')) {
      return json({ memberId, associationId: SESSION_ID, roles: ['MEMBER', 'ADMIN'] });
    }
    if (pathname.endsWith('/api/v1/join-requests')) {
      return json([
        {
          id: requestId,
          name: 'CSP Visitor',
          email: 'csp-visitor@example.com',
          phone: '+351 910 000 001',
          status: 'PENDING',
          requestedAt: new Date(Date.now() - HOUR).toISOString(),
        },
      ]);
    }
    if (pathname.endsWith('/api/v1/members')) return json({ members: [member], truncated: false });
    if (pathname.endsWith(`/api/v1/members/${memberId}`) && method === 'GET') return json(member);
    if (pathname.endsWith('/api/v1/levels')) {
      return json({
        entryLevelId: beginner,
        levels: [
          { id: beginner, name: 'Beginner', rank: 1 },
          { id: intermediate, name: 'Intermediate', rank: 2 },
        ],
      });
    }
    if (pathname.endsWith('/activation-links')) return json({ status: 'RECEIVED' }, 202);
    return json({ code: 'NOT_FOUND', message: 'Not found' }, 404);
  });

  // Act: the Admin home, the join requests (approve and reject dialogs: open, close), the member list and the member
  await page.goto('/admin');
  await expect(page.getByRole('heading', { level: 1, name: 'Admin' })).toBeVisible();
  await page.getByRole('link', { name: /^Join requests/ }).tap();
  const card = page.getByRole('article', { name: 'CSP Visitor' });
  await expect(card).toBeVisible();
  await card.getByRole('button', { name: /^Approve/ }).tap();
  const approve = page.getByRole('alertdialog', { name: 'Approve CSP Visitor?' });
  await expect(approve).toContainText('emails them an activation link');
  await expect(page.locator('body')).toHaveCSS('overflow', 'hidden');
  await approve.getByRole('button', { name: 'Not now' }).tap();
  await expect(approve).toBeHidden();
  await card.getByRole('button', { name: /^Reject/ }).tap();
  const reject = page.getByRole('dialog', { name: 'Reject CSP Visitor?' });
  await reject.getByLabel('Reason (optional)').fill('Not this season');
  await reject.getByRole('button', { name: 'Not now' }).tap();
  await expect(reject).toBeHidden();

  await page.goto('/admin/members?q=csp');
  await expect(page.getByLabel('Search members')).toHaveValue('csp');
  await page.getByRole('link', { name: 'CSP Member' }).tap();
  await expect(page.getByRole('heading', { level: 1, name: 'CSP Member' })).toBeVisible();
  await page.getByLabel('New level').selectOption(intermediate);
  await page.getByRole('button', { name: 'Make administrator' }).tap();
  const admin = page.getByRole('alertdialog', { name: 'Make CSP Member an administrator?' });
  await expect(admin).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(admin).toBeHidden();
  await page.getByRole('button', { name: 'Deactivate member' }).tap();
  const deactivate = page.getByRole('alertdialog', { name: 'Deactivate CSP Member?' });
  await expect(deactivate).toContainText('future bookings will be cancelled');
  await deactivate.getByRole('button', { name: 'Keep active' }).tap();
  await expect(deactivate).toBeHidden();
  await page.getByRole('button', { name: 'Resend activation link' }).tap();
  await expect(page.getByText(/a new activation link is on its way/)).toBeVisible();

  // Assert: the policy was in force, and nothing was blocked
  expect(await page.evaluate(() => window.__violations)).toEqual([]);
  expect(consoleErrors.filter((text) => /content security policy/i.test(text))).toEqual([]);
});
