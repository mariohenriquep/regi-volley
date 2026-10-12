import type { VitePWAOptions } from 'vite-plugin-pwa';

export const THEME_COLOR = '#0f3d75';

/**
 * The PWA (vite-plugin-pwa, Workbox generateSW). The service worker precaches the built app shell and nothing else:
 * no runtime caching at all, so no API response (personal data) is ever stored by it, and a navigation under /api is
 * never answered with the cached index.html.
 *
 * Updates (`registerType: 'prompt'`): a new worker installs and then *waits*; it activates only when the user taps
 * "Reload" in the notice (the page posts SKIP_WAITING, `src/pwa/`), and then claims the open pages and reloads them.
 * The forms (register, join, activate, reset) hold unsaved input, so an update must never reload a page by itself.
 * The app registers the worker from its own bundle (`virtual:pwa-register` in main.tsx, so index.html gets no extra
 * script, CSP). A reload loses the in-memory access token only, which the start-up refresh restores.
 */
export const pwaOptions: Partial<VitePWAOptions> = {
  registerType: 'prompt',
  injectRegister: false,
  manifest: {
    name: 'RegiVolley',
    short_name: 'RegiVolley',
    description: 'Book your volleyball training sessions.',
    lang: 'en',
    start_url: '/',
    scope: '/',
    display: 'standalone',
    orientation: 'portrait',
    theme_color: THEME_COLOR,
    background_color: '#ffffff',
    icons: [
      { src: '/pwa-192x192.png', sizes: '192x192', type: 'image/png' },
      { src: '/pwa-512x512.png', sizes: '512x512', type: 'image/png' },
      { src: '/maskable-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
    ],
  },
  workbox: {
    globPatterns: ['**/*.{js,css,html,svg,png,ico,webmanifest,woff2}'],
    navigateFallback: '/index.html',
    navigateFallbackDenylist: [/^\/api(\/|$)/],
    cleanupOutdatedCaches: true,
    skipWaiting: false,
    clientsClaim: true,
  },
  devOptions: { enabled: false },
};
