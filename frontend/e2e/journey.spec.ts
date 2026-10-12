import { expect, test, type Page } from '@playwright/test';
import { emailedToken } from './mailpit';

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

// Registration is limited to 3 an hour per address, so one registration serves the whole journey: the founder
// created and activated in the first test is the member who forgets the password in the second.
test.describe.configure({ mode: 'serial' });
let founder = { email: '', password: PASSWORD };

test('a founder registers through the UI, activates by the emailed link and signs in; a visitor finds the page and asks to join', async ({
  page,
  request,
}) => {
  const suffix = unique();
  const shortName = `e2e-${suffix}`;
  const founderEmail = `founder-${suffix}@example.com`;
  founder = { email: founderEmail, password: PASSWORD };

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
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await expect(page.getByText(/^[0-9a-f-]{36}$/)).toBeVisible();
  const refresh = page.waitForResponse('**/api/v1/auth/refresh');
  await page.reload();
  expect((await refresh).status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
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
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
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
