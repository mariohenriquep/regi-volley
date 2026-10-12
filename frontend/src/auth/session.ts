import createClient, { type Client } from 'openapi-fetch';
import { unwrap } from '@/api/errors';
import type { paths } from '@/api/schema.gen';

export type AuthStatus = 'unknown' | 'authenticated' | 'anonymous';

export type ApiClient = Client<paths>;

/**
 * The browser side of the API's session design (docs/architecture.md section 15, threat model D-7, D-7a):
 *
 * - the access token lives in this closure only, never in web storage, so a reload loses it and {@link start} asks
 *   for a new one with the refresh cookie;
 * - the refresh token is the `__Secure-rt` HttpOnly cookie, sent by the browser to `/api/v1/auth` only; the two
 *   cookie routes (`/auth/refresh`, `/auth/logout`) are called same-origin with `Content-Type: application/json`,
 *   which `CookieEndpointGuardFilter` requires;
 * - `api` adds the bearer header (only to this origin's `/api/`, redirects refused) and, on a 401, refreshes (one
 *   refresh in flight per tab, one per browser at a time through the `rv-refresh` Web Lock) and retries once;
 * - signing out bumps an epoch, so a refresh that was in flight cannot sign the user back in.
 */
export interface AuthSession {
  /** The typed API client for every authenticated call. */
  readonly api: ApiClient;
  readonly getStatus: () => AuthStatus;
  readonly subscribe: (listener: () => void) => () => void;
  /** Restores the session from the refresh cookie, if there is one. */
  readonly start: () => Promise<void>;
  /** Asks the API for a new access token. Concurrent calls share one request. */
  readonly refresh: () => Promise<boolean>;
  readonly login: (email: string, password: string) => Promise<void>;
  /**
   * Revokes this device's session (its refresh family) on the server and always forgets the access token here.
   * Rejects with an {@link ApiError} when the server did not confirm (the cookie may still be valid: call it again).
   */
  readonly logout: () => Promise<void>;
  /** Revokes every session of the account; signs out here only when the server confirmed (rejects otherwise). */
  readonly logoutAll: () => Promise<void>;
}

/** The part of the Web Locks API the refresh needs (`navigator.locks`). */
export interface RefreshLock {
  request<T>(name: string, callback: () => Promise<T>): Promise<T>;
}

export interface AuthSessionOptions {
  /** The API's origin: the page's own, the API being served under /api (Vite proxy in dev, reverse proxy in prod). */
  baseUrl: string;
  fetch?: (request: Request) => Promise<Response>;
  /** Serialises refreshes across tabs. Defaults to `navigator.locks`; null where it is missing (one tab at a time). */
  locks?: RefreshLock | null;
}

const JSON_ONLY = { 'Content-Type': 'application/json' } as const;
/** Refresh answers that mean "no session": the cookie is missing, expired, rotated or revoked. */
const SESSION_GONE = new Set([400, 401, 403]);
/** Shared by every tab of the origin: two tabs presenting the same refresh cookie at once would look like reuse. */
const REFRESH_LOCK = 'rv-refresh';

function browserLocks(): RefreshLock | null {
  if (!('locks' in navigator)) return null;
  const locks = navigator.locks;
  // `await` unwraps the callback's promise, which LockManager's typing keeps nested (it resolves it at run time).
  return { request: async (name, callback) => await locks.request(name, callback) };
}

export function createAuthSession(options: AuthSessionOptions): AuthSession {
  // Looked up per call so test doubles of the global fetch (MSW) are always honoured.
  const send = options.fetch ?? ((request: Request) => globalThis.fetch(request));
  const locks = options.locks === undefined ? browserLocks() : options.locks;
  let accessToken: string | null = null;
  let status: AuthStatus = 'unknown';
  let inflightRefresh: Promise<boolean> | null = null;
  /** Bumped by every sign-out: a refresh started before it must not sign the user back in. */
  let epoch = 0;
  const listeners = new Set<() => void>();

  const setState = (token: string | null, next: AuthStatus) => {
    accessToken = token;
    if (next === status) return;
    status = next;
    listeners.forEach((listener) => {
      listener();
    });
  };
  const signIn = (token: string) => {
    setState(token, 'authenticated');
  };
  const signOut = () => {
    epoch += 1;
    setState(null, 'anonymous');
  };

  // Redirects are refused: an API answer is never followed somewhere else.
  /** Credential routes: never a bearer header, never the 401 retry. */
  const credentials = createClient<paths>({
    baseUrl: options.baseUrl,
    fetch: send,
    redirect: 'error',
  });

  /** The bearer token goes to this origin's /api/ only; anything else is a programming error. */
  const withBearer = (request: Request, token: string | null) => {
    const url = new URL(request.url);
    if (url.origin !== globalThis.location.origin || !url.pathname.startsWith('/api/')) {
      throw new Error(`Refusing to send credentials outside the API: ${url.origin}${url.pathname}`);
    }
    if (token) request.headers.set('Authorization', `Bearer ${token}`);
    return request;
  };

  const authenticatedFetch = async (request: Request): Promise<Response> => {
    const tokenUsed = accessToken;
    const first = withBearer(request, tokenUsed);
    const retry = request.clone();
    const response = await send(first);
    if (response.status !== 401) return response;
    // Another request may already have refreshed while this one was in flight: then just retry with the new token.
    const refreshed = accessToken !== tokenUsed && accessToken !== null ? true : await refresh();
    if (!refreshed || accessToken === null) return response;
    const second = await send(withBearer(retry, accessToken));
    // A token the API issued a moment ago is refused: the session is gone (deactivated, stamp rotated...).
    if (second.status === 401) signOut();
    return second;
  };

  const api = createClient<paths>({
    baseUrl: options.baseUrl,
    fetch: authenticatedFetch,
    redirect: 'error',
  });

  async function requestNewToken(): Promise<boolean> {
    const startedIn = epoch;
    try {
      const { data, response } = await credentials.POST('/api/v1/auth/refresh', {
        headers: JSON_ONLY,
      });
      if (epoch !== startedIn) return false;
      if (response.ok && data?.accessToken) {
        signIn(data.accessToken);
        return true;
      }
      if (SESSION_GONE.has(response.status) || status === 'unknown') signOut();
      return false;
    } catch {
      // Unreachable API: keep a running session (the caller sees its error), but do not pretend to know one on start.
      if (epoch === startedIn && status === 'unknown') signOut();
      return false;
    }
  }

  function refresh(): Promise<boolean> {
    inflightRefresh ??= (
      locks ? locks.request(REFRESH_LOCK, requestNewToken) : requestNewToken()
    ).finally(() => {
      inflightRefresh = null;
    });
    return inflightRefresh;
  }

  return {
    api,
    getStatus: () => status,
    subscribe: (listener) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    start: async () => {
      await refresh();
    },
    refresh,
    login: async (email, password) => {
      const token = await unwrap(
        credentials.POST('/api/v1/auth/login', { body: { email, password } }),
      );
      if (!token.accessToken) throw new Error('The login response carries no access token');
      signIn(token.accessToken);
    },
    logout: async () => {
      try {
        await unwrap(credentials.POST('/api/v1/auth/logout', { headers: JSON_ONLY }));
      } finally {
        signOut();
      }
    },
    logoutAll: async () => {
      await unwrap(api.POST('/api/v1/auth/logout-all'));
      signOut();
    },
  };
}
