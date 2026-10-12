import type { RouteObject } from 'react-router';
import { PublicOnly, RequireAuth } from '@/auth/guards';
import { Loading } from '@/components/Loading';
import { AppLayout } from '@/layout/AppLayout';
import { accountLinkLoader, neverRevalidate } from '@/pages/accountLink';
import { LoginPage } from '@/pages/LoginPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { RouteError } from '@/pages/RouteError';

/**
 * Every screen except the shell, the guards, sign-in and the 404 page loads its code when its route is first visited, so the
 * first download is the app shell and not the whole app. The service worker precaches every chunk (pwa.config.ts), so
 * a visited-once app works from the cache.
 */
export const routes: RouteObject[] = [
  {
    element: <AppLayout />,
    // Only if the shell itself fails; a page that fails is caught below and keeps the header and the bar.
    errorElement: <RouteError />,
    // The first navigation waits for the code of its page.
    HydrateFallback: Loading,
    children: [
      {
        errorElement: <RouteError />,
        children: [
          // The member's screens: the week (home), the plan and the history.
          {
            element: <RequireAuth />,
            children: [
              {
                index: true,
                lazy: async () => ({
                  Component: (await import('@/features/sessions/SessionsPage')).SessionsPage,
                }),
              },
              {
                path: 'me/plan',
                lazy: async () => ({
                  Component: (await import('@/features/plan/MyPlanPage')).MyPlanPage,
                }),
              },
              {
                path: 'me/history',
                lazy: async () => ({
                  Component: (await import('@/features/history/MyHistoryPage')).MyHistoryPage,
                }),
              },
            ],
          },
          // Visitors only: a signed-in user is sent home.
          {
            element: <PublicOnly />,
            children: [
              { path: 'login', element: <LoginPage /> },
              {
                path: 'register',
                lazy: async () => ({
                  Component: (await import('@/features/association/RegisterPage')).RegisterPage,
                }),
              },
              {
                path: 'forgot-password',
                lazy: async () => ({
                  Component: (await import('@/features/account/ForgotPasswordPage'))
                    .ForgotPasswordPage,
                }),
              },
            ],
          },
          // The links in the emails (WEB_ORIGIN/activate#token=..., /reset-password#token=...): open to anyone.
          {
            path: 'activate',
            loader: accountLinkLoader,
            shouldRevalidate: neverRevalidate,
            lazy: async () => ({
              Component: (await import('@/features/account/PasswordLinkPage')).ActivatePage,
            }),
          },
          {
            path: 'reset-password',
            loader: accountLinkLoader,
            shouldRevalidate: neverRevalidate,
            lazy: async () => ({
              Component: (await import('@/features/account/PasswordLinkPage')).ResetPasswordPage,
            }),
          },
          // Public pages: open to everyone, signed in or not.
          {
            path: 'a/:shortName',
            lazy: async () => ({
              Component: (await import('@/features/association/PublicAssociationPage'))
                .PublicAssociationPage,
            }),
          },
          {
            path: 'a/:shortName/join',
            lazy: async () => ({
              Component: (await import('@/features/association/JoinPage')).JoinPage,
            }),
          },
          {
            path: 'privacy',
            lazy: async () => ({
              Component: (await import('@/pages/PrivacyPage')).PrivacyPage,
            }),
          },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
];
