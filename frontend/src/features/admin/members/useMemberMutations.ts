import { type QueryClient, useMutation, useQueryClient } from '@tanstack/react-query';
import { unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import { meKey, type Role } from '@/auth/useMe';
import { coachingKey, rosterKey } from '@/features/coaching/useCoaching';
import { historyKey } from '@/features/history/useHistory';
import { planKey } from '@/features/plan/usePlan';
import { sessionsKey } from '@/features/sessions/useWeek';
import { membersKey } from './useMembers';

/**
 * Whatever an administrator does to a member, the member and the list are read again (also after a refusal: the page may
 * be stale). A role change can be one's own, so `/me` is read again too; a deactivation cancels bookings, so the booking
 * screens are refreshed as well.
 */
function refresh(queryClient: QueryClient, ...extra: readonly (readonly string[])[]) {
  for (const queryKey of [membersKey, ...extra]) {
    void queryClient.invalidateQueries({ queryKey });
  }
}

export function useChangeLevel(memberId: string) {
  const { api } = useAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (levelId: string): Promise<void> => {
      await unwrap(
        api.PUT('/api/v1/members/{memberId}/level', {
          params: { path: { memberId } },
          body: { levelId },
        }),
      );
    },
    onSettled: () => {
      refresh(queryClient);
    },
  });
}

/** The roles an administrator can give and take away here; MEMBER is every member's own and is not offered. */
export type ManagedRole = Exclude<Role, 'MEMBER'>;

export function useChangeRole(memberId: string) {
  const { api } = useAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ role, grant }: { role: ManagedRole; grant: boolean }): Promise<void> => {
      const params = { params: { path: { memberId, role } } };
      await unwrap(
        grant
          ? api.PUT('/api/v1/members/{memberId}/roles/{role}', params)
          : api.DELETE('/api/v1/members/{memberId}/roles/{role}', params),
      );
    },
    onSettled: () => {
      refresh(queryClient, meKey);
    },
  });
}

export interface Deactivated {
  bookingsCancelled: number;
  /** Sessions where the booking could not be cancelled; the deactivation stands and repeating it retries exactly these. */
  failedSessions: number;
}

export function useDeactivateMember(memberId: string) {
  const { api } = useAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (): Promise<Deactivated> => {
      const outcome = await unwrap(
        api.POST('/api/v1/members/{memberId}/deactivation', { params: { path: { memberId } } }),
      );
      return {
        bookingsCancelled: outcome.bookingsCancelled ?? 0,
        failedSessions: Array.isArray(outcome.failedSessionIds)
          ? outcome.failedSessionIds.length
          : 0,
      };
    },
    onSettled: () => {
      refresh(queryClient, meKey, coachingKey, rosterKey, sessionsKey, planKey, historyKey);
    },
  });
}

/** The answer is the same 202 whatever happened (threat model P7), so there is nothing to read from it. */
export function useResendActivationLink(memberId: string) {
  const { api } = useAuth();
  return useMutation({
    mutationFn: async (): Promise<void> => {
      await unwrap(
        api.POST('/api/v1/members/{memberId}/activation-links', {
          params: { path: { memberId } },
        }),
      );
    },
  });
}
