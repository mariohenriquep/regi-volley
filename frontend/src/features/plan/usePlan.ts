import { useQuery } from '@tanstack/react-query';
import { unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import { toPlan } from './model';

export const planKey = ['plan'] as const;

export function usePlan(options: { enabled?: boolean } = {}) {
  const { api } = useAuth();
  return useQuery({
    queryKey: planKey,
    queryFn: async () => toPlan(await unwrap(api.GET('/api/v1/me/plan'))),
    enabled: options.enabled ?? true,
  });
}
