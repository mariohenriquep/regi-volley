import { useQuery } from '@tanstack/react-query';
import { unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import { type CoachingSession, type Roster, toCoachingSessions, toRoster } from './model';

export const coachingKey = ['coaching'] as const;
export const rosterKey = ['roster'] as const;

/**
 * The staff's sessions of the Lisbon week that starts on `weekOf` (a Monday, `YYYY-MM-DD`). The API answers 403 to a
 * member who is neither coach nor administrator; that answer is also how the app decides whether to show the Coaching
 * section at all (`useIsStaff`).
 */
export function useCoachingWeek(weekOf: string, options: { enabled?: boolean } = {}) {
  const { api } = useAuth();
  return useQuery({
    queryKey: [...coachingKey, weekOf],
    queryFn: async (): Promise<CoachingSession[]> =>
      toCoachingSessions(
        await unwrap(api.GET('/api/v1/coaching/sessions', { params: { query: { weekOf } } })),
      ),
    enabled: options.enabled ?? true,
  });
}

/** Who holds a seat and who waits, with the booking ids attendance is marked with. */
export function useRoster(sessionId: string) {
  const { api } = useAuth();
  return useQuery({
    queryKey: [...rosterKey, sessionId],
    queryFn: async (): Promise<Roster | null> =>
      toRoster(
        await unwrap(
          api.GET('/api/v1/sessions/{sessionId}/roster', { params: { path: { sessionId } } }),
        ),
      ),
  });
}
