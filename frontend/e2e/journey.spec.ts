import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { emailedToken, emailedTokens } from './mailpit';
import {
  seedAdmin,
  seedCoaching,
  seedMemberWithSessions,
  type SeededAdmin,
  type SeededCoaching,
} from './seed';

const PASSWORD = 'correct horse battery staple 42';
const NEW_PASSWORD = 'a different long passphrase 43';

const unique = () => `${Date.now().toString(36)}${Math.floor(Math.random() * 1e4).toString(36)}`;

/** Opens an emailed link the way a person does and checks the token left the address bar at once. */
async function openLink(page: Page, path: string, token: string) {
  await page.goto(`${path}#token=${encodeURIComponent(token)}`);
  await expect(page.getByLabel('New password', { exact: true })).toBeVisible();
  expect(page.url()).not.toContain(token);
  expect(new URL(page.url()).hash).toBe('');
}

async function setPassword(page: Page, password: string, button: string) {
  await page.getByLabel('New password', { exact: true }).fill(password);
  await page.getByLabel('Confirm new password').fill(password);
  await page.getByRole('button', { name: button }).click();
}

async function signIn(page: Page, email: string, password: string) {
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

/** The sessions are a day or two ahead: on a Saturday or Sunday that is next week. Shows the week that has `card`. */
async function showWeekWith(page: Page, card: Locator) {
  await expect(page.getByRole('heading', { level: 1, name: 'Sessions' })).toBeVisible();
  await expect(card.or(page.getByText('No sessions this week'))).toBeVisible();
  if (!(await card.isVisible())) {
    await page.getByRole('button', { name: 'Next week' }).click();
  }
  await expect(card).toBeVisible();
}

/** The Lisbon Monday (`YYYY-MM-DD`) of the week an instant is in, to open that week of the Coaching screen. */
function lisbonMonday(instant: number): string {
  const day = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Lisbon' }).format(instant);
  const midday = new Date(`${day}T12:00:00Z`);
  midday.setUTCDate(midday.getUTCDate() - ((midday.getUTCDay() + 6) % 7));
  return midday.toISOString().slice(0, 10);
}

async function activateAndSignIn(
  page: Page,
  request: APIRequestContext,
  email: string,
  password: string,
) {
  const token = await emailedToken(request, email, '/activate');
  await openLink(page, '/activate', token);
  await setPassword(page, password, 'Activate account');
  await expect(page.getByRole('heading', { name: 'Account activated' })).toBeVisible();
  await page.goto('/login');
  await signIn(page, email, password);
  await expect(page.getByRole('heading', { level: 1, name: 'Sessions' })).toBeVisible();
}

async function signInActivated(page: Page, email: string) {
  await page.goto('/login');
  await signIn(page, email, PASSWORD);
  await expect(page.getByRole('heading', { level: 1, name: 'Sessions' })).toBeVisible();
}

// Registration is limited to 3 an hour per address, so one registration serves the whole journey: the founder
// created and activated in the first test is the member who forgets the password in the second.
test.describe.configure({ mode: 'serial' });
let founder = { email: '', password: PASSWORD, shortName: '', visitorEmail: '' };
let coaching: SeededCoaching;
let admin: SeededAdmin;

test('a founder registers through the UI, activates by the emailed link and signs in; a visitor finds the page and asks to join', async ({
  page,
  request,
}) => {
  const suffix = unique();
  const shortName = `e2e-${suffix}`;
  const founderEmail = `founder-${suffix}@example.com`;
  const visitorEmail = `visitor-${suffix}@example.com`;
  founder = { email: founderEmail, password: PASSWORD, shortName, visitorEmail };

  // Register an association through the form
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  await page.getByRole('main').getByRole('link', { name: 'Register an association' }).click();
  await expect(page.getByRole('heading', { name: 'Register an association' })).toBeVisible();
  await page.getByLabel('Association name').fill(`E2E Club ${suffix}`);
  await page.getByLabel('Short name').fill(shortName.toUpperCase());
  await expect(page.getByText(`/a/${shortName}`)).toBeVisible();
  await page.getByLabel('Locality').fill('Lisbon');
  await page.getByLabel('Contact email').fill(`info-${suffix}@example.com`);
  await page.getByLabel('Level 1', { exact: true }).fill('Beginner');
  await page.getByRole('button', { name: 'Add level' }).click();
  await page.getByLabel('Level 2', { exact: true }).fill('Intermediate');
  await page.getByLabel('Your name').fill('E2E Founder');
  await page.getByLabel('Your email').fill(founderEmail);
  await page.getByLabel('Your phone').fill('912345678');
  await page.getByRole('checkbox', { name: /I agree/ }).click();
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(
    page.getByRole('heading', { name: 'Check your email to activate your account' }),
  ).toBeVisible();

  // Open the activation link from the mail and choose a password
  const activation = await emailedToken(request, founderEmail, '/activate');
  await openLink(page, '/activate', activation);
  await setPassword(page, PASSWORD, 'Activate account');
  await expect(page.getByRole('heading', { name: 'Account activated' })).toBeVisible();

  // The link is single use: opening it again is refused as invalid
  await openLink(page, '/activate', activation);
  await setPassword(page, PASSWORD, 'Activate account');
  await expect(page.getByText('This link is invalid or has expired.')).toBeVisible();

  // Sign in, see the home page, survive a reload through the refresh cookie, sign out
  await page.goto('/login');
  await signIn(page, founderEmail, PASSWORD);
  await expect(page.getByRole('heading', { level: 1, name: 'Sessions' })).toBeVisible();
  await expect(page.getByText('No sessions this week')).toBeVisible();
  const refresh = page.waitForResponse('**/api/v1/auth/refresh');
  await page.reload();
  expect((await refresh).status()).toBe(200);
  await expect(page.getByRole('heading', { level: 1, name: 'Sessions' })).toBeVisible();
  await page.getByRole('button', { name: 'Account' }).click();
  await page.getByRole('menuitem', { name: 'Sign out', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();

  // A visitor opens the public page and asks to join: the same confirmation as for anyone
  await page.goto(`/a/${shortName}`);
  await expect(page.getByRole('heading', { level: 1, name: `E2E Club ${suffix}` })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Levels' })).toContainText('Intermediate');
  await page.getByRole('link', { name: 'Want to try it?' }).click();
  await expect(page.getByRole('heading', { name: 'Request to join' })).toBeVisible();
  await page.getByLabel('Name', { exact: true }).fill('Rita Visitor');
  await page.getByLabel('Email').fill(visitorEmail);
  await page.getByLabel('Phone').fill('961234567');
  await page.getByRole('checkbox', { name: /I agree/ }).click();
  await page.getByRole('button', { name: 'Send request' }).click();
  await expect(
    page.getByText('If this email can join, the association will review your request.'),
  ).toBeVisible();

  // An unknown association is a friendly page
  await page.goto(`/a/nobody-${suffix}`);
  await expect(page.getByRole('heading', { name: 'Association not found' })).toBeVisible();
});

test('a member who forgot the password resets it through the UI', async ({ page, request }) => {
  // Arrange: the activated founder of the first test
  const { email } = founder;

  // Act: ask for a reset link from the sign-in page
  await page.goto('/login');
  await page.getByRole('main').getByRole('link', { name: 'Forgot password?' }).click();
  await expect(page.getByRole('heading', { name: 'Forgot your password?' })).toBeVisible();
  await page.getByLabel('Email').fill(email);
  await page.getByRole('button', { name: 'Send reset link' }).click();
  await expect(
    page.getByText(
      'If an account exists for this email, we have sent a link to reset the password.',
    ),
  ).toBeVisible();
  const token = await emailedToken(request, email, '/reset-password');
  await openLink(page, '/reset-password', token);
  await setPassword(page, NEW_PASSWORD, 'Change password');
  await expect(page.getByRole('heading', { name: 'Password changed' })).toBeVisible();

  // Assert: the old password no longer works, the new one does
  await page.getByRole('main').getByRole('link', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  await signIn(page, email, PASSWORD);
  await expect(page.getByRole('alert')).toContainText('Invalid email or password');
  await page.getByLabel('Password').fill(NEW_PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Sessions' })).toBeVisible();
});

test.describe('a member on a phone', () => {
  test.use({ viewport: { width: 390, height: 844 }, hasTouch: true });

  test('books a session in two taps, sees why a double booking is refused, cancels after a reload, then checks the plan and the history', async ({
    page,
    request,
  }) => {
    // Arrange: the activated founder of the first test holds a paid pack and there are sessions to book (see seed.ts)
    const { email, shortName } = founder;
    const suffix = unique();
    const { groupName, planName, headers } = await seedMemberWithSessions(
      request,
      { email, password: NEW_PASSWORD },
      suffix,
      shortName,
    );
    await page.goto('/login');
    await signIn(page, email, NEW_PASSWORD);
    const firstCard = page.getByRole('article', { name: groupName }).first();
    await showWeekWith(page, firstCard);
    await expect(firstCard).toContainText('12 of 12 seats free');
    await expect(firstCard).toContainText('E2E Pavilion');

    // Act: tap 1 (Book) here, while the same member books the same session on another device
    await firstCard.getByRole('button', { name: /^Book/ }).tap();
    const dialog = page.getByRole('dialog', { name: 'Book this session?' });
    await expect(dialog).toContainText("You'll use 1 credit (10 left).");
    const otherDevice = await page.context().newPage();
    await otherDevice.goto('/');
    const otherCard = otherDevice.getByRole('article', { name: groupName }).first();
    await showWeekWith(otherDevice, otherCard);
    await otherCard.getByRole('button', { name: /^Book/ }).click();
    await otherDevice.getByRole('button', { name: 'Confirm booking' }).click();
    await expect(otherDevice.getByText("You're booked and confirmed.")).toBeVisible();
    await otherDevice.close();

    // Assert: tap 2 is refused with the API's message and, from its reason, what to do about it; the list tells the truth
    await dialog.getByRole('button', { name: 'Confirm booking' }).tap();
    await expect(dialog.getByRole('alert')).toContainText('already has a booking');
    await expect(dialog).toContainText('You already have a booking in this session.');
    await dialog.getByRole('button', { name: 'Not now' }).tap();
    await expect(firstCard.getByText("You're confirmed")).toBeVisible();
    await expect(firstCard).toContainText('11 of 12 seats free');

    // A reload forgets everything the page knew, and the booking can still be cancelled: the list carries its id
    await page.reload();
    await showWeekWith(page, firstCard);
    await expect(firstCard.getByText("You're confirmed")).toBeVisible();
    await page.getByRole('link', { name: 'My plan' }).tap();
    const plan = page.getByRole('article', { name: planName });
    await expect(plan.getByText('9 credits left')).toBeVisible();
    await expect(plan.getByText('Paid')).toBeVisible();

    // Cancel (free: more than a day ahead): the credit comes back
    await page.getByRole('link', { name: 'Sessions' }).tap();
    await showWeekWith(page, firstCard);
    await firstCard.getByRole('button', { name: /^Cancel/ }).tap();
    const cancelDialog = page.getByRole('alertdialog', { name: 'Cancel your booking?' });
    await expect(cancelDialog).toContainText('Cancelling now is free.');
    await expect(cancelDialog).toContainText('You can cancel for free until');
    await cancelDialog.getByRole('button', { name: 'Cancel booking' }).tap();
    await expect(
      page.getByText('Booking cancelled. It was free and your credit was given back.'),
    ).toBeVisible();
    await expect(firstCard.getByRole('button', { name: /^Book/ })).toBeVisible();
    await expect(firstCard).toContainText('12 of 12 seats free');

    // Book again in two taps (nothing else in the way): the server's answer in the notice and on the card
    await firstCard.getByRole('button', { name: /^Book/ }).tap();
    await page
      .getByRole('dialog', { name: 'Book this session?' })
      .getByRole('button', { name: 'Confirm booking' })
      .tap();
    await expect(page.getByText("You're booked and confirmed.")).toBeVisible();
    await expect(firstCard.getByText("You're confirmed")).toBeVisible();
    await firstCard.getByRole('button', { name: /^Cancel/ }).tap();
    await page.getByRole('button', { name: 'Cancel booking' }).tap();
    await expect(
      page.getByText('Booking cancelled. It was free and your credit was given back.'),
    ).toBeVisible();

    // My plan again, and the history
    await page.getByRole('link', { name: 'My plan' }).tap();
    await expect(plan.getByText('10 credits left')).toBeVisible();
    await page.getByRole('link', { name: 'History' }).tap();
    await expect(page.getByRole('heading', { level: 1, name: 'History' })).toBeVisible();
    await expect(page.getByText('Attended')).toBeVisible();
    await expect(page.getByText('No-show', { exact: true })).toBeVisible();
    await expect(page.getByText(/\d of 3 no-shows this month/)).toBeVisible();

    // The coach and administrator journeys' data, made with the founder's login of the seed (sign-in is limited per address).
    // The coach's data books two sessions, so it comes after the credit checks above; the sign-out below ends that login.
    coaching = await seedCoaching(request, headers, suffix, shortName);
    admin = await seedAdmin(request, headers, suffix, shortName);

    // Sign out everywhere is in the account menu
    await page.getByRole('button', { name: 'Account' }).tap();
    await page.getByRole('menuitem', { name: 'Sign out on all devices' }).tap();
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  });
});

test.describe('a coach on a phone', () => {
  test.use({ viewport: { width: 390, height: 844 }, hasTouch: true });

  test('finds the week, marks attendance, changes the seats and cancels a session, while a player sees no Coaching', async ({
    page,
    request,
  }) => {
    // Arrange: a coach and a player activate their accounts; three sessions exist (see seed.ts)
    const seeded = coaching;
    await activateAndSignIn(page, request, seeded.coachEmail, PASSWORD);

    // Act: the section is in the bar for a coach
    await page
      .getByRole('navigation', { name: 'Sections' })
      .getByRole('link', { name: 'Coaching' })
      .tap();
    await expect(page.getByRole('heading', { level: 1, name: 'Coaching' })).toBeVisible();

    // The session that started an hour ago: nobody is marked yet, and the card offers the roster with the marking form
    await page.goto(`/coaching?week=${lisbonMonday(seeded.startsAt.marking)}`);
    const marking = page.getByRole('article', { name: seeded.marking });
    await expect(marking).toContainText('2 of 12 confirmed');
    await expect(marking).toContainText('0 attended · 0 no-show · 2 to mark');
    await expect(marking).toContainText('E2E Coaching Hall');
    await marking.getByRole('link', { name: /^Mark attendance/ }).tap();

    // The roster lists the seat holders in booking order, names only; one tap each, then one Save
    await expect(page.getByRole('heading', { level: 1, name: 'Roster' })).toBeVisible();
    const seats = page.getByRole('list', { name: 'Seats' });
    await expect(seats.getByRole('listitem')).toHaveText([/E2E Founder/, /Paula Player/]);
    await page.getByRole('group', { name: 'E2E Founder' }).getByText('Attended').tap();
    await page.getByRole('group', { name: 'Paula Player' }).getByText('No-show').tap();
    await page.getByRole('button', { name: 'Save attendance (2)' }).tap();

    // Assert: the API's counts are on screen, on the roster and on the week
    await expect(page.getByText('Attendance saved: 1 attended, 1 no-show.')).toBeVisible();
    await expect(page.getByRole('region', { name: seeded.marking })).toContainText(
      '1 attended · 1 no-show · 0 to mark',
    );
    await expect(seats.getByRole('listitem').first()).toContainText('Attended');
    await expect(page.getByText('Attendance is complete.')).toBeVisible();
    await page.getByRole('link', { name: 'Back to Coaching' }).tap();
    await expect(marking).toContainText('1 attended · 1 no-show · 0 to mark');
    await expect(marking.getByRole('link', { name: /^Roster/ })).toBeVisible();
    // A mark exists, so the API no longer offers to cancel it
    await expect(marking.getByRole('button')).toHaveCount(0);

    // Seats: the single seat is held by the player, the founder waits; below the confirmed is refused, raising promotes
    await page.goto(`/coaching?week=${lisbonMonday(seeded.startsAt.seats)}`);
    const seatsCard = page.getByRole('article', { name: seeded.seats });
    await expect(seatsCard).toContainText('1 of 1 confirmed');
    await expect(seatsCard).toContainText('1 on the waitlist');
    await seatsCard.getByRole('button', { name: /^Change seats/ }).tap();
    const seatsDialog = page.getByRole('dialog', { name: 'Change the seats' });
    await expect(seatsDialog.getByLabel('Seats')).toHaveValue('1');
    await seatsDialog.getByLabel('Seats').fill('2');
    await seatsDialog.getByRole('button', { name: 'Save seats' }).tap();
    await expect(
      page.getByText('Seats changed to 2. 1 person moved up from the waitlist.'),
    ).toBeVisible();
    await expect(seatsCard).toContainText('2 of 2 confirmed');
    await expect(seatsCard).not.toContainText('on the waitlist');
    await seatsCard.getByRole('button', { name: /^Change seats/ }).tap();
    await seatsDialog.getByLabel('Seats').fill('1');
    await seatsDialog.getByRole('button', { name: 'Save seats' }).tap();
    await expect(seatsDialog.getByLabel('Seats')).toHaveAccessibleDescription(/cannot be lower/);
    await seatsDialog.getByRole('button', { name: 'Not now' }).tap();
    await expect(seatsDialog).toBeHidden();

    // Cancel a session ahead: a reason is required, the warning counts the members, the API reports what it did
    await page.goto(`/coaching?week=${lisbonMonday(seeded.startsAt.cancelling)}`);
    const cancelling = page.getByRole('article', { name: seeded.cancelling });
    await expect(cancelling).toContainText('1 of 12 confirmed');
    await cancelling.getByRole('button', { name: /^Cancel session/ }).tap();
    const cancelDialog = page.getByRole('dialog', { name: 'Cancel this session?' });
    await expect(cancelDialog).toContainText(
      '1 member will be notified and their credit refunded.',
    );
    await cancelDialog.getByRole('button', { name: 'Cancel session' }).tap();
    await expect(cancelDialog.getByLabel('Reason')).toHaveAccessibleDescription(
      'Enter the reason the members will be told.',
    );
    await cancelDialog.getByLabel('Reason').fill('The hall is closed for repairs');
    await cancelDialog.getByRole('button', { name: 'Cancel session' }).tap();
    await expect(
      page.getByText('Session cancelled. 1 booking was cancelled and 1 credit refunded.'),
    ).toBeVisible();
    await expect(cancelling).toContainText('Cancelled');
    await expect(cancelling.getByRole('button')).toHaveCount(0);

    // A player has no Coaching section, and the address says why
    await page.getByRole('button', { name: 'Account' }).tap();
    await page.getByRole('menuitem', { name: 'Sign out', exact: true }).tap();
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
    await activateAndSignIn(page, request, seeded.playerEmail, PASSWORD);
    await expect(page.getByRole('navigation', { name: 'Sections' }).getByRole('link')).toHaveText([
      'Sessions',
      'My plan',
      'History',
    ]);
    await page.goto('/coaching');
    await expect(page.getByText('Coaching is for coaches and administrators.')).toBeVisible();
  });
});

test.describe('an administrator on a phone', () => {
  test.use({ viewport: { width: 390, height: 844 }, hasTouch: true });

  test('approves a join request, then changes the new member’s level and roles, resends the activation link and deactivates them', async ({
    page,
    request,
  }) => {
    // Arrange: a second administrator activates her account; the visitor of the first test is still waiting (see seed.ts)
    const visitorEmail = founder.visitorEmail;
    await activateAndSignIn(page, request, admin.adminEmail, PASSWORD);

    // Act: the section is in the bar for an administrator, and the home links the two screens
    await page
      .getByRole('navigation', { name: 'Sections' })
      .getByRole('link', { name: 'Admin' })
      .tap();
    await expect(page.getByRole('heading', { level: 1, name: 'Admin' })).toBeVisible();
    await page.getByRole('link', { name: /^Join requests/ }).tap();

    // The request lists what the visitor sent; approving asks first and says what happens
    const request1 = page.getByRole('article', { name: 'Rita Visitor' });
    await expect(request1).toContainText(visitorEmail);
    await expect(request1).toContainText('961234567');
    await request1.getByRole('button', { name: /^Approve/ }).tap();
    const approve = page.getByRole('alertdialog', { name: 'Approve Rita Visitor?' });
    await expect(approve).toContainText('creates the member at the entry level');
    await approve.getByRole('button', { name: 'Approve request' }).tap();
    await expect(
      page.getByRole('status').filter({ hasText: 'Rita Visitor is now a member' }),
    ).toBeVisible();
    await expect(page.getByText('No requests are waiting')).toBeVisible();
    const firstLink = (await emailedTokens(request, visitorEmail, '/activate'))[0] ?? '';
    expect(firstLink).not.toBe('');

    // The new member is found by searching (the search is in the address) and opened
    await page.getByRole('link', { name: 'Back to Admin' }).tap();
    await page.getByRole('link', { name: /^Members/ }).tap();
    await page.getByLabel('Search members').fill('rita');
    await expect(page).toHaveURL(/\/admin\/members\?q=rita$/);
    const row = page.getByRole('article', { name: 'Rita Visitor' });
    await expect(row).toContainText('Not activated');
    await expect(row).toContainText('Beginner');
    await expect(page.getByRole('article')).toHaveCount(1);
    await row.getByRole('link', { name: 'Rita Visitor' }).tap();
    await expect(page.getByRole('heading', { level: 1, name: 'Rita Visitor' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Contact' })).toContainText(visitorEmail);
    await expect(page.getByRole('region', { name: 'Account' })).toContainText('Not activated yet');

    // The level: a member starts at the entry level; the change is in the history with who made it
    await expect(page.getByRole('region', { name: 'Level' })).toContainText('Beginner');
    await page.getByLabel('New level').selectOption({ label: 'Intermediate' });
    await page.getByRole('button', { name: 'Change level' }).tap();
    await expect(page.getByRole('status')).toContainText('Rita Visitor is now Intermediate.');
    const history = page.getByRole('list', { name: 'Level history' });
    await expect(history).toContainText('Beginner → Intermediate');
    await expect(history).toContainText('Ada Admin');

    // Roles: grant COACH, then take it away again
    await page.getByRole('button', { name: 'Make coach' }).tap();
    await expect(page.getByRole('status')).toContainText('Rita Visitor can now coach.');
    await expect(page.getByRole('button', { name: 'Remove coach role' })).toBeVisible();
    await page.getByRole('button', { name: 'Remove coach role' }).tap();
    await expect(page.getByRole('status')).toContainText(
      'The coach role was removed from Rita Visitor.',
    );
    await expect(page.getByRole('button', { name: 'Make coach' })).toBeVisible();

    // The administrator role asks first; the API keeps the last administrator (there are two here, so only the question is checked)
    await page.getByRole('button', { name: 'Make administrator' }).tap();
    const makeAdmin = page.getByRole('alertdialog', {
      name: 'Make Rita Visitor an administrator?',
    });
    await expect(makeAdmin).toBeVisible();
    await makeAdmin.getByRole('button', { name: 'Not now' }).tap();
    await expect(makeAdmin).toBeHidden();

    // A new activation link is mailed to the account's address and replaces the first
    await page.getByRole('button', { name: 'Resend activation link' }).tap();
    await expect(page.getByRole('status')).toContainText('a new activation link is on its way');
    await expect
      .poll(async () => (await emailedTokens(request, visitorEmail, '/activate')).length, {
        message: 'a second activation link in Mailpit',
        timeout: 20_000,
      })
      .toBe(2);
    expect((await emailedTokens(request, visitorEmail, '/activate'))[0]).not.toBe(firstLink);

    // Deactivating asks first and says what it does; the member is inactive afterwards and nothing more can be sent
    await page.getByRole('button', { name: 'Deactivate member' }).tap();
    const deactivate = page.getByRole('alertdialog', { name: 'Deactivate Rita Visitor?' });
    await expect(deactivate).toContainText('future bookings will be cancelled');
    await deactivate.getByRole('button', { name: 'Deactivate' }).tap();
    await expect(page.getByRole('status')).toContainText('Rita Visitor was deactivated.');
    await expect(page.getByText('Inactive', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Deactivate member' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Resend activation link' })).toHaveCount(0);

    // Assert: the list filters by status
    await page.getByRole('link', { name: 'Back to Members' }).tap();
    await page.getByLabel('Status').selectOption('INACTIVE');
    await expect(page).toHaveURL(/status=INACTIVE/);
    await expect(page.getByRole('article', { name: 'Rita Visitor' })).toContainText('Inactive');
    await expect(page.getByRole('article', { name: 'Ada Admin' })).toHaveCount(0);
  });
});

test.describe('an administrator setting the association up on a phone', () => {
  test.use({ viewport: { width: 390, height: 844 }, hasTouch: true });

  test('adds a level, venues, a group with two slots and a coach and a pack plan, edits the plan price, archives the group and deletes a venue', async ({
    page,
  }) => {
    // Arrange: the second administrator signs in again; Rui Coach (made by seedCoaching) holds the coach role
    const tag = unique();
    const level = `E2E Level ${tag}`;
    const renamed = `E2E Pro ${tag}`;
    const annex = `E2E Annex ${tag}`;
    const spare = `E2E Spare ${tag}`;
    const group = `E2E Setup Group ${tag}`;
    const plan = `E2E Pack 20 ${tag}`;
    await signInActivated(page, admin.adminEmail);
    await page
      .getByRole('navigation', { name: 'Sections' })
      .getByRole('link', { name: 'Admin' })
      .tap();

    // Levels: add (it is the highest), move it up, rename it
    await page.getByRole('link', { name: /^Levels/ }).tap();
    await page.getByLabel('New level name').fill(level);
    await page.getByRole('button', { name: 'Add level' }).tap();
    await expect(page.getByRole('status').filter({ hasText: `${level} was added` })).toBeVisible();
    const levels = page.getByRole('list', { name: 'Levels' }).getByRole('listitem');
    await expect(levels.last()).toContainText(level);
    const count = await levels.count();
    await page.getByRole('button', { name: `Move ${level} up` }).tap();
    await expect(levels.nth(count - 2)).toContainText(level);
    await page.getByRole('button', { name: `Rename ${level}` }).tap();
    const rename = page.getByRole('dialog', { name: `Rename ${level}` });
    await rename.getByLabel('Level name').fill(renamed);
    await rename.getByRole('button', { name: 'Save name' }).tap();
    await expect(
      page.getByRole('status').filter({ hasText: `is now called ${renamed}` }),
    ).toBeVisible();
    await expect(levels.nth(count - 2)).toContainText(renamed);

    // Venues: two are added; a blank form is refused in the browser
    await page.goto('/admin/setup/venues');
    for (const name of [annex, spare]) {
      await page.getByRole('button', { name: 'Add venue' }).tap();
      const venue = page.getByRole('dialog', { name: 'Add a venue' });
      await venue.getByRole('button', { name: 'Add venue' }).tap();
      await expect(venue.getByLabel('Courts')).toHaveAccessibleDescription(
        'Enter the number of courts',
      );
      await venue.getByLabel('Name').fill(name);
      await venue.getByLabel('Address').fill('Rua do Teste 1, Lisbon');
      await venue.getByLabel('Courts').fill('2');
      await venue.getByRole('button', { name: 'Add venue' }).tap();
      await expect(page.getByRole('status').filter({ hasText: `${name} was added` })).toBeVisible();
    }

    // Groups: two slots that overlap are refused by the API and shown; moving the second one saves the group
    await page.goto('/admin/setup/groups');
    await page.getByRole('button', { name: 'Add group' }).tap();
    const form = page.getByRole('dialog', { name: 'Add a training group' });
    await form.getByLabel('Name').fill(group);
    await form.getByRole('checkbox', { name: renamed }).tap();
    await form.getByLabel('Venue').selectOption({ label: annex });
    await form.getByLabel('Coach', { exact: true }).selectOption({ label: 'Rui Coach' });
    await form.getByLabel('Seats').fill('12');
    const first = form.getByRole('group', { name: 'Slot 1' });
    await first.getByLabel('Day').selectOption('MONDAY');
    await first.getByLabel('Start time (Lisbon)').fill('19:00');
    await first.getByLabel('Length (minutes)').fill('60');
    await form.getByRole('button', { name: 'Add slot' }).tap();
    const second = form.getByRole('group', { name: 'Slot 2' });
    await second.getByLabel('Day').selectOption('MONDAY');
    await second.getByLabel('Start time (Lisbon)').fill('19:30');
    await second.getByLabel('Length (minutes)').fill('60');
    await form.getByRole('button', { name: 'Add group' }).tap();
    await expect(form.getByRole('alert')).toContainText('overlap');
    await second.getByLabel('Day').selectOption('WEDNESDAY');
    await form.getByRole('button', { name: 'Add group' }).tap();
    await expect(
      page
        .getByRole('status')
        .filter({ hasText: 'Sessions for this group appear after the next daily generation.' }),
    ).toBeVisible();
    const card = page.getByRole('article', { name: group });
    await expect(card).toContainText(renamed);
    await expect(card).toContainText(annex);
    await expect(card).toContainText('Rui Coach');
    await expect(card).toContainText('12 seats');
    await expect(card).toContainText('Monday 19:00, 60 min');
    await expect(card).toContainText('Wednesday 19:30, 60 min');

    // Plans: a pack, then a cheaper price; the sheet says existing subscriptions keep theirs
    await page.goto('/admin/setup/plans');
    await page.getByRole('button', { name: 'Add plan' }).tap();
    const add = page.getByRole('dialog', { name: 'Add a plan' });
    await add.getByLabel('Name').fill(plan);
    await add.getByLabel('Type').selectOption({ label: 'Pack of sessions' });
    await add.getByLabel('Credits').fill('20');
    await add.getByLabel('Valid for (days)').fill('120');
    await add.getByLabel('Price (€)').fill('89.90');
    await add.getByRole('button', { name: 'Add plan' }).tap();
    const planCard = page.getByRole('article', { name: plan });
    await expect(planCard).toContainText('€89.90');
    await expect(planCard).toContainText('20 credits, valid for 120 days');
    await expect(planCard).toContainText('All levels');
    await planCard.getByRole('button', { name: /^Edit/ }).tap();
    const edit = page.getByRole('dialog', { name: `Edit ${plan}` });
    await expect(edit).toContainText('keep the price and terms they bought');
    await edit.getByLabel('Price (€)').fill('79.90');
    await edit.getByRole('button', { name: 'Save plan' }).tap();
    await expect(planCard).toContainText('€79.90');

    // Venues again: the one the group uses cannot be deleted; a stale list lets the API's refusal be read
    await page.goto('/admin/setup/venues');
    const used = page.getByRole('article', { name: annex });
    await expect(used.getByRole('button', { name: /^Delete/ })).toBeDisabled();
    await expect(used).toContainText('An active training group uses this venue');
    await page.route('**/api/v1/venues', async (route) => {
      const response = await route.fetch();
      const venues = (await response.json()) as { inUseByActiveGroup?: boolean }[];
      await route.fulfill({
        response,
        json: venues.map((venue) => ({ ...venue, inUseByActiveGroup: false })),
      });
    });
    await page.reload();
    await page
      .getByRole('article', { name: annex })
      .getByRole('button', { name: /^Delete/ })
      .tap();
    const refused = page.getByRole('alertdialog', { name: `Delete ${annex}?` });
    await refused.getByRole('button', { name: 'Delete venue' }).tap();
    await expect(refused).toContainText('used by an active training group');
    await refused.getByRole('button', { name: 'Not now' }).tap();
    await page.unroute('**/api/v1/venues');

    // The spare venue is deleted after the question
    await page.reload();
    await page
      .getByRole('article', { name: spare })
      .getByRole('button', { name: /^Delete/ })
      .tap();
    await page
      .getByRole('alertdialog', { name: `Delete ${spare}?` })
      .getByRole('button', { name: 'Delete venue' })
      .tap();
    await expect(
      page.getByRole('status').filter({ hasText: `${spare} was deleted` }),
    ).toBeVisible();
    await expect(page.getByRole('article', { name: spare })).toHaveCount(0);

    // Archive the group: it asks first, leaves the active list and shows up with the archived toggle; its venue is free again
    await page.goto('/admin/setup/groups');
    await page
      .getByRole('article', { name: group })
      .getByRole('button', { name: /^Archive/ })
      .tap();
    const archive = page.getByRole('alertdialog', { name: `Archive ${group}?` });
    await expect(archive).toContainText('Sessions that already exist are not changed or cancelled');
    await archive.getByRole('button', { name: 'Archive group' }).tap();
    await expect(
      page.getByRole('status').filter({ hasText: `${group} was archived` }),
    ).toBeVisible();
    await expect(page.getByRole('article', { name: group })).toHaveCount(0);
    await page.getByRole('checkbox', { name: 'Show archived groups' }).tap();
    await expect(page).toHaveURL(/archived=1/);
    await expect(page.getByRole('article', { name: group })).toContainText('Archived');
    await page.goto('/admin/setup/venues');
    await expect(
      page.getByRole('article', { name: annex }).getByRole('button', { name: /^Delete/ }),
    ).toBeEnabled();
  });
});

test('asking for a reset link looks the same for an address nobody registered', async ({
  page,
}) => {
  // Arrange / Act
  await page.goto('/forgot-password');
  await page.getByLabel('Email').fill(`nobody-${unique()}@example.com`);
  await page.getByRole('button', { name: 'Send reset link' }).click();

  // Assert
  await expect(
    page.getByText(
      'If an account exists for this email, we have sent a link to reset the password.',
    ),
  ).toBeVisible();
});
