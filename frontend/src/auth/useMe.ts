import { useQuery } from '@tanstack/react-query';
import { unwrap } from '@/api/errors';
import type { components } from '@/api/schema.gen';
import { useAuth } from './context';

export type Role = NonNullable<components['schemas']['MeResponse']['roles']>[number];

/** The roles the client knows by name; a word from a newer API is ignored rather than guessed at. */
const KNOWN_ROLES: ReadonlySet<string> = new Set<Role>(['MEMBER', 'COACH', 'ADMIN']);

export interface Me {
  roles: Role[];
}

export const meKey = ['me'] as const;

/** The role words of an answer, once each, in the order the API gave them. Anything unreadable is no role. */
export function toRoles(raw: unknown): Role[] {
  const roles = Array.isArray(raw)
    ? raw.filter((role): role is Role => typeof role === 'string' && KNOWN_ROLES.has(role))
    : [];
  return [...new Set(roles)];
}

/** The roles of a `GET /me` answer. */
export function toMe(raw: components['schemas']['MeResponse']): Me {
  return { roles: toRoles(raw.roles) };
}

/**
 * Who the API says the signed-in member is: their roles as they stand now. Read for display only (which sections to
 * offer); every operation is authorised again by the API. It is read again when the page comes back in front, so a role
 * revoked meanwhile stops being offered, and it is dropped with the rest of the cache whenever the user changes. A failure
 * is not retried: the shell must not wait on it, and the answer is then "no roles known".
 */
export function useMe() {
  const { api, status } = useAuth();
  return useQuery({
    queryKey: meKey,
    queryFn: async (): Promise<Me> => toMe(await unwrap(api.GET('/api/v1/me'))),
    enabled: status === 'authenticated',
    retry: false,
    refetchOnWindowFocus: true,
  });
}
