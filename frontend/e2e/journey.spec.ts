import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { emailedToken } from './mailpit';
import { seedCoaching, seedMemberWithSessions, type SeededCoaching } from './seed';

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

// Registration is limited to 3 an hour per address, so one registration serves the whole journey: the founder
// created and activated in the first test is the member who forgets the password in the second.
test.describe.configure({ mode: 'serial' });
let founder = { email: '', password: PASSWORD, shortName: '' };
let coaching: SeededCoaching;

test('a founder registers through the UI, activates by the emailed link and signs in; a visitor finds the page and asks to join', async ({
  page,
  request,
}) => {
  const suffix = unique();
  const shortName = `e2e-${suffix}`;
  const founderEmail = `founder-${suffix}@example.com`;
  founder = { email: founderEmail, password: PASSWORD, shortName };

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
  await page.getByLabel('Email').fill(`visitor-${suffix}@example.com`);
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

    // The coach journey's data, made with the founder's login of the seed (sign-in is limited per address). It books two
    // sessions, so it comes after the credit checks above; the sign-out below ends that login.
    coaching = await seedCoaching(request, headers, suffix, shortName);

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
