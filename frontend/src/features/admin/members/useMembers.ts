import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import {
  type LevelOption,
  type MemberDetail,
  type MemberListing,
  type StatusFilter,
  toLevelOptions,
  toMemberDetail,
  toMemberListing,
} from './model';

/** Every query about members starts with this key, so one invalidation refreshes the list and every open member. */
export const membersKey = ['members'] as const;
export const levelsKey = ['levels'] as const;

/**
 * The association's members by name, narrowed by status and by a search text (name or email). While a new search is on its
 * way the previous list stays on screen, so typing does not flash a skeleton.
 */
export function useMembers(filter: { status: StatusFilter | undefined; q: string }) {
  const { api } = useAuth();
  const { status, q } = filter;
  return useQuery({
    queryKey: [...membersKey, 'list', { status: status ?? null, q }],
    queryFn: async (): Promise<MemberListing> =>
      toMemberListing(
        await unwrap(
          api.GET('/api/v1/members', {
            params: { query: { ...(status ? { status } : {}), ...(q ? { q } : {}) } },
          }),
        ),
      ),
    placeholderData: keepPreviousData,
  });
}

/** One member in full. A member the API does not know (or from another association) is a 404, not retried. */
export function useMember(memberId: string) {
  const { api } = useAuth();
  return useQuery({
    queryKey: [...membersKey, 'detail', memberId],
    queryFn: async (): Promise<MemberDetail | null> =>
      toMemberDetail(
        await unwrap(api.GET('/api/v1/members/{memberId}', { params: { path: { memberId } } })),
      ),
  });
}

/** The levels a member can be moved to. */
export function useLevels() {
  const { api } = useAuth();
  return useQuery({
    queryKey: levelsKey,
    queryFn: async (): Promise<LevelOption[]> =>
      toLevelOptions(await unwrap(api.GET('/api/v1/levels'))),
  });
}
