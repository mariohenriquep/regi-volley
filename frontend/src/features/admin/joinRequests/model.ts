import type { components } from '@/api/schema.gen';
import { isInstant } from '@/lib/dates';

type Schemas = components['schemas'];

/** The API lists at most this many pending requests (`JoinRequestRepository.MAX_PENDING_LISTED`), oldest first. */
export const MAX_PENDING_LISTED = 100;

/** A request waiting for a decision, read once from the generated type whose fields are all optional. */
export interface JoinRequest {
  id: string;
  name: string;
  email: string;
  phone: string;
  requestedAt: string;
}

export function toJoinRequest(raw: Schemas['JoinRequestResponse']): JoinRequest | null {
  if (!raw.id || !isInstant(raw.requestedAt)) return null;
  return {
    id: raw.id,
    name: raw.name ?? '',
    email: raw.email ?? '',
    phone: raw.phone ?? '',
    requestedAt: raw.requestedAt,
  };
}

/** The requests of a response in the order the API gave them; unreadable rows are skipped, a non-list is empty. */
export function toJoinRequests(raw: unknown): JoinRequest[] {
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((row: unknown) =>
    typeof row === 'object' && row !== null ? (toJoinRequest(row) ?? []) : [],
  );
}
