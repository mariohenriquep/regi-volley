import { expect, test } from '@playwright/test';
import { activationToken } from './mailpit';

const API = process.env.API_URL ?? 'http://localhost:8080';
const PASSWORD = 'correct horse battery staple 42';

test('a founder registers, activates by the emailed link and signs in', async ({
  page,
  request,
}) => {
  // Arrange: a new association through the API, its founder activated with the token from Mailpit
  const suffix = `${Date.now().toString(36)}${Math.floor(Math.random() * 1e4).toString(36)}`;
  const shortName = `e2e-${suffix}`;
  const founderEmail = `founder-${suffix}@example.com`;
  const registered = await request.post(`${API}/api/v1/public/associations`, {
    data: {
      name: `E2E Club ${suffix}`,
      shortName,
      locality: 'Lisbon',
      contactEmail: `info-${suffix}@example.com`,
      levelNames: ['Beginner', 'Intermediate'],
      founderName: 'E2E Founder',
      founderEmail,
      founderPhone: '912345678',
      consentAccepted: true,
      policyVersion: '2026-01',
    },
  });
  expect(registered.status(), await registered.text()).toBe(201);
  const token = await activationToken(request, founderEmail);
  const activated = await request.post(`${API}/api/v1/auth/activate`, {
    data: { token, password: PASSWORD },
  });
  expect(activated.status(), await activated.text()).toBe(204);

  // Act: sign in through the UI
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  await page.getByLabel('Email').fill(founderEmail);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();

  // Assert: the home page made an authenticated call (GET /api/v1/me)
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await expect(page.getByText(/^[0-9a-f-]{36}$/)).toBeVisible();

  // ...and a reload restores the session from the refresh cookie, through the Vite proxy (same origin, Origin check)
  const refresh = page.waitForResponse('**/api/v1/auth/refresh');
  await page.reload();
  expect((await refresh).status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();

  // ...and signing out revokes it: a reload lands on the sign-in page
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
});
