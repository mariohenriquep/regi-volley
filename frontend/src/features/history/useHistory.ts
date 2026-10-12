import { useQuery } from '@tanstack/react-query';
import { unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import { toHistory } from './entries';

export const historyKey = ['history'] as const;

export function useHistory() {
  const { api } = useAuth();
  return useQuery({
    queryKey: historyKey,
    queryFn: async () => toHistory(await unwrap(api.GET('/api/v1/me/history'))),
  });
}
