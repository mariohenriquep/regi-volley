import { useMe } from '@/auth/useMe';

/**
 * Whether to show the Admin section: the member holds ADMIN, as `GET /me` says. `settled` is false until that answer (or
 * its failure) is in. Not a security boundary: the API decides on every call.
 */
export function useIsAdmin(): { settled: boolean; admin: boolean } {
  const me = useMe();
  return {
    settled: !me.isPending,
    admin: me.data?.roles.includes('ADMIN') ?? false,
  };
}
