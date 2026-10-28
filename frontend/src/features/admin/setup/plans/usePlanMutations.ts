import { unwrap } from '@/api/errors';
import type { components } from '@/api/schema.gen';
import { plansKey } from '../keys';
import { useSetupMutation } from '../useSetupMutation';

/** Creates the plan, or replaces what the one with `id` says. */
export function useSavePlan() {
  return useSetupMutation(
    async (
      api,
      { id, body }: { id: string | null; body: components['schemas']['PlanRequest'] },
    ): Promise<void> => {
      await unwrap(
        id === null
          ? api.POST('/api/v1/plans', { body })
          : api.PUT('/api/v1/plans/{planId}', { params: { path: { planId: id } }, body }),
      );
    },
    [plansKey],
  );
}
