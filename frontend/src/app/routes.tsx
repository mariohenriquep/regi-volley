import type { RouteObject } from 'react-router';
import { PublicOnly, RequireAuth } from '@/auth/guards';
import { ForgotPasswordPage } from '@/features/account/ForgotPasswordPage';
import { ActivatePage, ResetPasswordPage } from '@/features/account/PasswordLinkPage';
import { JoinPage } from '@/features/association/JoinPage';
import { PublicAssociationPage } from '@/features/association/PublicAssociationPage';
import { RegisterPage } from '@/features/association/RegisterPage';
import { AppLayout } from '@/layout/AppLayout';
import { accountLinkLoader, neverRevalidate } from '@/pages/accountLink';
import { HomePage } from '@/pages/HomePage';
import { LoginPage } from '@/pages/LoginPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { PrivacyPage } from '@/pages/PrivacyPage';

export const routes: RouteObject[] = [
  {
    element: <AppLayout />,
    children: [
      { element: <RequireAuth />, children: [{ index: true, element: <HomePage /> }] },
      // Visitors only: a signed-in user is sent home.
      {
        element: <PublicOnly />,
        children: [
          { path: 'login', element: <LoginPage /> },
          { path: 'register', element: <RegisterPage /> },
          { path: 'forgot-password', element: <ForgotPasswordPage /> },
        ],
      },
      // The links in the emails (WEB_ORIGIN/activate#token=..., /reset-password#token=...): open to anyone.
      {
        path: 'activate',
        loader: accountLinkLoader,
        shouldRevalidate: neverRevalidate,
        element: <ActivatePage />,
      },
      {
        path: 'reset-password',
        loader: accountLinkLoader,
        shouldRevalidate: neverRevalidate,
        element: <ResetPasswordPage />,
      },
      // Public pages: open to everyone, signed in or not.
      { path: 'a/:shortName', element: <PublicAssociationPage /> },
      { path: 'a/:shortName/join', element: <JoinPage /> },
      { path: 'privacy', element: <PrivacyPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
