import { useQuery } from '@tanstack/react-query';
import { unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import { membersKey } from '../../members/useMembers';
import { toMemberListing } from '../../members/model';
import { groupsKey } from '../keys';
import { type Group, type Named, toCoaches, toGroups } from './model';

/** The active groups, and the archived ones too when `includeArchived`. */
export function useGroups(includeArchived: boolean) {
  const { api } = useAuth();
  return useQuery({
    queryKey: [...groupsKey, { includeArchived }],
    queryFn: async (): Promise<Group[]> =>
      toGroups(
        await unwrap(
          api.GET('/api/v1/training-groups', { params: { query: { includeArchived } } }),
        ),
      ),
  });
}

/**
 * Who can be chosen as a coach: the active members holding COACH. The API has no role filter, so this reads the active
 * members (at most the first 300) and keeps those; `incomplete` says when the API cut the list short.
 */
export function useCoaches() {
  const { api } = useAuth();
  return useQuery({
    queryKey: [...membersKey, 'coaches'],
    queryFn: async (): Promise<{ coaches: Named[]; incomplete: boolean }> =>
      toCoaches(
        toMemberListing(
          await unwrap(api.GET('/api/v1/members', { params: { query: { status: 'ACTIVE' } } })),
        ),
      ),
  });
}
