import { defineConfig, devices } from '@playwright/test';

const port = process.env.CSP_PORT ?? '4175';

/**
 * The production build served with the documented Content-Security-Policy (docs/architecture.md section 15), driven in a
 * browser with the API mocked by `page.route`: no backend, no Docker, so it can run in CI. `npm run e2e:csp` builds first.
 */
export default defineConfig({
  testDir: './e2e-csp',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: !!process.env.CI,
  reporter: [['list']],
  use: { baseURL: `http://localhost:${port}`, trace: 'off', serviceWorkers: 'block' },
  projects: [{ name: 'chromium', use: { ...devices['Pixel 7'] } }],
  webServer: {
    command: 'node e2e-csp/server.mjs',
    url: `http://localhost:${port}`,
    reuseExistingServer: false,
    timeout: 30_000,
    env: { CSP_PORT: port },
  },
});
