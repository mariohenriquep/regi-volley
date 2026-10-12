import { type QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { AuthProvider } from '@/auth/AuthProvider';
import type { AuthSession } from '@/auth/session';
import { UpdateContext } from '@/pwa/context';
import type { UpdateStore } from '@/pwa/store';

export function AppProviders({
  session,
  queryClient,
  updates,
  children,
}: {
  session: AuthSession;
  queryClient: QueryClient;
  /** Where the service worker offers a new version; without one the app never prompts. */
  updates?: UpdateStore;
  children: ReactNode;
}) {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider session={session}>
        {updates ? (
          <UpdateContext.Provider value={updates}>{children}</UpdateContext.Provider>
        ) : (
          children
        )}
      </AuthProvider>
    </QueryClientProvider>
  );
}
