import { type QueryClient, useMutation, useQueryClient } from '@tanstack/react-query';
import { unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import { historyKey } from '@/features/history/useHistory';
import { planKey } from '@/features/plan/usePlan';
import { sessionsKey } from '@/features/sessions/useWeek';
import { coachingKey, rosterKey } from './useCoaching';

/** Whatever a staff action does, every list that shows the session is refetched (also after a refusal: the page may be stale). */
function refresh(queryClient: QueryClient) {
  for (const queryKey of [coachingKey, rosterKey, sessionsKey, planKey, historyKey]) {
    void queryClient.invalidateQueries({ queryKey });
  }
}

export type Mark = 'ATTENDED' | 'NO_SHOW';

export interface AttendanceSaved {
  attended: number;
  noShow: number;
  /** Members who reached the no-show limit with this batch and were warned. */
  warned: number;
}

/** One batch, all or nothing: the API refuses the whole thing if any entry is refused. */
export function useSaveAttendance(sessionId: string) {
  const { api } = useAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (marks: Record<string, Mark>): Promise<AttendanceSaved> => {
      const entries = Object.entries(marks).map(([bookingId, mark]) => ({ bookingId, mark }));
      const outcome = await unwrap(
        api.PUT('/api/v1/sessions/{sessionId}/attendance', {
          params: { path: { sessionId } },
          body: { entries },
        }),
      );
      return {
        attended: entries.filter(({ mark }) => mark === 'ATTENDED').length,
        noShow: entries.filter(({ mark }) => mark === 'NO_SHOW').length,
        warned: outcome.warnedCount ?? 0,
      };
    },
    onSettled: () => {
      refresh(queryClient);
    },
  });
}

export interface SessionCancelled {
  bookingsCancelled: number;
  creditsRefunded: number;
}

export function useCancelSession() {
  const { api } = useAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (target: {
      sessionId: string;
      reason: string;
    }): Promise<SessionCancelled> => {
      const outcome = await unwrap(
        api.POST('/api/v1/sessions/{sessionId}/cancellation', {
          params: { path: { sessionId: target.sessionId } },
          body: { reason: target.reason },
        }),
      );
      return {
        bookingsCancelled: outcome.bookingsCancelled ?? 0,
        creditsRefunded: outcome.creditsRefunded ?? 0,
      };
    },
    onSettled: () => {
      refresh(queryClient);
    },
  });
}

export interface CapacityChanged {
  capacity: number;
  promotedCount: number;
}

export function useChangeCapacity() {
  const { api } = useAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (target: {
      sessionId: string;
      capacity: number;
    }): Promise<CapacityChanged> => {
      const outcome = await unwrap(
        api.PUT('/api/v1/sessions/{sessionId}/capacity', {
          params: { path: { sessionId: target.sessionId } },
          body: { capacity: target.capacity },
        }),
      );
      return {
        capacity: outcome.session?.capacity ?? target.capacity,
        promotedCount: outcome.promotedCount ?? 0,
      };
    },
    onSettled: () => {
      refresh(queryClient);
    },
  });
}
