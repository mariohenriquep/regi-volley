import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '@/auth/context';
import type { ApiClient } from '@/auth/session';

/**
 * A call to the API that changes the association's setup. Whatever the answer (a refusal included: the page may be stale),
 * the lists in `refresh` are read again, so every screen that shows what was changed follows it.
 */
export function useSetupMutation<Variables, Result = void>(
  run: (api: ApiClient, variables: Variables) => Promise<Result>,
  refresh: readonly (readonly string[])[],
) {
  const { api } = useAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables: Variables) => run(api, variables),
    onSettled: () => {
      for (const queryKey of refresh) void queryClient.invalidateQueries({ queryKey });
    },
  });
}
