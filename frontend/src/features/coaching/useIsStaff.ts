import { useMe } from '@/auth/useMe';

/**
 * Whether to show the Coaching section: the member holds COACH or ADMIN, as `GET /me` says. `settled` is false until that
 * answer (or its failure) is in, so the shell can show its navigation once, in its final shape. Not a security boundary:
 * the API decides on every call.
 */
export function useIsStaff(): { settled: boolean; staff: boolean } {
  const me = useMe();
  const roles = me.data?.roles ?? [];
  return {
    settled: !me.isPending,
    staff: roles.includes('COACH') || roles.includes('ADMIN'),
  };
}
