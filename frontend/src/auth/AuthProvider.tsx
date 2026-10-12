import { useQueryClient } from '@tanstack/react-query';
import {
  type ReactNode,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  useSyncExternalStore,
} from 'react';
import { AuthContext, type AuthContextValue } from './context';
import type { AuthSession } from './session';

/**
 * Exposes the {@link AuthSession} to React: its status (unknown until the start-up refresh answered), the API client
 * and the login / logout actions. Every cached query is cancelled and dropped whenever the user changes (sign-in or
 * sign-out, not the first answer after start-up), so nothing of one user is ever shown to the next.
 */
export function AuthProvider({ session, children }: { session: AuthSession; children: ReactNode }) {
  const queryClient = useQueryClient();
  const status = useSyncExternalStore(session.subscribe, session.getStatus);
  const [logoutFailed, setLogoutFailed] = useState(false);
  const epochRef = useRef(0);
  const epoch = useCallback(() => epochRef.current, []);

  useEffect(() => {
    // Under StrictMode this runs twice; the session shares the one refresh in flight.
    if (session.getStatus() === 'unknown') void session.start();
  }, [session]);

  useEffect(() => {
    let previous = session.getStatus();
    // Runs synchronously inside the status change, before React renders the next user's pages. The first answer
    // (unknown to anonymous or authenticated) changes no user: what public pages loaded meanwhile is kept.
    return session.subscribe(() => {
      const current = session.getStatus();
      const userChanged = previous !== 'unknown';
      previous = current;
      if (!userChanged) return;
      epochRef.current += 1;
      void queryClient.cancelQueries();
      queryClient.clear();
    });
  }, [session, queryClient]);

  const logout = useCallback(async () => {
    try {
      await session.logout();
      setLogoutFailed(false);
    } catch {
      setLogoutFailed(true);
    }
  }, [session]);

  const login = useCallback(
    async (email: string, password: string) => {
      await session.login(email, password);
      setLogoutFailed(false);
    },
    [session],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      api: session.api,
      publicApi: session.publicApi,
      login,
      logout,
      logoutFailed,
      logoutAll: session.logoutAll,
      epoch,
    }),
    [status, session, login, logout, logoutFailed, epoch],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
