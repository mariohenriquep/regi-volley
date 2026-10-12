import { type QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { AuthProvider } from '@/auth/AuthProvider';
import type { AuthSession } from '@/auth/session';

export function AppProviders({
  session,
  queryClient,
  children,
}: {
  session: AuthSession;
  queryClient: QueryClient;
  children: ReactNode;
}) {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider session={session}>{children}</AuthProvider>
    </QueryClientProvider>
  );
}
