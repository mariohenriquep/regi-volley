import { Navigate, Outlet, useLocation } from 'react-router';
import { Loading } from '@/components/Loading';
import { useAuth } from './context';

/** Where a guarded page sends an anonymous visitor, remembering the page asked for. */
export interface ReturnTo {
  from?: string;
}

/**
 * Only a path that stays on this origin once the browser resolves it: no open redirect through `//evil.com`,
 * `/\evil.com` (backslashes count as slashes) or an absolute URL.
 */
function isOwnPath(path: string | undefined): path is string {
  if (!path?.startsWith('/')) return false;
  try {
    return new URL(path, window.location.origin).origin === window.location.origin;
  } catch {
    return false;
  }
}

/** Pages for a signed-in user only: an anonymous visitor goes to /login and comes back after signing in. */
export function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'unknown') return <Loading />;
  if (status === 'anonymous') {
    const state: ReturnTo = { from: location.pathname + location.search };
    return <Navigate to="/login" replace state={state} />;
  }
  return <Outlet />;
}

/** Pages for visitors only (sign in): a signed-in user goes where they were heading, or home. */
export function PublicOnly() {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'unknown') return <Loading />;
  if (status === 'authenticated') {
    const from = (location.state as ReturnTo | null)?.from;
    return <Navigate to={isOwnPath(from) ? from : '/'} replace />;
  }
  return <Outlet />;
}
