import { type QueryClient, useMutation, useQueryClient } from '@tanstack/react-query';
import { unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import { historyKey } from '@/features/history/useHistory';
import { planKey } from '@/features/plan/usePlan';
import { forgetBooking, rememberBooking } from './bookingMemory';
import { sessionsKey } from './useWeek';

/** A booking changes the week's seats, the plan's balance and the history: refetch them (when next shown). */
function refresh(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: sessionsKey });
  void queryClient.invalidateQueries({ queryKey: planKey });
  void queryClient.invalidateQueries({ queryKey: historyKey });
}

export interface Placed {
  status: string;
  waitlistPosition: number | undefined;
}

/** Books a session. The server decides confirmed or waitlisted; nothing is shown before it answers. */
export function useBookSession() {
  const { api, epoch } = useAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (sessionId: string): Promise<Placed> => {
      const started = epoch();
      const placed = await unwrap(
        api.POST('/api/v1/sessions/{sessionId}/bookings', { params: { path: { sessionId } } }),
      );
      // Signed out (or another user in) while the call was in flight: its answer belongs to nobody now.
      if (placed.bookingId && epoch() === started)
        rememberBooking(queryClient, sessionId, placed.bookingId);
      return { status: placed.status ?? '', waitlistPosition: placed.waitlistPosition };
    },
    // Also after a refusal: a 409 or 404 means the list on screen is out of date.
    onSettled: () => {
      refresh(queryClient);
    },
  });
}

export interface Cancelled {
  late: boolean;
  creditRefunded: boolean;
  promotedCount: number;
}

export function useCancelBooking() {
  const { api, epoch } = useAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (target: { sessionId: string; bookingId: string }): Promise<Cancelled> => {
      const started = epoch();
      const outcome = await unwrap(
        api.POST('/api/v1/sessions/{sessionId}/bookings/{bookingId}/cancellation', {
          params: { path: target },
        }),
      );
      if (epoch() === started) forgetBooking(queryClient, target.sessionId);
      return {
        late: outcome.late ?? false,
        creditRefunded: outcome.creditRefunded ?? false,
        promotedCount: outcome.promotedCount ?? 0,
      };
    },
    onSettled: () => {
      refresh(queryClient);
    },
  });
}
