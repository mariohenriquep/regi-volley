import type { ComponentType } from 'react';

/**
 * Stands in for `react-style-singleton` (see `vite.config.ts`, `resolve.alias`).
 *
 * Radix dialogs and menus lock the page scroll through `react-remove-scroll`, which injects a `<style>` element with the
 * rules for `body[data-scroll-locked]`. The production Content-Security-Policy is `style-src 'self'` (docs/architecture.md
 * section 15), which blocks any `<style>` element, and a nonce would need a server-generated policy per response. So the
 * injection is dropped here and the same rules ship in the stylesheet (`src/index.css`); the library still sets the
 * `data-scroll-locked` attribute, which is all those rules need. `e2e-csp/csp.spec.ts` fails if a violation ever returns.
 */
export function styleSingleton(): ComponentType<{ styles: string; dynamic?: boolean }> {
  return function NoStyle() {
    return null;
  };
}
