import { useQuery } from '@tanstack/react-query';
import { unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import { type Session, toSessions } from './model';

export const sessionsKey = ['sessions'] as const;

/** The bookable sessions of the Lisbon week that starts on `weekOf` (a Monday, `YYYY-MM-DD`). */
export function useWeek(weekOf: string) {
  const { api } = useAuth();
  return useQuery({
    queryKey: [...sessionsKey, weekOf],
    queryFn: async (): Promise<Session[]> => {
      const response = await unwrap(api.GET('/api/v1/sessions', { params: { query: { weekOf } } }));
      return toSessions(response);
    },
  });
}
