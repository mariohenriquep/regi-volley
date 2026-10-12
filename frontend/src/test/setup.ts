import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './server';

// findBy*/waitFor wait up to 3 s instead of 1 s: slow or loaded machines (CI runners) must not make them flaky.
configure({ asyncUtilTimeout: 3000 });

// Radix measures some controls (the checkbox) with ResizeObserver, which jsdom lacks.
if (typeof window !== 'undefined' && typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class implements ResizeObserver {
    observe() {
      /* layout does not exist in jsdom */
    }
    unobserve() {
      /* layout does not exist in jsdom */
    }
    disconnect() {
      /* layout does not exist in jsdom */
    }
  };
}

beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' });
});

afterEach(() => {
  server.resetHandlers();
  // Node-environment tests (pwa.config.test.ts) have no DOM.
  if (typeof window !== 'undefined') {
    cleanup();
    window.history.replaceState(null, '', '/');
  }
});

afterAll(() => {
  server.close();
});
