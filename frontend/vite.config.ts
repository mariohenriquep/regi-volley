import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
import { VitePWA } from 'vite-plugin-pwa';
import { defineConfig } from 'vitest/config';
import { pwaOptions } from './pwa.config.ts';

// The API is reached same-origin, under /api (threat model D-7b): in development through this proxy, in production
// through the reverse proxy that serves dist/ (docs/architecture.md, "Frontend").
const apiTarget = process.env.API_PROXY_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react(), tailwindcss(), VitePWA(pwaOptions)],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: apiTarget,
        // Keep the browser's Host (localhost:5173): the API then sees the request as same-origin, so its CORS filter
        // stays out of the way and CookieEndpointGuardFilter accepts Origin http://localhost:5173 as the API's own
        // origin. changeOrigin: true would turn every call into a cross-origin one and the cookie routes would get 403.
        changeOrigin: false,
        xfwd: false,
      },
    },
  },
  preview: {
    port: 4173,
    strictPort: true,
    proxy: { '/api': { target: apiTarget, changeOrigin: false, xfwd: false } },
  },
  build: {
    // No inline scripts or data: URIs in the bundle, so a strict CSP (script-src 'self') can be used.
    assetsInlineLimit: 0,
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}', '*.test.ts'],
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
    // Generous: a loaded machine or CI runner must slow the suite down, not fail it.
    testTimeout: 15_000,
  },
});
