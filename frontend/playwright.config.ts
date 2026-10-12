import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end smoke against the real, dockerised backend (not run in CI yet). Needs `docker compose up -d` and the API
 * on :8080 with the dev profile and Mailpit as SMTP server (README, "Frontend"). Playwright starts `npm run dev`
 * (Vite on :5173, proxying /api to the API) unless it is already running.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: !!process.env.CI,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
