import type { VitePWAOptions } from 'vite-plugin-pwa';

export const THEME_COLOR = '#0f3d75';

/**
 * The PWA (vite-plugin-pwa, Workbox generateSW). The service worker precaches the built app shell and nothing else:
 * no runtime caching at all, so no API response (personal data) is ever stored by it, and a navigation under /api is
 * never answered with the cached index.html.
 *
 * Updates: a new worker activates at once (skipWaiting) and takes control of the open pages (clientsClaim); the app
 * registers it from its own bundle (`virtual:pwa-register` in main.tsx, so index.html gets no extra script, CSP),
 * whose autoUpdate helper reloads every open page when the new version activates. A reload loses the in-memory access
 * token only, which the start-up refresh restores. An update prompt can replace this once forms hold unsaved input.
 */
export const pwaOptions: Partial<VitePWAOptions> = {
  registerType: 'autoUpdate',
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
    skipWaiting: true,
    clientsClaim: true,
  },
  devOptions: { enabled: false },
};
