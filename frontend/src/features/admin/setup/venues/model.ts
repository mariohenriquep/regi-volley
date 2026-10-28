import type { components } from '@/api/schema.gen';

type Schemas = components['schemas'];

/** The API's limits for a venue (`VenueRequest`). */
export const MAX_VENUE_NAME = 100;
export const MAX_VENUE_ADDRESS = 200;
export const MIN_COURTS = 1;
export const MAX_COURTS = 1000;

/** A venue of the list, read once from the generated type (every field of which is optional). */
export interface Venue {
  id: string;
  name: string;
  address: string;
  courts: number;
  /** An active training group runs here, so the API will refuse to delete it. The API still decides when asked. */
  inUse: boolean;
}

export function toVenue(raw: Schemas['VenueEntryResponse']): Venue | null {
  if (!raw.id || typeof raw.courts !== 'number') return null;
  return {
    id: raw.id,
    name: raw.name ?? '',
    address: raw.address ?? '',
    courts: raw.courts,
    inUse: raw.inUseByActiveGroup === true,
  };
}

/** The venues in the order the API gives them; a payload that is not a list is an empty one. */
export function toVenues(raw: unknown): Venue[] {
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((row: unknown) =>
    typeof row === 'object' && row !== null ? (toVenue(row) ?? []) : [],
  );
}
