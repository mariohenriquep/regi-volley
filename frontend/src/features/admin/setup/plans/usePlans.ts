import { useQuery } from '@tanstack/react-query';
import { unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import { plansKey } from '../keys';
import { type Plan, toPlans } from './model';

/** The association's plans, with their allowed levels named. */
export function usePlans() {
  const { api } = useAuth();
  return useQuery({
    queryKey: plansKey,
    queryFn: async (): Promise<Plan[]> => toPlans(await unwrap(api.GET('/api/v1/plans'))),
  });
}
