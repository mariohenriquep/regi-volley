import { createContext, useContext } from 'react';
import type { ApiClient, AuthStatus } from './session';

export interface AuthContextValue {
  status: AuthStatus;
  /** The typed API client: bearer token, refresh on 401 and one retry built in. */
  api: ApiClient;
  /** The typed API client for the public routes: no bearer token, no refresh. */
  publicApi: ApiClient;
  /**
   * Counts the times the signed-in user changed (sign-in, sign-out, session end). A call that started in one epoch must not
   * write its answer into the cache of the next: compare the value before and after it.
   */
  epoch: () => number;
  login: (email: string, password: string) => Promise<void>;
  /** Signs out here at once; never rejects. When the server did not confirm, `logoutFailed` turns true. */
  logout: () => Promise<void>;
  /** True after a sign-out the server did not confirm: the device's session may still be valid there. */
  logoutFailed: boolean;
  /** Signs out on every device; rejects (and stays signed in) when the server did not confirm. */
  logoutAll: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>');
  return value;
}
