import { type QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import { membersKey } from '../members/useMembers';
import { type JoinRequest, toJoinRequests } from './model';

export const joinRequestsKey = ['join-requests'] as const;

/** The requests waiting for a decision, oldest first. The API answers 403 to a member who is not an administrator. */
export function useJoinRequests() {
  const { api } = useAuth();
  return useQuery({
    queryKey: joinRequestsKey,
    queryFn: async (): Promise<JoinRequest[]> =>
      toJoinRequests(await unwrap(api.GET('/api/v1/join-requests'))),
  });
}

/** A decision changes the list, and an approval adds a member. Also after a refusal: the request may be decided already. */
function refresh(queryClient: QueryClient) {
  for (const queryKey of [joinRequestsKey, membersKey]) {
    void queryClient.invalidateQueries({ queryKey });
  }
}

export interface Approved {
  memberId: string | undefined;
}

export function useApproveJoinRequest() {
  const { api } = useAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (requestId: string): Promise<Approved> => {
      const outcome = await unwrap(
        api.POST('/api/v1/join-requests/{requestId}/approval', {
          params: { path: { requestId } },
        }),
      );
      return { memberId: outcome.memberId };
    },
    onSettled: () => {
      refresh(queryClient);
    },
  });
}

export function useRejectJoinRequest() {
  const { api } = useAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      requestId,
      reason,
    }: {
      requestId: string;
      reason: string;
    }): Promise<void> => {
      await unwrap(
        api.POST('/api/v1/join-requests/{requestId}/rejection', {
          params: { path: { requestId } },
          body: reason === '' ? {} : { reason },
        }),
      );
    },
    onSettled: () => {
      refresh(queryClient);
    },
  });
}
