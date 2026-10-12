import './index.css';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router';
import { registerSW } from 'virtual:pwa-register';
import { AppProviders } from '@/app/AppProviders';
import { createQueryClient } from '@/app/queryClient';
import { routes } from '@/app/routes';
import { createAuthSession } from '@/auth/session';

// Same origin: the API is served under /api next to the app (Vite proxy in dev, reverse proxy in production).
const session = createAuthSession({ baseUrl: window.location.origin });
const queryClient = createQueryClient();
const router = createBrowserRouter(routes);

// The service worker (pwa.config.ts): autoUpdate reloads the open pages when a new version activates.
registerSW({ immediate: true });

const root = document.getElementById('root');
if (!root) throw new Error('#root is missing from index.html');

createRoot(root).render(
  <StrictMode>
    <AppProviders session={session} queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>
  </StrictMode>,
);
