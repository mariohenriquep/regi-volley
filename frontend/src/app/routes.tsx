import type { RouteObject } from 'react-router';
import { PublicOnly, RequireAuth } from '@/auth/guards';
import { AppLayout } from '@/layout/AppLayout';
import { AccountLinkPage } from '@/pages/AccountLinkPage';
import { accountLinkLoader, neverRevalidate } from '@/pages/accountLink';
import { HomePage } from '@/pages/HomePage';
import { LoginPage } from '@/pages/LoginPage';
import { NotFoundPage } from '@/pages/NotFoundPage';

export const routes: RouteObject[] = [
  {
    element: <AppLayout />,
    children: [
      { element: <RequireAuth />, children: [{ index: true, element: <HomePage /> }] },
      { element: <PublicOnly />, children: [{ path: 'login', element: <LoginPage /> }] },
      // The links in the emails (WEB_ORIGIN/activate#token=..., /reset-password#token=...): open to anyone.
      {
        path: 'activate',
        loader: accountLinkLoader,
        shouldRevalidate: neverRevalidate,
        element: <AccountLinkPage title="Activate your account" />,
      },
      {
        path: 'reset-password',
        loader: accountLinkLoader,
        shouldRevalidate: neverRevalidate,
        element: <AccountLinkPage title="Choose a new password" />,
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
